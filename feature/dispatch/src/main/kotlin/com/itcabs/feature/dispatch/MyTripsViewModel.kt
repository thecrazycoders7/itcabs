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

data class MyTripsUiState(
    val trips: List<Leg> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

/** The driver's claimed trips. Read-only: status is advanced by the coordinator (ADR workflow). */
@HiltViewModel
class MyTripsViewModel @Inject constructor(
    private val dispatch: DispatchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MyTripsUiState())
    val state: StateFlow<MyTripsUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = dispatch.myClaims()) {
                is AppResult.Ok -> _state.update { it.copy(loading = false, trips = result.value) }
                is AppResult.Err -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }
}
