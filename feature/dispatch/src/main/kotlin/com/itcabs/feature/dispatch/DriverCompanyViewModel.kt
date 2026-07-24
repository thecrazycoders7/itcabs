package com.itcabs.feature.dispatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.CompanyJob
import com.itcabs.domain.repository.CompanyJobRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DriverCompanyUiState(
    val feed: List<CompanyJob> = emptyList(),
    val trips: List<CompanyJob> = emptyList(),
    val loading: Boolean = false,
    val claimingId: Long? = null,
    val error: String? = null,
)

/** Driver side of multi-stop company jobs: browse feed, claim, run the trip stop-by-stop. */
@HiltViewModel
class DriverCompanyViewModel @Inject constructor(
    private val repo: CompanyJobRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DriverCompanyUiState())
    val state: StateFlow<DriverCompanyUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            (repo.feed() as? AppResult.Ok)?.let { r -> _state.update { it.copy(feed = r.value) } }
            when (val r = repo.myTrips()) {
                is AppResult.Ok -> _state.update { it.copy(loading = false, trips = r.value) }
                is AppResult.Err -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun claim(jobId: Long) {
        _state.update { it.copy(claimingId = jobId, error = null) }
        viewModelScope.launch {
            when (val r = repo.claim(jobId)) {
                is AppResult.Ok -> { _state.update { it.copy(claimingId = null) }; refresh() }
                is AppResult.Err -> _state.update {
                    it.copy(claimingId = null, error = if (r.code == 409) "Someone else took this job." else r.message)
                }
            }
        }
    }

    fun confirmPickup(stopId: Long, otp: String) {
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            when (val r = repo.confirmStopPickup(stopId, otp)) {
                is AppResult.Ok -> refresh()
                is AppResult.Err -> _state.update { it.copy(error = r.message) }
            }
        }
    }
}
