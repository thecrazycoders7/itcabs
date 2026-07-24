package com.itcabs.feature.dispatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itcabs.domain.AppResult
import com.itcabs.domain.repository.DriverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One document slot: the label the driver sees + its upload/review state. */
data class KycDocDef(val type: String, val label: String)

/** The documents every corporate driver uploads (mirrors REQUIRED_KYC_DOCS on the backend). */
val KYC_DOC_DEFS = listOf(
    KycDocDef("DL_FRONT", "Driving licence — front"),
    KycDocDef("DL_BACK", "Driving licence — back"),
    KycDocDef("AADHAAR_FRONT", "Aadhaar — front"),
    KycDocDef("AADHAAR_BACK", "Aadhaar — back"),
    KycDocDef("RC_FRONT", "Vehicle RC — front"),
    KycDocDef("RC_BACK", "Vehicle RC — back"),
    KycDocDef("PERMIT", "Permit"),
    KycDocDef("INSURANCE", "Insurance"),
    KycDocDef("FITNESS", "Fitness certificate"),
)

enum class DocStatus { MISSING, UPLOADING, UPLOADED, REUPLOAD }

/** Per-document UI state (keyed by docType in the map). */
data class DocUi(
    val status: DocStatus = DocStatus.MISSING,
    val error: String? = null,
    val rejectReason: String? = null,
)

data class DriverKycUiState(
    val phone: String = "",
    val phoneVerified: Boolean = false,
    val otpSent: Boolean = false,
    val verifyingPhone: Boolean = false,
    val phoneError: String? = null,
    val vehicleType: String = "",
    val vehicleReg: String = "",
    val aadhaar: String = "",
    val rcNumber: String = "",
    val docs: Map<String, DocUi> = KYC_DOC_DEFS.associate { it.type to DocUi() },
    val kycStatus: com.itcabs.domain.model.KycStatus = com.itcabs.domain.model.KycStatus.NONE,
    val rejectionReason: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false,
) {
    val uploadedCount: Int
        get() = KYC_DOC_DEFS.count { docs[it.type]?.status == DocStatus.UPLOADED }

    val allDocsUploaded: Boolean
        get() = uploadedCount == KYC_DOC_DEFS.size

    // Phone verified + all documents uploaded + vehicle/identity fields (spec).
    val canSubmit: Boolean
        get() = phoneVerified && allDocsUploaded &&
            vehicleType.isNotBlank() && vehicleReg.isNotBlank() && aadhaar.length >= 4 && rcNumber.isNotBlank()
}

/** Lets a driver complete/submit KYC from their home when onboarding didn't capture it. */
@HiltViewModel
class DriverKycViewModel @Inject constructor(
    private val driver: DriverRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DriverKycUiState())
    val state: StateFlow<DriverKycUiState> = _state.asStateFlow()

    init {
        // Reflect any already-verified phone so the badge shows on return.
        viewModelScope.launch {
            (driver.myProfile() as? AppResult.Ok)?.value?.let { p ->
                _state.update {
                    it.copy(
                        phone = p.phone ?: it.phone, phoneVerified = p.phoneVerified,
                        kycStatus = p.kycStatus, rejectionReason = p.rejectionReason,
                    )
                }
            }
        }
        // Reflect already-uploaded docs (and any admin re-upload requests) so returning drivers resume.
        viewModelScope.launch {
            (driver.myKycDocs() as? AppResult.Ok)?.value?.let { docs ->
                _state.update { s ->
                    s.copy(docs = s.docs.mapValues { (type, ui) ->
                        docs.firstOrNull { it.docType == type }?.let {
                            val reupload = it.status == "REUPLOAD_REQUESTED"
                            ui.copy(status = if (reupload) DocStatus.REUPLOAD else DocStatus.UPLOADED, rejectReason = it.rejectReason)
                        } ?: ui
                    })
                }
            }
        }
    }

    private fun setDoc(type: String, ui: DocUi) =
        _state.update { it.copy(docs = it.docs + (type to ui)) }

    /** Surface a client-side error (e.g. the image couldn't be read) on one document slot. */
    fun docError(type: String, msg: String) = setDoc(type, DocUi(DocStatus.MISSING, error = msg))

    /** Upload compressed JPEG bytes for one document; overwrites any prior upload (replace). */
    fun uploadDoc(type: String, jpeg: ByteArray) {
        setDoc(type, DocUi(DocStatus.UPLOADING))
        viewModelScope.launch {
            when (val r = driver.uploadKycDoc(type, jpeg)) {
                is AppResult.Ok -> setDoc(type, DocUi(DocStatus.UPLOADED))
                is AppResult.Err -> setDoc(type, DocUi(DocStatus.MISSING, error = r.message))
            }
        }
    }

    // --- phone verification (Firebase flow lives in the screen; these track state + backend) ---
    fun onPhoneChange(v: String) = _state.update { it.copy(phone = v.filter { c -> c.isDigit() || c == '+' }, phoneError = null) }
    fun onOtpSending() = _state.update { it.copy(verifyingPhone = true, phoneError = null) }
    fun onOtpSent() = _state.update { it.copy(verifyingPhone = false, otpSent = true) }
    fun onPhoneError(msg: String?) = _state.update { it.copy(verifyingPhone = false, phoneError = msg ?: "Verification failed") }

    /** Called with the Firebase ID token once the OTP is confirmed; backend verifies + marks it. */
    fun submitPhoneToken(idToken: String) {
        _state.update { it.copy(verifyingPhone = true, phoneError = null) }
        viewModelScope.launch {
            when (val r = driver.verifyPhone(idToken)) {
                is AppResult.Ok -> _state.update { it.copy(verifyingPhone = false, phoneVerified = true, otpSent = false) }
                is AppResult.Err -> _state.update { it.copy(verifyingPhone = false, phoneError = r.message) }
            }
        }
    }

    fun onVehicleTypeChange(v: String) = _state.update { it.copy(vehicleType = v, error = null) }
    fun onVehicleRegChange(v: String) = _state.update { it.copy(vehicleReg = v, error = null) }
    fun onAadhaarChange(v: String) = _state.update { it.copy(aadhaar = v.filter(Char::isDigit), error = null) }
    fun onRcNumberChange(v: String) = _state.update { it.copy(rcNumber = v, error = null) }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) {
            val msg = when {
                !s.phoneVerified -> "Verify your phone first."
                !s.allDocsUploaded -> "Upload all documents first."
                else -> "Fill vehicle, registration, Aadhaar, and RC."
            }
            _state.update { it.copy(error = msg) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (
                val r = driver.submitKyc(
                    s.vehicleType, s.vehicleReg,
                    aadhaarRef = "REF_" + s.aadhaar,
                    aadhaarMasked = "********" + s.aadhaar.takeLast(4),
                    rcNumberMasked = "********" + s.rcNumber.takeLast(4),
                    photoUrl = "",
                )
            ) {
                // Show the "Under Review" screen in place of the form (spec) rather than navigating away.
                is AppResult.Ok -> _state.update {
                    it.copy(loading = false, submitted = true, kycStatus = com.itcabs.domain.model.KycStatus.PENDING)
                }
                is AppResult.Err -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }
}
