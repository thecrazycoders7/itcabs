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
    val area: String = "",
    val seats: String = "1",
    val fareRupees: String = "",
    val passengerName: String = "",
    val passengerPhone: String = "",
)

data class CreateJobUiState(
    val office: String = "",
    val shift: String = "",
    val vehicleType: String = "Sedan",
    val legs: List<LegForm> = listOf(LegForm()),
    val areas: List<String> = emptyList(),
    /** Hours from now to schedule the job; 0 = post immediately. */
    val scheduleHours: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val published: Boolean = false,
    val notice: String? = null,   // e.g. "Template saved"
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

    init {
        // Load the pickable area list; posting still works if this fails (area stays optional).
        viewModelScope.launch {
            (dispatch.areas() as? AppResult.Ok)?.value?.let { list ->
                _state.update { it.copy(areas = list.map { a -> a.name }) }
            }
        }
    }

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

    fun onScheduleHours(h: Int) = _state.update { it.copy(scheduleHours = h.coerceAtLeast(0)) }

    /**
     * Bulk-add legs from pasted roster lines. One leg per non-blank line:
     * `pickup, drop, area, fare[, passenger, phone]` (extra fields optional).
     */
    fun addFromPaste(text: String) {
        val parsed = text.lines().mapNotNull { line ->
            val f = line.split(",").map { it.trim() }
            if (f.size < 2 || f[0].isBlank() || f[1].isBlank()) return@mapNotNull null
            LegForm(
                pickup = f[0], drop = f[1],
                area = f.getOrNull(2).orEmpty(),
                fareRupees = f.getOrNull(3).orEmpty().filter { it.isDigit() || it == '.' },
                passengerName = f.getOrNull(4).orEmpty(),
                passengerPhone = f.getOrNull(5).orEmpty(),
            )
        }
        if (parsed.isEmpty()) { _state.update { it.copy(error = "Couldn't parse any lines. Use: pickup, drop, area, fare") }; return }
        // Replace a lone empty starter leg; otherwise append.
        _state.update {
            val base = if (it.legs.size == 1 && it.legs[0].pickup.isBlank() && it.legs[0].drop.isBlank()) emptyList() else it.legs
            it.copy(legs = base + parsed, error = null)
        }
    }

    private fun buildJob(s: CreateJobUiState): NewJob {
        val publishAt = if (s.scheduleHours > 0)
            java.time.OffsetDateTime.now().plusHours(s.scheduleHours.toLong()).toString() else null
        return NewJob(
            office = s.office, shift = s.shift, publishAt = publishAt,
            legs = s.legs.map { form ->
                NewLeg(
                    pickup = form.pickup, drop = form.drop, area = form.area, timeWindow = s.shift,
                    vehicleType = s.vehicleType,
                    farePaise = ((form.fareRupees.toDoubleOrNull() ?: 0.0) * 100).toLong(),
                    seats = form.seats.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                    passengerName = form.passengerName, passengerPhone = form.passengerPhone,
                )
            },
        )
    }

    fun publish() {
        val s = _state.value
        if (!s.canPublish) {
            _state.update { it.copy(error = "Fill office, shift, and every leg's pickup/drop/fare.") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = dispatch.postJob(buildJob(s))) {
                is AppResult.Ok -> _state.update { it.copy(loading = false, published = true) }
                is AppResult.Err -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    /** Save the current form as a reusable template (optionally recurring/auto-daily). Doesn't post. */
    fun saveAsTemplate(name: String, recurring: Boolean) {
        val s = _state.value
        if (name.isBlank() || !s.canPublish) {
            _state.update { it.copy(error = "Give the template a name and fill every leg first.") }
            return
        }
        viewModelScope.launch {
            when (dispatch.saveTemplate(name, buildJob(s), s.vehicleType, recurring)) {
                is AppResult.Ok -> _state.update { it.copy(notice = "Template saved") }
                is AppResult.Err -> _state.update { it.copy(error = "Couldn't save template") }
            }
        }
    }
}
