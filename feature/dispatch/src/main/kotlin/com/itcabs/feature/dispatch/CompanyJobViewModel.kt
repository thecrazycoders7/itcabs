package com.itcabs.feature.dispatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.Area
import com.itcabs.domain.model.CompanyJob
import com.itcabs.domain.model.LegStatus
import com.itcabs.domain.model.NewCompanyJob
import com.itcabs.domain.model.NewStop
import com.itcabs.domain.model.TripType
import com.itcabs.domain.model.VerifiedDriver
import com.itcabs.domain.repository.CompanyJobRepository
import com.itcabs.domain.repository.DispatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompanyJobUiState(
    val jobs: List<CompanyJob> = emptyList(),
    val areas: List<Area> = emptyList(),
    val verifiedDrivers: List<VerifiedDriver> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val published: Boolean = false,
)

/** Coordinator side of multi-stop company jobs: create, list, manage. */
@HiltViewModel
class CompanyJobViewModel @Inject constructor(
    private val repo: CompanyJobRepository,
    private val dispatch: DispatchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CompanyJobUiState())
    val state: StateFlow<CompanyJobUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            (dispatch.areas() as? AppResult.Ok)?.let { r -> _state.update { it.copy(areas = r.value) } }
        }
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.mine()) {
                is AppResult.Ok -> _state.update { it.copy(loading = false, jobs = r.value) }
                is AppResult.Err -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun create(job: NewCompanyJob) {
        _state.update { it.copy(loading = true, error = null, published = false) }
        viewModelScope.launch {
            when (val r = repo.create(job)) {
                is AppResult.Ok -> { _state.update { it.copy(loading = false, published = true) }; refresh() }
                is AppResult.Err -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun setStatus(jobId: Long, status: LegStatus) = act { repo.setStatus(jobId, status) }
    fun replaceStops(jobId: Long, stops: List<NewStop>) = act { repo.replaceStops(jobId, stops) }
    fun assign(jobId: Long, driverId: Long) = act { repo.assign(jobId, driverId).map { } }

    fun loadDrivers() {
        viewModelScope.launch {
            (dispatch.verifiedDrivers() as? AppResult.Ok)?.let { r -> _state.update { it.copy(verifiedDrivers = r.value) } }
        }
    }

    private fun <T> AppResult<T>.map(f: (T) -> Unit): AppResult<Unit> = when (this) {
        is AppResult.Ok -> AppResult.Ok(Unit).also { f(value) }
        is AppResult.Err -> this
    }

    private fun act(block: suspend () -> AppResult<Unit>) {
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            when (val r = block()) {
                is AppResult.Ok -> refresh()
                is AppResult.Err -> _state.update { it.copy(error = r.message) }
            }
        }
    }
}
