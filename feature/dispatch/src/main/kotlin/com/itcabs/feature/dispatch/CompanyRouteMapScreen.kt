package com.itcabs.feature.dispatch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.itcabs.domain.model.CompanyJob

/** All employee stops of a company job as ordered pins. Pins use each stop's stored GPS. */
@Composable
fun CompanyRouteMapScreen(job: CompanyJob, onBack: () -> Unit) {
    val points = job.stops.filter { it.lat != null && it.lng != null }
    val center = points.firstOrNull()?.let { LatLng(it.lat!!, it.lng!!) } ?: LatLng(17.4401, 78.3489)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center, 11f)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("${job.companyName} · route", style = MaterialTheme.typography.titleLarge)
        }
        if (points.isEmpty()) {
            Text("No stop coordinates yet — pins appear once stops have a location.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        } else {
            GoogleMap(modifier = Modifier.fillMaxSize(), cameraPositionState = cameraPositionState) {
                points.forEachIndexed { i, s ->
                    Marker(
                        state = MarkerState(LatLng(s.lat!!, s.lng!!)),
                        title = "${i + 1}. ${s.employeeName}",
                        snippet = s.address,
                    )
                }
            }
        }
    }
}
