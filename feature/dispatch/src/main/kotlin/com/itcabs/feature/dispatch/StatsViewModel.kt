package com.itcabs.feature.dispatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.CoordinatorStats
import com.itcabs.domain.repository.DispatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Insights time window. [days] null = all time. */
enum class StatsRange(val label: String, val days: Int?) {
    WEEK("7 days", 7), MONTH("30 days", 30), ALL("All time", null)
}

data class StatsUiState(
    val stats: CoordinatorStats? = null,
    val range: StatsRange = StatsRange.ALL,
    val loading: Boolean = false,
    val error: String? = null,
)

/** Coordinator Insights: performance summary from the trips they've posted, over a chosen window. */
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val dispatch: DispatchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init { refresh() }

    fun setRange(range: StatsRange) {
        if (range == _state.value.range) return
        _state.update { it.copy(range = range) }
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = dispatch.coordinatorStats(_state.value.range.days)) {
                is AppResult.Ok -> _state.update { it.copy(loading = false, stats = r.value) }
                is AppResult.Err -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }
}
