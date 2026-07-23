package com.itcabs.feature.dispatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.JobTemplate
import com.itcabs.domain.model.Leg
import com.itcabs.domain.model.LegStatus
import com.itcabs.domain.model.NewLeg
import com.itcabs.domain.model.VerifiedDriver
import com.itcabs.domain.repository.DispatchRepository
import com.itcabs.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CoordinatorHomeUiState(
    val legs: List<Leg> = emptyList(),
    val statusFilter: LegStatus? = null,   // null = all
    val query: String = "",
    val verifiedDrivers: List<VerifiedDriver> = emptyList(),
    val templates: List<JobTemplate> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
) {
    /** Legs after the status filter + text search (office/pickup/drop/passenger). */
    val visibleLegs: List<Leg>
        get() = legs
            .filter { statusFilter == null || it.status == statusFilter }
            .filter {
                query.isBlank() || listOf(it.office, it.pickup, it.drop, it.passengerName)
                    .any { f -> f.contains(query, ignoreCase = true) }
            }
}

@HiltViewModel
class CoordinatorHomeViewModel @Inject constructor(
    private val dispatch: DispatchRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CoordinatorHomeUiState())
    val state: StateFlow<CoordinatorHomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            (auth.currentUser() as? AppResult.Ok)?.value?.let { user ->
                dispatch.getMyLegsFlow(user.id).collect { legs ->
                    _state.update { it.copy(legs = legs) }
                }
            }
        }
        // Realtime (ADR-0008): a driver claiming/updating a leg pushes an event → re-fetch, so the
        // dashboard flips OPEN→CLAIMED live without a manual Refresh.
        viewModelScope.launch { dispatch.legEvents().collect { refresh() } }
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = dispatch.myLegs()) {
                is AppResult.Ok -> _state.update { it.copy(loading = false) }
                is AppResult.Err -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    /** Repost a job's route as a fresh OPEN job (M6). Realtime + refresh surface the new legs. */
    fun repost(jobId: Long) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = dispatch.repostJob(jobId)) {
                is AppResult.Ok -> refresh()
                is AppResult.Err -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    fun setStatus(legId: Long, status: LegStatus) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = dispatch.setStatus(legId, status)) {
                is AppResult.Ok -> refresh()
                is AppResult.Err -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    /** Report the claimed driver as a no-show: dings reliability, leg reopens (realtime refreshes). */
    fun markNoShow(legId: Long) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = dispatch.markNoShow(legId)) {
                is AppResult.Ok -> refresh()
                is AppResult.Err -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    /** Mark a completed trip settled (cash paid). Realtime + refresh flip it to Paid on both sides. */
    fun markPaid(legId: Long) {
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            when (val result = dispatch.markPaid(legId)) {
                is AppResult.Ok -> refresh()
                is AppResult.Err -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    fun rate(legId: Long, stars: Int) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = dispatch.rate(legId, stars, null)) {
                is AppResult.Ok -> refresh()
                is AppResult.Err -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    // --- filter / search ---
    fun onFilter(status: LegStatus?) = _state.update { it.copy(statusFilter = status) }
    fun onQuery(q: String) = _state.update { it.copy(query = q) }

    // --- edit an OPEN leg ---
    fun editLeg(legId: Long, edit: NewLeg) {
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            when (val r = dispatch.editLeg(legId, edit)) {
                is AppResult.Ok -> refresh()
                is AppResult.Err -> _state.update { it.copy(error = r.message) }
            }
        }
    }

    // --- direct assign to a verified driver ---
    fun loadDrivers() {
        viewModelScope.launch {
            (dispatch.verifiedDrivers() as? AppResult.Ok)?.let { r -> _state.update { it.copy(verifiedDrivers = r.value) } }
        }
    }

    fun assign(legId: Long, driverId: Long) {
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            when (val r = dispatch.assign(legId, driverId)) {
                is AppResult.Ok -> refresh()
                is AppResult.Err -> _state.update { it.copy(error = r.message) }
            }
        }
    }

    // --- saved-route templates ---
    fun loadTemplates() {
        viewModelScope.launch {
            (dispatch.templates() as? AppResult.Ok)?.let { r -> _state.update { it.copy(templates = r.value) } }
        }
    }

    fun postTemplate(id: Long) {
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            when (val r = dispatch.postTemplate(id)) {
                is AppResult.Ok -> refresh()
                is AppResult.Err -> _state.update { it.copy(error = r.message) }
            }
        }
    }

    fun deleteTemplate(id: Long) {
        viewModelScope.launch {
            if (dispatch.deleteTemplate(id) is AppResult.Ok) loadTemplates()
        }
    }
}
