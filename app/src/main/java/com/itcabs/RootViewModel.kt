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
     * Try /auth/me with the persisted access token. If it's expired (401) but a refresh token
     * exists, refresh once and retry — so a returning user stays signed in past the 15-min
     * access-token lifetime. (Full auto-refresh on any 401 is a separate follow-up.)
     */
    private suspend fun resolve(): RootState {
        (auth.currentUser() as? AppResult.Ok)?.let { return RootState.SignedIn(it.value.role) }
        if (auth.refresh() is AppResult.Ok) {
            (auth.currentUser() as? AppResult.Ok)?.let { return RootState.SignedIn(it.value.role) }
        }
        return RootState.SignedOut
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
