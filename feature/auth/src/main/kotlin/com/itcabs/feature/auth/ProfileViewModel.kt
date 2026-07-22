package com.itcabs.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itcabs.domain.model.User
import com.itcabs.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The signed-in user's account details, observed from the local cache and refreshed from /auth/me. */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    val user: StateFlow<User?> = auth.getUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Refresh from the server so the cache is current (also self-heals an empty cache).
        viewModelScope.launch { auth.currentUser() }
    }
}
