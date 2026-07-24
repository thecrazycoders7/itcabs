package com.itcabs.feature.dispatch

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itcabs.domain.model.CompanyJob
import com.itcabs.domain.model.JobStop
import com.itcabs.domain.model.LegStatus

/** Driver: available company trips (claim) + my company trips (run stop-by-stop). */
@Composable
fun DriverCompanyScreen(viewModel: DriverCompanyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var mapJob by remember { mutableStateOf<CompanyJob?>(null) }
    var chatJob by remember { mutableStateOf<CompanyJob?>(null) }
    var otpStop by remember { mutableStateOf<JobStop?>(null) }
    val context = LocalContext.current

    mapJob?.let { CompanyRouteMapScreen(job = it, onBack = { mapJob = null }); return }
    chatJob?.let { ChatScreen(legId = it.id, onBack = { chatJob = null }, companyJob = true); return }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Company Trips", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = viewModel::refresh) { Text("Refresh") }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }

        if (state.loading && state.feed.isEmpty() && state.trips.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.trips.isNotEmpty()) {
                item { Text("My trips", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                items(state.trips, key = { "t-${it.id}" }) { job ->
                    MyCompanyTripCard(job, onMap = { mapJob = job }, onConfirm = { otpStop = it },
                        onNavigate = { stop -> navigateTo(context, stop) }, onChat = { chatJob = job },
                        onRelease = { viewModel.release(job.id) }, onComplete = { viewModel.complete(job.id) })
                }
            }
            item {
                Text("Available", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp))
            }
            if (state.feed.isEmpty()) item { Text("No open company trips right now.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(state.feed, key = { "f-${it.id}" }) { job ->
                FeedCompanyCard(job, claiming = state.claimingId == job.id, onClaim = { viewModel.claim(job.id) })
            }
        }
    }

    otpStop?.let { stop ->
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { otpStop = null },
            title = { Text("Confirm pickup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ask ${stop.employeeName} for their 4-digit code.")
                    OutlinedTextField(code, { if (it.length <= 4) code = it.filter(Char::isDigit) },
                        label = { Text("Code") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = { TextButton(enabled = code.length == 4, onClick = { viewModel.confirmPickup(stop.id, code); otpStop = null }) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = { otpStop = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FeedCompanyCard(job: CompanyJob, claiming: Boolean, onClaim: () -> Unit) {
    CardBox {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(job.companyName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(formatRupees(job.farePaise), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text("${job.tripType.name.lowercase().replaceFirstChar { it.uppercase() }} · ${job.stops.size} stops",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        job.stops.take(4).forEachIndexed { i, s -> Text("${i + 1}. ${s.employeeName}" + if (s.address.isNotBlank()) " · ${s.address}" else "", style = MaterialTheme.typography.bodySmall) }
        Row(Modifier.fillMaxWidth(), Arrangement.End) {
            Button(onClick = onClaim, enabled = !claiming, shape = MaterialTheme.shapes.small) {
                if (claiming) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Claim Trip")
            }
        }
    }
}

@Composable
private fun MyCompanyTripCard(job: CompanyJob, onMap: () -> Unit, onConfirm: (JobStop) -> Unit, onNavigate: (JobStop) -> Unit, onChat: () -> Unit, onRelease: () -> Unit, onComplete: () -> Unit) {
    CardBox {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(job.companyName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(job.status.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text("${job.tripType.name.lowercase().replaceFirstChar { it.uppercase() }} · ${formatRupees(job.farePaise)}",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (job.status == LegStatus.COMPLETED) Text(if (job.paid) "Paid ✓" else "Payment pending",
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
            color = if (job.paid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        val active = job.status == LegStatus.CLAIMED || job.status == LegStatus.CONFIRMED
        val anyPicked = job.stops.any { it.pickedUp }
        job.stops.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(
                    "${i + 1}. ${s.employeeName}" + if (s.pickedUp) "  ✓" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (s.pickedUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (active) {
                    TextButton(onClick = { onNavigate(s) }) { Icon(Icons.Filled.Navigation, null, Modifier.size(16.dp)) }
                    if (!s.pickedUp) TextButton(onClick = { onConfirm(s) }) { Text("Pickup") }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), Arrangement.End, Alignment.CenterVertically) {
            TextButton(onClick = onChat) { Text("Chat") }
            TextButton(onClick = onMap) { Icon(Icons.Filled.Map, null, Modifier.size(16.dp)); Text(" Map") }
            // Release only before any pickup has happened (honest early bail-out).
            if (active && !anyPicked) TextButton(onClick = onRelease) { Text("Release", color = MaterialTheme.colorScheme.error) }
            if (active) {
                val context = LocalContext.current
                Button(onClick = { navigateRoute(context, job) }, enabled = job.stops.any { it.lat != null }, shape = MaterialTheme.shapes.small) {
                    Icon(Icons.Filled.Navigation, null, Modifier.size(16.dp)); Text(" Route")
                }
                // Finish the whole job once every stop is picked up.
                if (job.stops.all { it.pickedUp }) Button(onClick = onComplete, shape = MaterialTheme.shapes.small) { Text(" Complete") }
            }
        }
    }
}

@Composable
private fun CardBox(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp), content = content,
    )
}

private fun navigateTo(context: android.content.Context, stop: JobStop) {
    val uri = if (stop.lat != null && stop.lng != null) "geo:${stop.lat},${stop.lng}?q=${stop.lat},${stop.lng}(${Uri.encode(stop.employeeName)})"
    else "geo:0,0?q=${Uri.encode(stop.address.ifBlank { stop.employeeName })}"
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
}

/**
 * Launch Google Maps turn-by-turn through all located stops in order. Origin is omitted so Maps
 * starts from the driver's current location; waypoints are visited in the given (optimized) order.
 */
private fun navigateRoute(context: android.content.Context, job: CompanyJob) {
    val pts = job.stops.filter { it.lat != null && it.lng != null }.map { "${it.lat},${it.lng}" }
    if (pts.isEmpty()) return
    val destination = pts.last()
    val waypoints = pts.dropLast(1).joinToString("|")
    val url = StringBuilder("https://www.google.com/maps/dir/?api=1&travelmode=driving&destination=$destination")
    if (waypoints.isNotBlank()) url.append("&waypoints=${Uri.encode(waypoints)}")
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())))
}
