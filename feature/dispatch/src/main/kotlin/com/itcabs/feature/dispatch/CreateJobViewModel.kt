package com.itcabs.feature.dispatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.NewJob
import com.itcabs.domain.model.NewLeg
import com.itcabs.domain.repository.DispatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One editable pickup leg. Fare is entered in rupees (converted to paise on publish). */
data class LegForm(
    val pickup: String = "",
    val drop: String = "",
    val seats: String = "1",
    val fareRupees: String = "",
)

data class CreateJobUiState(
    val office: String = "",
    val shift: String = "",
    val vehicleType: String = "Sedan",
    val legs: List<LegForm> = listOf(LegForm()),
    val loading: Boolean = false,
    val error: String? = null,
    val published: Boolean = false,
) {
    val canPublish: Boolean
        get() = office.isNotBlank() && shift.isNotBlank() && legs.isNotEmpty() &&
            legs.all { it.pickup.isNotBlank() && it.drop.isNotBlank() && it.fareRupees.toDoubleOrNull() != null }
}

@HiltViewModel
class CreateJobViewModel @Inject constructor(
    private val dispatch: DispatchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateJobUiState())
    val state: StateFlow<CreateJobUiState> = _state.asStateFlow()

    fun onOfficeChange(v: String) = _state.update { it.copy(office = v, error = null) }
    fun onShiftChange(v: String) = _state.update { it.copy(shift = v, error = null) }
    fun onVehicleChange(v: String) = _state.update { it.copy(vehicleType = v) }

    fun addLeg() = _state.update { it.copy(legs = it.legs + LegForm()) }

    fun removeLeg(index: Int) = _state.update {
        if (it.legs.size <= 1) it else it.copy(legs = it.legs.filterIndexed { i, _ -> i != index })
    }

    fun updateLeg(index: Int, leg: LegForm) = _state.update {
        it.copy(legs = it.legs.mapIndexed { i, existing -> if (i == index) leg else existing }, error = null)
    }

    fun publish() {
        val s = _state.value
        if (!s.canPublish) {
            _state.update { it.copy(error = "Fill office, shift, and every leg's pickup/drop/fare.") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val job = NewJob(
                office = s.office,
                shift = s.shift,
                legs = s.legs.map { form ->
                    NewLeg(
                        pickup = form.pickup,
                        drop = form.drop,
                        timeWindow = s.shift,
                        vehicleType = s.vehicleType,
                        // rupees → paise, never float in the wire type
                        farePaise = ((form.fareRupees.toDoubleOrNull() ?: 0.0) * 100).toLong(),
                        seats = form.seats.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                    )
                },
            )
            when (val result = dispatch.postJob(job)) {
                is AppResult.Ok -> _state.update { it.copy(loading = false, published = true) }
                is AppResult.Err -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }
}
