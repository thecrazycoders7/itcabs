package com.itcabs.feature.dispatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.Leg
import com.itcabs.domain.repository.DispatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DriverFeedUiState(
    val area: String = "",
    val legs: List<Leg> = emptyList(),
    val loading: Boolean = false,
    val claimingId: Long? = null,
    val error: String? = null,
    val notice: String? = null,
)

@HiltViewModel
class DriverFeedViewModel @Inject constructor(
    private val dispatch: DispatchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DriverFeedUiState())
    val state: StateFlow<DriverFeedUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            dispatch.getFeedFlow().collect { legs ->
                _state.update { it.copy(legs = legs) }
            }
        }
        refresh()
    }

    fun onAreaChange(value: String) = _state.update { it.copy(area = value) }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = dispatch.feed(_state.value.area.ifBlank { null }, null)) {
                is AppResult.Ok -> _state.update { it.copy(loading = false, legs = result.value) }
                is AppResult.Err -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    /** Claim a leg. On 409 the leg was taken by someone else — surface it and refresh the feed. */
    fun claim(legId: Long) {
        _state.update { it.copy(claimingId = legId, notice = null) }
        viewModelScope.launch {
            when (val result = dispatch.claim(legId)) {
                is AppResult.Ok -> {
                    _state.update { it.copy(claimingId = null, notice = "Trip claimed!") }
                    refresh()
                }
                is AppResult.Err -> {
                    val message = if (result.code == 409) "Trip already taken" else result.message
                    _state.update { it.copy(claimingId = null, notice = message) }
                    if (result.code == 409) refresh()
                }
            }
        }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }
}
