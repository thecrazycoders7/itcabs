package com.itcabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.UserRole
import com.itcabs.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Top-level app state: are we signed in, and as which role? Decides the start screen. */
sealed interface RootState {
    data object Loading : RootState
    data object SignedOut : RootState
    data class SignedIn(val role: UserRole) : RootState
}

@HiltViewModel
class RootViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val pushTokens: PushTokenManager,
) : ViewModel() {

    private val _state = MutableStateFlow<RootState>(RootState.Loading)
    val state: StateFlow<RootState> = _state.asStateFlow()

    init {
        // Register this device's FCM token whenever we become signed in (new login or cold start).
        viewModelScope.launch {
            state.collect { if (it is RootState.SignedIn) pushTokens.registerCurrentToken() }
        }
        viewModelScope.launch {
            auth.getUserFlow().collect { user ->
                if (user != null) {
                    _state.value = RootState.SignedIn(user.role)
                } else if (_state.value is RootState.SignedIn) {
                    _state.value = RootState.SignedOut
                }
            }
        }
        viewModelScope.launch {
            if (_state.value == RootState.Loading) {
                _state.value = resolve()
            }
        }
    }

    /**
     * Cold start: if a Supabase session is stored, ask the backend who we are. Onboarded → SignedIn;
     * authenticated-but-not-onboarded (or no/expired session) → SignedOut, and the AuthScreen picks
     * up the onboarding step. ponytail: Supabase access tokens expire in ~1h with no auto-refresh
     * yet — a returning user re-signs-in after that. Add Supabase refresh-token handling later.
     */
    private suspend fun resolve(): RootState {
        if (!auth.hasSession()) return RootState.SignedOut
        return when (val r = auth.currentUser()) {
            is AppResult.Ok -> r.value?.let { RootState.SignedIn(it.role) } ?: RootState.SignedOut
            is AppResult.Err -> RootState.SignedOut
        }
    }

    fun onSignedIn(role: UserRole) { _state.value = RootState.SignedIn(role) }

    fun signOut() {
        // Transition to signed-out immediately: local cleanup (token wipe + Room clear) must never
        // block the UI. Doing it after auth.signOut() meant a slow/stuck cleanup left the user
        // stranded on their home screen. Cleanup now runs best-effort in the background.
        _state.value = RootState.SignedOut
        viewModelScope.launch { runCatching { auth.signOut() } }
    }
}
