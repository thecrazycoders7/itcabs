package com.itcabs.feature.dispatch

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itcabs.domain.model.CompanyJob
import com.itcabs.domain.model.LegStatus

/** Coordinator: list of multi-stop company jobs + create + manage. */
@Composable
fun CompanyJobsScreen(viewModel: CompanyJobViewModel = hiltViewModel()) {
    var showCreate by remember { mutableStateOf(false) }
    var assignJob by remember { mutableStateOf<CompanyJob?>(null) }
    var driverProfileId by remember { mutableStateOf<Long?>(null) }
    var mapJob by remember { mutableStateOf<CompanyJob?>(null) }
    var chatJob by remember { mutableStateOf<CompanyJob?>(null) }
    val state by viewModel.state.collectAsState()

    if (showCreate) {
        CreateCompanyJobScreen(onDone = { showCreate = false })
        return
    }
    mapJob?.let { CompanyRouteMapScreen(job = it, onBack = { mapJob = null }, track = true); return }
    chatJob?.let { ChatScreen(legId = it.id, onBack = { chatJob = null }, companyJob = true); return }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val context = androidx.compose.ui.platform.LocalContext.current
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Company Jobs", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { shareCompanyCsv(context, state.jobs) }) { Icon(Icons.Filled.Share, "Export CSV") }
            TextButton(onClick = viewModel::refresh) { Text("Refresh") }
            Button(onClick = { showCreate = true }, shape = MaterialTheme.shapes.small) { Icon(Icons.Filled.Add, null); Text("New") }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
        val outstanding = state.jobs.filter { it.status == LegStatus.COMPLETED && !it.paid }.sumOf { it.farePaise }
        if (outstanding > 0) Text(
            "${formatRupees(outstanding)} outstanding to drivers",
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        when {
            state.loading && state.jobs.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            state.jobs.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("No company jobs yet. Tap “New”.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.jobs, key = { it.id }) { job ->
                    CompanyJobCard(
                        job = job,
                        onAssign = { assignJob = job; viewModel.loadDrivers() },
                        onConfirm = { viewModel.setStatus(job.id, LegStatus.CONFIRMED) },
                        onComplete = { viewModel.setStatus(job.id, LegStatus.COMPLETED) },
                        onCancel = { viewModel.setStatus(job.id, LegStatus.CANCELLED) },
                        onNoShow = { viewModel.markNoShow(job.id) },
                        onMarkPaid = { viewModel.markPaid(job.id) },
                        onDriverProfile = { job.claimedBy?.let { driverProfileId = it } },
                        onMap = { mapJob = job },
                        onChat = { chatJob = job },
                    )
                }
            }
        }
    }

    assignJob?.let { job ->
        AssignDriverDialog(state.verifiedDrivers, onDismiss = { assignJob = null }, onPick = { viewModel.assign(job.id, it); assignJob = null })
    }
    driverProfileId?.let { DriverProfileDialog(it, onDismiss = { driverProfileId = null }) }
}

@Composable
private fun CompanyJobCard(job: CompanyJob, onAssign: () -> Unit, onConfirm: () -> Unit, onComplete: () -> Unit, onCancel: () -> Unit, onNoShow: () -> Unit, onMarkPaid: () -> Unit, onDriverProfile: () -> Unit, onMap: () -> Unit, onChat: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(job.companyName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(job.status.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text("${job.tripType.name.lowercase().replaceFirstChar { it.uppercase() }} · ${job.stops.size} stops · ${formatRupees(job.farePaise)}",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        job.claimedByName?.let {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Driver: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                Row {
                    if (job.status == LegStatus.CLAIMED || job.status == LegStatus.CONFIRMED) {
                        TextButton(onClick = onChat) { Text("Chat") }
                        TextButton(onClick = onMap) { Text("Map") }
                    }
                    TextButton(onClick = onDriverProfile) { Text("Profile") }
                }
            }
        }

        // Ordered stops with pickup state + the code to relay to each employee.
        job.stops.forEachIndexed { i, s ->
            Text(
                "${i + 1}. ${s.employeeName}" + (if (s.address.isNotBlank()) " · ${s.address}" else "") +
                    (if (s.pickedUp) "  ✓ picked up" else s.pickupOtp?.let { "  code $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = if (s.pickedUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }

        Row(Modifier.fillMaxWidth(), Arrangement.End, Alignment.CenterVertically) {
            when (job.status) {
                LegStatus.OPEN -> {
                    TextButton(onClick = onAssign) { Text("Assign") }
                    TextButton(onClick = onCancel) { Text("Cancel", color = MaterialTheme.colorScheme.error) }
                }
                LegStatus.CLAIMED -> {
                    TextButton(onClick = onNoShow) { Text("No-show", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = onCancel) { Text("Cancel", color = MaterialTheme.colorScheme.error) }
                    Button(onClick = onConfirm, shape = MaterialTheme.shapes.small) { Text("Confirm") }
                }
                LegStatus.CONFIRMED -> {
                    TextButton(onClick = onNoShow) { Text("No-show", color = MaterialTheme.colorScheme.error) }
                    Button(onClick = onComplete, shape = MaterialTheme.shapes.small) { Text("Complete") }
                }
                LegStatus.COMPLETED -> {
                    if (job.paid) Text("Paid ✓", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    else TextButton(onClick = onMarkPaid) { Text("Mark Paid") }
                }
                else -> {}
            }
        }
    }
}

/** CSV of company jobs for finance/billing, via the Android share sheet. */
private fun shareCompanyCsv(context: android.content.Context, jobs: List<CompanyJob>) {
    val header = "company,trip_type,stops,fare_rupees,status,driver,paid"
    val rows = jobs.joinToString("\n") { j ->
        listOf(j.companyName, j.tripType.name, j.stops.size.toString(), (j.farePaise / 100).toString(),
            j.status.name, j.claimedByName ?: "", if (j.paid) "yes" else "no")
            .joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
    }
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(android.content.Intent.EXTRA_SUBJECT, "ITCABS company jobs export")
        putExtra(android.content.Intent.EXTRA_TEXT, "$header\n$rows")
    }
    context.startActivity(android.content.Intent.createChooser(send, "Export company jobs"))
}

@Composable
private fun AssignDriverDialog(drivers: List<com.itcabs.domain.model.VerifiedDriver>, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign to a driver") },
        text = {
            if (drivers.isEmpty()) Text("No verified drivers available.")
            else Column {
                drivers.forEach { d ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(d.name.ifBlank { "Driver ${d.id}" })
                        TextButton(onClick = { onPick(d.id) }) { Text("Assign") }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
