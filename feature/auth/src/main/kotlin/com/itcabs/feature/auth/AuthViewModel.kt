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

/** UI state for the Supabase auth flow: sign in → (first time) pick a role → into the app. */
data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val role: UserRole = UserRole.DRIVER,
    val gender: String? = null,
    val step: Step = Step.SignIn,
    val loading: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
    val signedInRole: UserRole? = null,
) {
    enum class Step { SignIn, Onboard }
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        // Returning-but-not-onboarded user already has a Supabase session → jump to onboarding.
        if (auth.hasSession()) launchLoading { checkOnboarding() }
    }

    fun onEmailChange(v: String) = _state.update { it.copy(email = v, error = null) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, error = null) }
    fun onNameChange(v: String) = _state.update { it.copy(name = v) }
    fun onRoleChange(r: UserRole) = _state.update { it.copy(role = r) }
    fun onGenderChange(g: String) = _state.update { it.copy(gender = g) }

    /** [idToken] comes from Credential Manager (Google). */
    fun signInWithGoogle(idToken: String) = launchLoading {
        when (val r = auth.signInWithGoogle(idToken)) {
            is AppResult.Ok -> checkOnboarding()
            is AppResult.Err -> fail(r.message)
        }
    }

    fun signInEmail() = launchLoading {
        val s = _state.value
        when (val r = auth.signInWithEmail(s.email.trim(), s.password)) {
            is AppResult.Ok -> checkOnboarding()
            is AppResult.Err -> fail(r.message)
        }
    }

    fun signUpEmail() = launchLoading {
        val s = _state.value
        when (val r = auth.signUpWithEmail(s.email.trim(), s.password)) {
            is AppResult.Ok ->
                if (auth.hasSession()) checkOnboarding()
                else fail("Account created — check your email to confirm, then sign in.")
            is AppResult.Err -> fail(r.message)
        }
    }

    private suspend fun checkOnboarding() {
        when (val r = auth.currentUser()) {
            is AppResult.Ok -> r.value?.let { u ->
                _state.update { it.copy(loading = false, signedIn = true, signedInRole = u.role) }
            } ?: _state.update { it.copy(loading = false, step = AuthUiState.Step.Onboard) }
            is AppResult.Err -> fail(r.message)
        }
    }

    fun onboard() = launchLoading {
        val s = _state.value
        // Carpooling has one persona: a "member" who can host or book rides. We onboard everyone under
        // the DRIVER role so hosting reuses the existing KYC/driver_profiles; booking only needs a phone.
        when (val r = auth.onboard(UserRole.DRIVER, s.name.ifBlank { null }, s.gender)) {
            is AppResult.Ok -> _state.update { it.copy(loading = false, signedIn = true, signedInRole = UserRole.DRIVER) }
            is AppResult.Err -> fail(r.message)
        }
    }

    private fun fail(message: String) = _state.update { it.copy(loading = false, error = message) }

    private inline fun launchLoading(crossinline block: suspend () -> Unit) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch { block() }
    }
}
