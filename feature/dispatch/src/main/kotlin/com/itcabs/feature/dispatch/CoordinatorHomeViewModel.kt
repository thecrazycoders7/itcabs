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

data class CoordinatorHomeUiState(
    val legs: List<Leg> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class CoordinatorHomeViewModel @Inject constructor(
    private val dispatch: DispatchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CoordinatorHomeUiState())
    val state: StateFlow<CoordinatorHomeUiState> = _state.asStateFlow()

    init { refresh() }

    // ponytail: manual refresh to watch OPEN→CLAIMED; live push via WebSocket is a later milestone.
    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = dispatch.myLegs()) {
                is AppResult.Ok -> _state.update { it.copy(loading = false, legs = result.value) }
                is AppResult.Err -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }
}
