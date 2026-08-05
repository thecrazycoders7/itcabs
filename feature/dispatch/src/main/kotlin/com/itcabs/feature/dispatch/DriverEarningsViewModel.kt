package com.itcabs.feature.dispatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.DriverEarnings
import com.itcabs.domain.repository.DriverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EarningsUiState(
    val earnings: DriverEarnings? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

/** Backs the driver Earnings screen: settled/pending totals + recent trips (legs + company jobs). */
@HiltViewModel
class DriverEarningsViewModel @Inject constructor(
    private val driver: DriverRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EarningsUiState())
    val state: StateFlow<EarningsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = driver.earnings()) {
                is AppResult.Ok -> _state.update { it.copy(loading = false, earnings = r.value) }
                is AppResult.Err -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }
}
