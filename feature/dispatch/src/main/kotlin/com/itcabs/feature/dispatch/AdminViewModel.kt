package com.itcabs.feature.dispatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.PendingDriver
import com.itcabs.domain.repository.DriverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminUiState(
    val pending: List<PendingDriver> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

/** Admin KYC review queue: list drivers awaiting approval and verify them (is_admin gated server-side). */
@HiltViewModel
class AdminViewModel @Inject constructor(
    private val drivers: DriverRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = drivers.pendingDrivers()) {
                is AppResult.Ok -> _state.update { it.copy(loading = false, pending = r.value) }
                is AppResult.Err -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun verify(driverId: Long) = act(driverId) { drivers.verifyDriver(it) }

    fun reject(driverId: Long, reason: String?) = act(driverId) { drivers.rejectDriver(it, reason) }

    private fun act(driverId: Long, action: suspend (Long) -> AppResult<Unit>) {
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            when (val r = action(driverId)) {
                is AppResult.Ok -> refresh()
                is AppResult.Err -> _state.update { it.copy(error = r.message) }
            }
        }
    }
}
