package com.itcabs.feature.dispatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.Leg
import com.itcabs.domain.model.LegStatus
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
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class CoordinatorHomeViewModel @Inject constructor(
    private val dispatch: DispatchRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CoordinatorHomeUiState())
    val state: StateFlow<CoordinatorHomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val user = auth.currentUser()
            if (user is AppResult.Ok) {
                dispatch.getMyLegsFlow(user.value.id).collect { legs ->
                    _state.update { it.copy(legs = legs) }
                }
            }
        }
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

    fun setStatus(legId: Long, status: LegStatus) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = dispatch.setStatus(legId, status)) {
                is AppResult.Ok -> refresh()
                is AppResult.Err -> _state.update { it.copy(loading = false, error = result.message) }
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
}
