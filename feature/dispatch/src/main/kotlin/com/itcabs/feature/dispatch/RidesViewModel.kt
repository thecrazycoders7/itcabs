package com.itcabs.feature.dispatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.NewRide
import com.itcabs.domain.model.Ride
import com.itcabs.domain.repository.AuthRepository
import com.itcabs.domain.repository.RideRepository
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RidesUiState(
    val results: List<Ride> = emptyList(),
    val myRides: List<Ride> = emptyList(),
    val myBookings: List<Ride> = emptyList(),
    val loading: Boolean = false,
    val posting: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val myGender: String? = null,
    val riders: Map<Long, List<com.itcabs.domain.model.RideRider>> = emptyMap(),
)

/** Backs the carpooling screen: browse/book rides, offer a ride, manage my rides + bookings. */
@HiltViewModel
class RidesViewModel @Inject constructor(
    private val rides: RideRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RidesUiState())
    val state: StateFlow<RidesUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch { auth.getUserFlow().first()?.let { u -> _state.update { it.copy(myGender = u.gender) } } }
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            // Browse all open rides for now (no origin/dest filter until Places billing is on).
            (rides.search(null, null, null, null, null) as? AppResult.Ok)?.let { r -> _state.update { it.copy(results = r.value) } }
            (rides.myRides() as? AppResult.Ok)?.let { r -> _state.update { it.copy(myRides = r.value) } }
            when (val b = rides.myBookings()) {
                is AppResult.Ok -> _state.update { it.copy(loading = false, myBookings = b.value) }
                is AppResult.Err -> _state.update { it.copy(loading = false, error = b.message) }
            }
        }
    }

    fun create(input: NewRide, onDone: () -> Unit) {
        _state.update { it.copy(posting = true, error = null) }
        viewModelScope.launch {
            when (val r = rides.create(input)) {
                is AppResult.Ok -> { _state.update { it.copy(posting = false, notice = "Ride posted") }; refresh(); onDone() }
                is AppResult.Err -> _state.update { it.copy(posting = false, error = r.message) }
            }
        }
    }

    fun loadRiders(rideId: Long) {
        viewModelScope.launch {
            (rides.riders(rideId) as? AppResult.Ok)?.let { r -> _state.update { it.copy(riders = it.riders + (rideId to r.value)) } }
        }
    }

    fun confirmPickup(rideId: Long, riderId: Long, otp: String) {
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            when (val r = rides.confirmPickup(rideId, riderId, otp)) {
                is AppResult.Ok -> loadRiders(rideId)
                is AppResult.Err -> _state.update { it.copy(error = r.message) }
            }
        }
    }

    fun rate(rideId: Long, rateeId: Long, stars: Int, review: String?) {
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            when (val r = rides.rate(rideId, rateeId, stars, review)) {
                is AppResult.Ok -> _state.update { it.copy(notice = "Thanks for rating!") }
                is AppResult.Err -> _state.update { it.copy(error = r.message) }
            }
        }
    }

    fun book(rideId: Long, seats: Int) = act { rides.book(rideId, seats).map() }
    fun cancelBooking(rideId: Long) = act { rides.cancelBooking(rideId) }
    fun setStatus(rideId: Long, status: String) = act { rides.setStatus(rideId, status) }

    private fun <T> AppResult<T>.map(): AppResult<Unit> = when (this) {
        is AppResult.Ok -> AppResult.Ok(Unit); is AppResult.Err -> this
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

    fun clearNotice() = _state.update { it.copy(notice = null) }
}
