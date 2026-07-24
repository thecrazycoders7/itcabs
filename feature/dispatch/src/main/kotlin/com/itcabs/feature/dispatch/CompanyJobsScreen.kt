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
    val state by viewModel.state.collectAsState()

    if (showCreate) {
        CreateCompanyJobScreen(onDone = { showCreate = false })
        return
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Company Jobs", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = viewModel::refresh) { Text("Refresh") }
            Button(onClick = { showCreate = true }, shape = MaterialTheme.shapes.small) { Icon(Icons.Filled.Add, null); Text("New") }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }

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
                    )
                }
            }
        }
    }

    assignJob?.let { job ->
        AssignDriverDialog(state.verifiedDrivers, onDismiss = { assignJob = null }, onPick = { viewModel.assign(job.id, it); assignJob = null })
    }
}

@Composable
private fun CompanyJobCard(job: CompanyJob, onAssign: () -> Unit, onConfirm: () -> Unit, onComplete: () -> Unit, onCancel: () -> Unit) {
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
        job.claimedByName?.let { Text("Driver: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary) }

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
                    TextButton(onClick = onCancel) { Text("Cancel", color = MaterialTheme.colorScheme.error) }
                    Button(onClick = onConfirm, shape = MaterialTheme.shapes.small) { Text("Confirm") }
                }
                LegStatus.CONFIRMED -> Button(onClick = onComplete, shape = MaterialTheme.shapes.small) { Text("Complete") }
                else -> {}
            }
        }
    }
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
