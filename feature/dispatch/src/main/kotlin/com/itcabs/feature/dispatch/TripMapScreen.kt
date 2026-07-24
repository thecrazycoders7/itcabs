package com.itcabs.feature.dispatch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * Trip map: pickup-area pin + the driver's live marker (polled every 5s) + a distance/ETA readout.
 * Pins use the area centroid (we don't geocode exact addresses); the driver marker is real GPS.
 */
@Composable
fun TripMapScreen(
    legId: Long,
    area: String,
    pickupLabel: String,
    onBack: () -> Unit,
    viewModel: TripMapViewModel = hiltViewModel(),
) {
    LaunchedEffect(legId) { viewModel.start(legId, area) }
    val state by viewModel.state.collectAsState()

    // Center on the pickup area (fallback to Hyderabad's tech corridor until it loads).
    val hyderabad = LatLng(17.4401, 78.3489)
    val center = state.pickup?.let { LatLng(it.lat, it.lng) } ?: hyderabad
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center, 12f)
    }
    LaunchedEffect(state.pickup) {
        state.pickup?.let { cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(it.lat, it.lng), 12f) }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Trip map", style = MaterialTheme.typography.titleLarge)
        }

        Box(Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
            ) {
                state.pickup?.let {
                    Marker(state = MarkerState(LatLng(it.lat, it.lng)), title = "Pickup: $pickupLabel")
                }
                state.driver?.let {
                    Marker(state = MarkerState(LatLng(it.lat, it.lng)), title = "Driver")
                }
            }

            // Distance/ETA + a "waiting for location" hint until the driver's app reports in.
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium).padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (state.driver == null) {
                        Text("Waiting for driver location…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(state.etaText ?: "Driver on the map", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Text("to pickup · estimate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
