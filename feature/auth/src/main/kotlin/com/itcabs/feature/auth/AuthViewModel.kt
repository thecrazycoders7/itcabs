package com.itcabs.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.KycStatus
import com.itcabs.domain.model.UserRole
import com.itcabs.domain.repository.AuthRepository
import com.itcabs.domain.repository.DriverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Immutable UI state — the only thing the screen renders (UDF, ADR-0007). */
data class AuthUiState(
    val phone: String = "",
    val code: String = "",
    val name: String = "",
    val role: UserRole = UserRole.DRIVER,
    val step: Step = Step.Phone,
    val loading: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
    // The authoritative role from the backend (a returning user's role may differ from the
    // one picked in the form). Non-null once signedIn; drives role-based home routing.
    val signedInRole: UserRole? = null,

    // KYC fields
    val vehicleType: String = "",
    val vehicleReg: String = "",
    val aadhaar: String = "",
    val rcNumber: String = "",
    val photoUrl: String = "",
) {
    enum class Step { Phone, Code, Kyc }
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val driver: DriverRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onPhoneChange(value: String) = _state.update { it.copy(phone = value, error = null) }
    fun onCodeChange(value: String) = _state.update { it.copy(code = value, error = null) }
    fun onNameChange(value: String) = _state.update { it.copy(name = value) }
    fun onRoleChange(role: UserRole) = _state.update { it.copy(role = role) }

    // KYC field changes
    fun onVehicleTypeChange(v: String) = _state.update { it.copy(vehicleType = v) }
    fun onVehicleRegChange(v: String) = _state.update { it.copy(vehicleReg = v) }
    fun onAadhaarChange(v: String) = _state.update { it.copy(aadhaar = v) }
    fun onRcNumberChange(v: String) = _state.update { it.copy(rcNumber = v) }
    fun onPhotoUrlChange(v: String) = _state.update { it.copy(photoUrl = v) }

    /** E.164 form the backend expects; the field holds the 10 national digits, "+91" is the shown prefix. */
    private fun e164Phone(): String = "+91" + _state.value.phone

    fun requestOtp() = launchLoading {
        when (val result = auth.requestOtp(e164Phone())) {
            is AppResult.Ok -> _state.update { it.copy(loading = false, step = AuthUiState.Step.Code) }
            is AppResult.Err -> _state.update { it.copy(loading = false, error = result.message) }
        }
    }

    fun verify() = launchLoading {
        val s = _state.value
        when (val result = auth.verifyOtp(e164Phone(), s.code, s.role, s.name.ifBlank { null })) {
            is AppResult.Ok -> {
                val role = result.value.role
                // A driver still needs KYC unless the backend already has them VERIFIED — otherwise a
                // returning, already-verified driver would be forced through the KYC form every login.
                val needsKyc = role == UserRole.DRIVER &&
                    (driver.myProfile() as? AppResult.Ok)?.value?.kycStatus != KycStatus.VERIFIED
                if (needsKyc) {
                    _state.update { it.copy(loading = false, step = AuthUiState.Step.Kyc, signedInRole = role) }
                } else {
                    _state.update { it.copy(loading = false, signedIn = true, signedInRole = role) }
                }
            }
            is AppResult.Err -> _state.update { it.copy(loading = false, error = result.message) }
        }
    }

    fun submitKyc() = launchLoading {
        val s = _state.value
        when (val result = driver.submitKyc(
            s.vehicleType, s.vehicleReg,
            aadhaarRef = "REF_" + s.aadhaar, // mock ref
            aadhaarMasked = "********" + s.aadhaar.takeLast(4),
            rcNumberMasked = "********" + s.rcNumber.takeLast(4),
            photoUrl = s.photoUrl
        )) {
            is AppResult.Ok -> _state.update { it.copy(loading = false, signedIn = true) }
            is AppResult.Err -> _state.update { it.copy(loading = false, error = result.message) }
        }
    }

    private inline fun launchLoading(crossinline block: suspend () -> Unit) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch { block() }
    }
}
