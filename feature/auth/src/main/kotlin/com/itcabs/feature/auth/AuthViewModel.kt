package com.itcabs.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.UserRole
import com.itcabs.domain.repository.AuthRepository
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
) {
    enum class Step { Phone, Code }
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onPhoneChange(value: String) = _state.update { it.copy(phone = value, error = null) }
    fun onCodeChange(value: String) = _state.update { it.copy(code = value, error = null) }
    fun onNameChange(value: String) = _state.update { it.copy(name = value) }
    fun onRoleChange(role: UserRole) = _state.update { it.copy(role = role) }

    fun requestOtp() = launchLoading {
        when (val result = auth.requestOtp(_state.value.phone)) {
            is AppResult.Ok -> _state.update { it.copy(loading = false, step = AuthUiState.Step.Code) }
            is AppResult.Err -> _state.update { it.copy(loading = false, error = result.message) }
        }
    }

    fun verify() = launchLoading {
        val s = _state.value
        when (val result = auth.verifyOtp(s.phone, s.code, s.role, s.name.ifBlank { null })) {
            is AppResult.Ok -> _state.update {
                it.copy(loading = false, signedIn = true, signedInRole = result.value.role)
            }
            is AppResult.Err -> _state.update { it.copy(loading = false, error = result.message) }
        }
    }

    private inline fun launchLoading(crossinline block: suspend () -> Unit) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch { block() }
    }
}
