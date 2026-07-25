package com.itcabs.feature.dispatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.AdminDriver
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
    val pendingCoordinators: List<com.itcabs.domain.model.PendingCoordinator> = emptyList(),
    val drivers: List<AdminDriver> = emptyList(),
    val docs: Map<Long, List<com.itcabs.domain.model.KycDoc>> = emptyMap(),
    val openUrl: String? = null,   // one-shot: a freshly signed URL for the screen to open, then consume
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
            (drivers.allDrivers() as? AppResult.Ok)?.let { r -> _state.update { it.copy(drivers = r.value) } }
            (drivers.pendingCoordinators() as? AppResult.Ok)?.let { r -> _state.update { it.copy(pendingCoordinators = r.value) } }
            when (val r = drivers.pendingDrivers()) {
                is AppResult.Ok -> _state.update { it.copy(loading = false, pending = r.value) }
                is AppResult.Err -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun approveCoordinator(userId: Long) = act(userId) { drivers.approveCoordinator(it) }
    fun rejectCoordinator(userId: Long, reason: String?) = act(userId) { drivers.rejectCoordinator(it, reason) }

    fun verify(driverId: Long) = act(driverId) { drivers.verifyDriver(it) }
    fun reject(driverId: Long, reason: String?) = act(driverId) { drivers.rejectDriver(it, reason) }
    fun block(userId: Long) = act(userId) { drivers.blockUser(it) }
    fun unblock(userId: Long) = act(userId) { drivers.unblockUser(it) }

    /** Load a driver's documents (called when the reviewer expands their card). */
    fun loadDocs(driverId: Long) {
        viewModelScope.launch {
            when (val r = drivers.adminDriverDocs(driverId)) {
                is AppResult.Ok -> _state.update { it.copy(docs = it.docs + (driverId to r.value)) }
                is AppResult.Err -> _state.update { it.copy(error = r.message) }
            }
        }
    }

    /** Sign a private document path and hand the URL to the screen to open in a viewer. */
    fun openDoc(storagePath: String) {
        viewModelScope.launch {
            when (val r = drivers.signedDocUrl(storagePath)) {
                is AppResult.Ok -> _state.update { it.copy(openUrl = r.value) }
                is AppResult.Err -> _state.update { it.copy(error = r.message) }
            }
        }
    }

    fun consumeOpenUrl() = _state.update { it.copy(openUrl = null) }

    fun requestReupload(driverId: Long, docType: String, reason: String?) {
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            when (val r = drivers.requestReupload(driverId, docType, reason)) {
                is AppResult.Ok -> loadDocs(driverId)   // refresh that driver's doc states
                is AppResult.Err -> _state.update { it.copy(error = r.message) }
            }
        }
    }

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
