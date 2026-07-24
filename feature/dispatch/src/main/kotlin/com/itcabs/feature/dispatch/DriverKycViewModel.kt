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
    val loading: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false,
) {
    // Phone must be verified before KYC can be submitted (spec).
    val canSubmit: Boolean
        get() = phoneVerified && vehicleType.isNotBlank() && vehicleReg.isNotBlank() && aadhaar.length >= 4 && rcNumber.isNotBlank()
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
                _state.update { it.copy(phone = p.phone ?: it.phone, phoneVerified = p.phoneVerified) }
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
            _state.update { it.copy(error = "Fill vehicle, registration, Aadhaar, and RC.") }
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
                is AppResult.Ok -> _state.update { it.copy(loading = false, submitted = true) }
                is AppResult.Err -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }
}
