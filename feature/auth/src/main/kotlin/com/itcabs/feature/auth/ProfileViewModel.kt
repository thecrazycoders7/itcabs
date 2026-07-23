package com.itcabs.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.DriverProfile
import com.itcabs.domain.model.KycStatus
import com.itcabs.domain.model.User
import com.itcabs.domain.repository.AuthRepository
import com.itcabs.domain.repository.DriverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The signed-in user's account details + (for drivers) their KYC status and reliability record. */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val driver: DriverRepository,
) : ViewModel() {

    val user: StateFlow<User?> = auth.getUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _profile = MutableStateFlow<DriverProfile?>(null)
    val profile: StateFlow<DriverProfile?> = _profile.asStateFlow()
    val kyc: StateFlow<KycStatus?> = _profile.map { it?.kycStatus }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { auth.currentUser() } // refresh cache from /auth/me
        viewModelScope.launch {
            // Only drivers have a driver profile; a coordinator gets NONE (harmless).
            when (val result = driver.myProfile()) {
                is AppResult.Ok -> _profile.value = result.value
                is AppResult.Err -> Unit
            }
        }
    }
}
