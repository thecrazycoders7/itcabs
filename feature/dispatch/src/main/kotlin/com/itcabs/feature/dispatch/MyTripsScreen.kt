package com.itcabs.feature.dispatch

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itcabs.domain.model.Leg
import com.itcabs.domain.model.LegStatus

@Composable
fun MyTripsScreen(viewModel: MyTripsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var chatLegId by rememberSaveable { mutableStateOf<Long?>(null) }

    chatLegId?.let { id ->
        ChatScreen(legId = id, onBack = { chatLegId = null })
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("My Trips", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Refresh",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { viewModel.refresh() }
                    .padding(8.dp),
            )
        }

        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // Earnings at a glance: what's been paid vs. still owed on completed trips.
        val completed = state.trips.filter { it.status == LegStatus.COMPLETED }
        val pending = completed.filter { !it.paid }.sumOf { it.farePaise }
        val earned = completed.filter { it.paid }.sumOf { it.farePaise }
        if (completed.isNotEmpty()) {
            Text(
                "Earned ${formatRupees(earned)}" + if (pending > 0) " · ${formatRupees(pending)} pending" else "",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (pending > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (state.trips.isEmpty() && !state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No claimed trips yet. Claim one from Available Trips.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.trips, key = { it.id }) { trip ->
                    TripCard(
                        trip,
                        onChat = { chatLegId = trip.id },
                        onStage = { stage, otp -> viewModel.setStage(trip.id, stage, otp) },
                        onRelease = { viewModel.release(trip.id) },
                        onComplete = { viewModel.complete(trip.id) },
                    )
                }
            }
        }
    }
}

/** Live-progress ladder the driver walks: null → EN_ROUTE → ARRIVED → STARTED (coordinator then completes). */
private fun nextStage(current: String?): Pair<String, String>? = when (current) {
    null -> "EN_ROUTE" to "I'm on the way"
    "EN_ROUTE" -> "ARRIVED" to "Arrived at pickup"
    "ARRIVED" -> "STARTED" to "Start trip"
    else -> null // STARTED: nothing more for the driver to report
}

private fun stageLabel(stage: String?): String? = when (stage) {
    "EN_ROUTE" -> "On the way to pickup"
    "ARRIVED" -> "Arrived at pickup"
    "STARTED" -> "Trip in progress"
    else -> null
}

private const val SUPPORT_PHONE = "112" // pilot: national emergency; swap for the ops desk line later

@Composable
private fun TripCard(trip: Leg, onChat: () -> Unit, onStage: (String, String?) -> Unit, onRelease: () -> Unit, onComplete: () -> Unit) {
    val context = LocalContext.current
    var askOtp by remember { mutableStateOf(false) }
    if (askOtp) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askOtp = false },
            title = { Text("Start trip") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ask ${trip.passengerName.ifBlank { "the passenger" }} for their 4-digit pickup code.")
                    OutlinedTextField(
                        value = code,
                        onValueChange = { if (it.length <= 4) code = it.filter(Char::isDigit) },
                        label = { Text("Pickup code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { askOtp = false; onStage("STARTED", code) }, enabled = code.length == 4) { Text("Start") } },
            dismissButton = { TextButton(onClick = { askOtp = false }) { Text("Cancel") } },
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatRupees(trip.farePaise),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            StatusPill(trip.status)
        }
        RoutePoint("PICKUP", trip.pickup, MaterialTheme.colorScheme.primary)
        RoutePoint("DROP", trip.drop, MaterialTheme.colorScheme.outline)
        // Who am I picking up — with a one-tap call so the driver can reach the employee.
        if (trip.passengerName.isNotBlank() || trip.passengerPhone.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Person, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(trip.passengerName.ifBlank { "Passenger" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (trip.passengerPhone.isNotBlank()) {
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${trip.passengerPhone}")))
                    }) {
                        Icon(Icons.Filled.Call, null, Modifier.size(16.dp))
                        Text(" Call")
                    }
                }
            }
        }
        if (trip.timeWindow.isNotBlank() || trip.vehicleType.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (trip.timeWindow.isNotBlank()) {
                    Icon(Icons.Filled.Schedule, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(trip.timeWindow, style = MaterialTheme.typography.bodyMedium)
                }
                if (trip.vehicleType.isNotBlank()) {
                    Icon(Icons.Filled.DirectionsCar, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(trip.vehicleType, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (trip.status == LegStatus.COMPLETED) {
            Text(
                if (trip.paid) "Paid ✓" else "Payment pending",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (trip.paid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        stageLabel(trip.tripStage)?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        val active = trip.status == LegStatus.CLAIMED || trip.status == LegStatus.CONFIRMED
        // Navigate + SOS on an active trip: open Maps to the pickup, or dial support in an emergency.
        if (active) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    // geo: query opens whatever nav app the driver has — no Maps API key needed.
                    val q = Uri.encode(if (trip.tripStage == "STARTED") trip.drop else trip.pickup)
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q")))
                }) {
                    Icon(Icons.Filled.Navigation, null, Modifier.size(16.dp)); Text(" Navigate")
                }
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$SUPPORT_PHONE"))) }) {
                    Icon(Icons.Filled.Warning, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Text(" SOS", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        // Chat + release + live-progress / complete.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onChat) { Text("Chat") }
            // Release before the trip starts — an honest bail-out with no no-show hit.
            if (active && trip.tripStage != "STARTED") {
                TextButton(onClick = onRelease) { Text("Release", color = MaterialTheme.colorScheme.error) }
            }
            if (trip.tripStage == "STARTED") {
                androidx.compose.material3.Button(onClick = onComplete, shape = MaterialTheme.shapes.small) { Text("Complete") }
            } else if (active) {
                nextStage(trip.tripStage)?.let { (stage, label) ->
                    androidx.compose.material3.Button(
                        onClick = { if (stage == "STARTED") askOtp = true else onStage(stage, null) },
                        shape = MaterialTheme.shapes.small,
                    ) { Text(label) }
                }
            }
        }
    }
}

@Composable
private fun RoutePoint(label: String, place: String, dotColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(place, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatusPill(status: LegStatus) {
    // Claimed = in progress (blue), Completed = done (green-ish), Cancelled = muted.
    val (bg, fg) = when (status) {
        LegStatus.CLAIMED, LegStatus.CONFIRMED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        LegStatus.COMPLETED -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(status.name, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}
