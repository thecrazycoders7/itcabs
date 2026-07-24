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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itcabs.domain.model.PendingDriver

/** Admin tab: review drivers awaiting KYC and approve them so they can start claiming trips. */
@Composable
fun AdminScreen(viewModel: AdminViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Open a freshly signed document URL in the device viewer/browser, then consume it.
    androidx.compose.runtime.LaunchedEffect(state.openUrl) {
        state.openUrl?.let { url ->
            runCatching {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            viewModel.consumeOpenUrl()
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Pending Approvals", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = viewModel::refresh) { Text("Refresh") }
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
        }

        if (state.loading && state.pending.isEmpty() && state.drivers.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        } else LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Pending approvals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            if (state.pending.isEmpty()) item { Text("None waiting.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(state.pending, key = { "p-${it.id}" }) { d ->
                PendingDriverCard(
                    driver = d,
                    docs = state.docs[d.id],
                    onLoadDocs = { viewModel.loadDocs(d.id) },
                    onView = { path -> viewModel.openDoc(path) },
                    onReupload = { docType, reason -> viewModel.requestReupload(d.id, docType, reason) },
                    onVerify = { viewModel.verify(d.id) },
                    onReject = { reason -> viewModel.reject(d.id, reason) },
                )
            }
            // Roster: block/unblock any driver (trust & safety).
            item { Text("All drivers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp)) }
            items(state.drivers, key = { "d-${it.id}" }) { d ->
                DriverRosterRow(d, onBlock = { viewModel.block(d.id) }, onUnblock = { viewModel.unblock(d.id) })
            }
        }
    }
}

@Composable
private fun DriverRosterRow(driver: com.itcabs.domain.model.AdminDriver, onBlock: () -> Unit, onUnblock: () -> Unit) {
    val blocked = driver.status == "BLOCKED"
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(driver.name.ifBlank { "Driver ${driver.id}" }, fontWeight = FontWeight.Medium,
                color = if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text("${driver.kycStatus.name} · ${driver.tripsCompleted} trips" + if (driver.noShows > 0) " · ${driver.noShows} no-shows" else "" + if (blocked) " · BLOCKED" else "",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (blocked) TextButton(onClick = onUnblock) { Text("Unblock") }
        else TextButton(onClick = onBlock) { Text("Block", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun PendingDriverCard(
    driver: PendingDriver,
    docs: List<com.itcabs.domain.model.KycDoc>?,
    onLoadDocs: () -> Unit,
    onView: (String) -> Unit,
    onReupload: (String, String?) -> Unit,
    onVerify: () -> Unit,
    onReject: (String?) -> Unit,
) {
    var confirmReject by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    var showDocs by remember { mutableStateOf(false) }
    if (confirmReject) {
        AlertDialog(
            onDismissRequest = { confirmReject = false },
            title = { Text("Reject ${driver.name.ifBlank { "this driver" }}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("They'll be notified and can resubmit from the app. Add a reason so they know what to fix:")
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        placeholder = { Text("e.g. RC photo unclear") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { confirmReject = false; onReject(reason.trim().ifBlank { null }) }) { Text("Reject", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmReject = false }) { Text("Cancel") } },
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(driver.name.ifBlank { "(no name)" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        driver.email?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        val vehicle = listOfNotNull(driver.vehicleType, driver.vehicleReg).joinToString(" · ")
        if (vehicle.isNotBlank()) Text(vehicle, style = MaterialTheme.typography.bodyMedium)
        driver.aadhaarMasked?.let { Text("Aadhaar: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        driver.rcNumberMasked?.let { Text("RC: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        TextButton(
            onClick = { showDocs = !showDocs; if (showDocs && docs == null) onLoadDocs() },
            contentPadding = PaddingValues(0.dp),
        ) { Text(if (showDocs) "Hide documents" else "Review documents") }
        if (showDocs) {
            when {
                docs == null -> Text("Loading…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                docs.isEmpty() -> Text("No documents uploaded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    docs.forEach { doc -> DocReviewRow(doc, onView = { onView(doc.storagePath) }, onReupload = { r -> onReupload(doc.docType, r) }) }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { confirmReject = true }) { Text("Reject", color = MaterialTheme.colorScheme.error) }
            Button(onClick = onVerify, shape = MaterialTheme.shapes.small) { Text("Verify") }
        }
    }
}

/** One document row in the admin review: label + state + View + "Ask re-upload" (with a reason). */
@Composable
private fun DocReviewRow(doc: com.itcabs.domain.model.KycDoc, onView: () -> Unit, onReupload: (String?) -> Unit) {
    var askReupload by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    val label = KYC_DOC_DEFS.firstOrNull { it.type == doc.docType }?.label ?: doc.docType
    val requested = doc.status == "REUPLOAD_REQUESTED"

    if (askReupload) {
        AlertDialog(
            onDismissRequest = { askReupload = false },
            title = { Text("Re-upload: $label") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The driver is notified and can re-upload this one document while the rest stays in review.")
                    OutlinedTextField(reason, { reason = it }, placeholder = { Text("e.g. blurry, corner cut off") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { askReupload = false; onReupload(reason.trim().ifBlank { null }) }) { Text("Request") } },
            dismissButton = { TextButton(onClick = { askReupload = false }) { Text("Cancel") } },
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (requested) Text("Re-upload requested" + (doc.rejectReason?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        TextButton(onClick = onView, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("View") }
        TextButton(onClick = { askReupload = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(if (requested) "Re-ask" else "Re-upload")
        }
    }
}
