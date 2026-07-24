package com.itcabs.feature.dispatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.KycStatus
import com.itcabs.domain.model.Leg
import com.itcabs.domain.repository.DispatchRepository
import com.itcabs.domain.repository.DriverRepository
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
    val upcoming: List<Leg> = emptyList(),
    val loading: Boolean = false,
    val claimingId: Long? = null,
    val error: String? = null,
    val notice: String? = null,
    /** null while the driver's verification status is still loading. */
    val kycStatus: KycStatus? = null,
    val available: Boolean = true,
    // Feed preferences (client-side): only show trips I can/ want to do.
    val vehicleFilter: String? = null,
    val minFareRupees: String = "",
) {
    /** Only a verified driver may claim. Allow while status is still unknown (backend re-checks). */
    val canClaim: Boolean get() = kycStatus == null || kycStatus == KycStatus.VERIFIED

    /** Legs after the driver's vehicle + min-fare preferences. */
    val visibleLegs: List<Leg>
        get() {
            val minPaise = (minFareRupees.toLongOrNull() ?: 0) * 100
            return legs
                .filter { vehicleFilter == null || it.vehicleType.equals(vehicleFilter, ignoreCase = true) }
                .filter { it.farePaise >= minPaise }
        }

    val vehicleOptions: List<String> get() = legs.map { it.vehicleType }.filter { it.isNotBlank() }.distinct()
}

@HiltViewModel
class DriverFeedViewModel @Inject constructor(
    private val dispatch: DispatchRepository,
    private val driver: DriverRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DriverFeedUiState())
    val state: StateFlow<DriverFeedUiState> = _state.asStateFlow()

    // Driver coords (set once the screen obtains location); feed becomes nearest-first.
    private var lat: Double? = null
    private var lng: Double? = null

    /** Legs sorted nearest-first when distances are known; unknown areas sink to the bottom. */
    private fun sorted(legs: List<Leg>): List<Leg> =
        if (legs.none { it.distanceKm != null }) legs
        else legs.sortedWith(compareBy(nullsLast()) { it.distanceKm })

    init {
        viewModelScope.launch {
            dispatch.getFeedFlow().collect { legs ->
                _state.update { it.copy(legs = sorted(legs)) }
            }
        }
        // Realtime (ADR-0008): a newly posted (or claimed) leg pushes an event → re-fetch the feed.
        viewModelScope.launch { dispatch.legEvents().collect { refresh() } }
        refresh()
    }

    fun onAreaChange(value: String) = _state.update { it.copy(area = value) }
    fun onVehicleFilter(v: String?) = _state.update { it.copy(vehicleFilter = v) }
    fun onMinFare(v: String) = _state.update { it.copy(minFareRupees = v.filter(Char::isDigit)) }

    /** Screen supplies device location (coarse is fine — relevance is area-level). */
    fun onLocation(latitude: Double, longitude: Double) {
        // Push location for on-trip tracking (fire-and-forget) even if it hasn't moved enough to refresh.
        viewModelScope.launch { dispatch.postLocation(latitude, longitude) }
        if (lat == latitude && lng == longitude) return
        lat = latitude; lng = longitude
        refresh()
    }

    /** Go online/offline for new-trip pushes. */
    fun setAvailable(available: Boolean) {
        _state.update { it.copy(available = available) }
        viewModelScope.launch { driver.setAvailability(available) }
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            // Re-check verification too, so approving a driver clears the banner on the next refresh
            // (instead of only after a full app restart).
            (driver.myProfile() as? AppResult.Ok)?.let { r -> _state.update { it.copy(kycStatus = r.value.kycStatus, available = r.value.available) } }
            (dispatch.upcoming(lat, lng) as? AppResult.Ok)?.let { r -> _state.update { it.copy(upcoming = r.value) } }
            when (val result = dispatch.feed(_state.value.area.ifBlank { null }, null, lat, lng)) {
                is AppResult.Ok -> _state.update { it.copy(loading = false, legs = sorted(result.value)) }
                is AppResult.Err -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    /** Claim a leg. On 409 the leg was taken by someone else — surface it and refresh the feed. */
    fun claim(legId: Long) {
        val status = _state.value.kycStatus
        if (status != null && status != KycStatus.VERIFIED) {
            val why = when (status) {
                KycStatus.NONE -> "Complete your KYC to claim trips."
                KycStatus.PENDING -> "Your verification is pending — you can't claim trips yet."
                else -> "Your account can't claim trips. Contact support."
            }
            _state.update { it.copy(notice = why) }
            return
        }
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
