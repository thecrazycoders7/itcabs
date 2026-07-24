package com.itcabs.feature.dispatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itcabs.domain.AppResult
import com.itcabs.domain.repository.DispatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLngUi(val lat: Double, val lng: Double)

data class TripMapUiState(
    val pickup: LatLngUi? = null,          // area centroid of the trip's pickup area
    val driver: LatLngUi? = null,          // live driver position (polled)
    val etaText: String? = null,           // "3.2 km · ~10 min" once both points known
)

/** Backs the trip map: resolves the pickup area centroid, then polls the driver's live location. */
@HiltViewModel
class TripMapViewModel @Inject constructor(
    private val dispatch: DispatchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TripMapUiState())
    val state: StateFlow<TripMapUiState> = _state.asStateFlow()
    private var started = false

    /** Call once with the leg + its pickup area name. Loads the pin, then polls the driver every 5s. */
    fun start(legId: Long, area: String) {
        if (started) return
        started = true
        viewModelScope.launch {
            (dispatch.areas() as? AppResult.Ok)?.value
                ?.firstOrNull { it.name.equals(area, ignoreCase = true) }
                ?.let { _state.update { s -> s.copy(pickup = LatLngUi(it.lat, it.lng)) } }
        }
        viewModelScope.launch {
            while (true) {
                (dispatch.driverLocation(legId) as? AppResult.Ok)?.value?.let { loc ->
                    val lat = loc.lat; val lng = loc.lng
                    if (lat != null && lng != null) {
                        _state.update { it.copy(driver = LatLngUi(lat, lng)) }
                        recomputeEta()
                    }
                }
                delay(5_000)
            }
        }
    }

    private fun recomputeEta() {
        val p = _state.value.pickup ?: return
        val d = _state.value.driver ?: return
        val km = haversineKm(d.lat, d.lng, p.lat, p.lng)
        // ponytail: ~20 km/h city average — a real road ETA needs Distance Matrix (separate server key).
        val mins = (km / 20.0 * 60).toInt().coerceAtLeast(1)
        _state.update { it.copy(etaText = "%.1f km · ~%d min".format(km, mins)) }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
