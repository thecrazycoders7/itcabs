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
    val vehicleType: String = "",
    val vehicleReg: String = "",
    val aadhaar: String = "",
    val rcNumber: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false,
) {
    val canSubmit: Boolean
        get() = vehicleType.isNotBlank() && vehicleReg.isNotBlank() && aadhaar.length >= 4 && rcNumber.isNotBlank()
}

/** Lets a driver complete/submit KYC from their home when onboarding didn't capture it. */
@HiltViewModel
class DriverKycViewModel @Inject constructor(
    private val driver: DriverRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DriverKycUiState())
    val state: StateFlow<DriverKycUiState> = _state.asStateFlow()

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
