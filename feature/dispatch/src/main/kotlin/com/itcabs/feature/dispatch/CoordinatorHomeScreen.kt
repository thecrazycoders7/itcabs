package com.itcabs.feature.dispatch

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itcabs.core.designsystem.ItCabsTheme
import com.itcabs.domain.model.Leg
import com.itcabs.domain.model.LegStatus
import com.itcabs.domain.model.NewLeg

/** Coordinator home: the jobs I posted, their live status, and full management (edit/assign/settle). */
@Composable
fun CoordinatorHomeScreen(viewModel: CoordinatorHomeViewModel = hiltViewModel()) {
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var ratingLegId by rememberSaveable { mutableStateOf<Long?>(null) }
    var chatLegId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editLeg by remember { mutableStateOf<Leg?>(null) }
    var assignLeg by remember { mutableStateOf<Leg?>(null) }
    var showTemplates by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    chatLegId?.let { id ->
        ChatScreen(legId = id, onBack = { chatLegId = null })
        return
    }
    if (showCreate) {
        CreateJobScreen(onPublished = { showCreate = false; viewModel.refresh() })
        return
    }

    CoordinatorHomeContent(
        state = state,
        onRefresh = viewModel::refresh,
        onCreateJob = { showCreate = true },
        onConfirm = { viewModel.setStatus(it, LegStatus.CONFIRMED) },
        onComplete = { viewModel.setStatus(it, LegStatus.COMPLETED) },
        onCancel = { viewModel.setStatus(it, LegStatus.CANCELLED) },
        onRateClick = { ratingLegId = it },
        onRepost = viewModel::repost,
        onChat = { chatLegId = it },
        onNoShow = viewModel::markNoShow,
        onMarkPaid = viewModel::markPaid,
        onFilter = viewModel::onFilter,
        onQuery = viewModel::onQuery,
        onEdit = { editLeg = it },
        onAssign = { assignLeg = it; viewModel.loadDrivers() },
        onExport = { shareCsv(context, state.legs) },
        onTemplates = { showTemplates = true; viewModel.loadTemplates() },
    )

    ratingLegId?.let { id ->
        RatingDialog(onDismiss = { ratingLegId = null }, onRate = { stars -> viewModel.rate(id, stars); ratingLegId = null })
    }
    editLeg?.let { leg ->
        EditLegDialog(leg, onDismiss = { editLeg = null }, onSave = { edit -> viewModel.editLeg(leg.id, edit); editLeg = null })
    }
    assignLeg?.let { leg ->
        AssignDialog(state.verifiedDrivers, onDismiss = { assignLeg = null }, onPick = { driverId -> viewModel.assign(leg.id, driverId); assignLeg = null })
    }
    if (showTemplates) {
        TemplatesDialog(
            templates = state.templates,
            onDismiss = { showTemplates = false },
            onPost = { viewModel.postTemplate(it); showTemplates = false },
            onDelete = viewModel::deleteTemplate,
        )
    }
}

@Composable
fun CoordinatorHomeContent(
    state: CoordinatorHomeUiState,
    onRefresh: () -> Unit = {},
    onCreateJob: () -> Unit = {},
    onConfirm: (Long) -> Unit = {},
    onComplete: (Long) -> Unit = {},
    onCancel: (Long) -> Unit = {},
    onRateClick: (Long) -> Unit = {},
    onRepost: (Long) -> Unit = {},
    onChat: (Long) -> Unit = {},
    onNoShow: (Long) -> Unit = {},
    onMarkPaid: (Long) -> Unit = {},
    onFilter: (LegStatus?) -> Unit = {},
    onQuery: (String) -> Unit = {},
    onEdit: (Leg) -> Unit = {},
    onAssign: (Leg) -> Unit = {},
    onExport: () -> Unit = {},
    onTemplates: () -> Unit = {},
) {
    val outstanding = state.legs.filter { it.status == LegStatus.COMPLETED && !it.paid }.sumOf { it.farePaise }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("My Jobs", style = MaterialTheme.typography.headlineMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTemplates) { Icon(Icons.Filled.Star, contentDescription = "Templates") }
                IconButton(onClick = onExport) { Icon(Icons.Filled.Share, contentDescription = "Export CSV") }
                Button(onClick = onCreateJob, shape = MaterialTheme.shapes.small) {
                    Icon(Icons.Filled.Add, null); Text("New")
                }
            }
        }

        // Search + status filter (feature: find one trip among many).
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            placeholder = { Text("Search office / route / passenger…") },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = state.statusFilter == null, onClick = { onFilter(null) }, label = { Text("All") })
            LegStatus.entries.forEach { s ->
                FilterChip(selected = state.statusFilter == s, onClick = { onFilter(s) }, label = { Text(s.name.lowercase().replaceFirstChar { it.uppercase() }) })
            }
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        if (outstanding > 0) {
            Text(
                "${formatRupees(outstanding)} outstanding to drivers",
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        val visible = state.visibleLegs
        when {
            state.loading && state.legs.isEmpty() ->
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            visible.isEmpty() ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        if (state.legs.isEmpty()) "No jobs yet. Tap “New” to post one." else "No trips match this filter.",
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(visible, key = { it.id }) { leg ->
                    CoordinatorLegCard(
                        leg = leg,
                        onConfirm = { onConfirm(leg.id) }, onComplete = { onComplete(leg.id) },
                        onCancel = { onCancel(leg.id) }, onRate = { onRateClick(leg.id) },
                        onRepost = { onRepost(leg.jobId) }, onChat = { onChat(leg.id) },
                        onNoShow = { onNoShow(leg.id) }, onMarkPaid = { onMarkPaid(leg.id) },
                        onEdit = { onEdit(leg) }, onAssign = { onAssign(leg) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CoordinatorLegCard(
    leg: Leg,
    onConfirm: () -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    onRate: () -> Unit,
    onRepost: () -> Unit,
    onChat: () -> Unit,
    onNoShow: () -> Unit,
    onMarkPaid: () -> Unit,
    onEdit: () -> Unit,
    onAssign: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(formatRupees(leg.farePaise), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            StatusPill(leg.status)
        }
        Text("${leg.office} · ${leg.shift}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${leg.pickup} → ${leg.drop}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        if (leg.passengerName.isNotBlank()) {
            Text("Passenger: ${leg.passengerName}" + if (leg.passengerPhone.isNotBlank()) " · ${leg.passengerPhone}" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (leg.claimedByName != null) {
            Text("Driver: ${leg.claimedByName}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.secondary)
            when (leg.tripStage) {
                "EN_ROUTE" -> "On the way to pickup"
                "ARRIVED" -> "Arrived at pickup"
                "STARTED" -> "Trip in progress"
                else -> null
            }?.let { Text(it, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) }
            leg.claimedByTrips?.let { trips ->
                val noShows = leg.claimedByNoShows ?: 0
                Text("$trips trips done" + if (noShows > 0) " · $noShows no-show${if (noShows > 1) "s" else ""}" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (noShows > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Pickup code to relay to the passenger — the driver must key it in to start (proof of pickup).
            leg.pickupOtp?.takeIf { leg.tripStage != "STARTED" }?.let {
                Text("Pickup code: $it", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }

        Row(Modifier.fillMaxWidth(), Arrangement.End, Alignment.CenterVertically) {
            TextButton(onClick = onRepost) { Text("Repost") }
            if (leg.claimedByName != null) TextButton(onClick = onChat) { Text("Chat") }
            when (leg.status) {
                LegStatus.OPEN -> {
                    TextButton(onClick = onEdit) { Text("Edit") }
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
                    if (leg.paid) Text("Paid ✓", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    else TextButton(onClick = onMarkPaid) { Text("Mark Paid") }
                    TextButton(onClick = onRate) { Text("Rate") }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun EditLegDialog(leg: Leg, onDismiss: () -> Unit, onSave: (NewLeg) -> Unit) {
    var pickup by remember { mutableStateOf(leg.pickup) }
    var drop by remember { mutableStateOf(leg.drop) }
    var fare by remember { mutableStateOf((leg.farePaise / 100).toString()) }
    var time by remember { mutableStateOf(leg.timeWindow) }
    var passenger by remember { mutableStateOf(leg.passengerName) }
    var phone by remember { mutableStateOf(leg.passengerPhone) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit trip") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(pickup, { pickup = it }, label = { Text("Pickup") }, singleLine = true)
                OutlinedTextField(drop, { drop = it }, label = { Text("Drop") }, singleLine = true)
                OutlinedTextField(fare, { fare = it.filter(Char::isDigit) }, label = { Text("Fare (₹)") }, singleLine = true)
                OutlinedTextField(time, { time = it }, label = { Text("Time window") }, singleLine = true)
                OutlinedTextField(passenger, { passenger = it }, label = { Text("Passenger") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Passenger phone") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(NewLeg(
                    pickup = pickup, drop = drop, area = leg.area, timeWindow = time,
                    vehicleType = leg.vehicleType, farePaise = (fare.toLongOrNull() ?: 0) * 100,
                    seats = leg.seats, passengerName = passenger, passengerPhone = phone,
                ))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AssignDialog(drivers: List<com.itcabs.domain.model.VerifiedDriver>, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign to a driver") },
        text = {
            if (drivers.isEmpty()) Text("No verified drivers available.")
            else Column {
                drivers.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).padding(vertical = 4.dp),
                        Arrangement.SpaceBetween, Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(d.name.ifBlank { "Driver ${d.id}" }, fontWeight = FontWeight.Medium)
                            Text("${d.tripsCompleted} trips" + if (d.noShows > 0) " · ${d.noShows} no-shows" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onPick(d.id) }) { Text("Assign") }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun TemplatesDialog(
    templates: List<com.itcabs.domain.model.JobTemplate>,
    onDismiss: () -> Unit,
    onPost: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saved routes") },
        text = {
            if (templates.isEmpty()) Text("No templates yet. Save one while creating a job.")
            else Column {
                templates.forEach { t ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name + if (t.recurring) " · daily" else "", fontWeight = FontWeight.Medium)
                            Text("${t.office} · ${t.legs.size} leg${if (t.legs.size != 1) "s" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onDelete(t.id) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                        TextButton(onClick = { onPost(t.id) }) { Text("Post") }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun RatingDialog(onDismiss: () -> Unit, onRate: (Int) -> Unit) {
    var stars by remember { mutableIntStateOf(5) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rate Driver") },
        text = {
            Row(Modifier.fillMaxWidth(), Arrangement.Center) {
                (1..5).forEach { i ->
                    IconButton(onClick = { stars = i }) {
                        Icon(if (i <= stars) Icons.Filled.Star else Icons.Filled.StarBorder, "$i stars",
                            tint = if (i <= stars) Color(0xFFFFB400) else MaterialTheme.colorScheme.outline)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onRate(stars) }) { Text("Submit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StatusPill(status: LegStatus) {
    val (bg, fg) = when (status) {
        LegStatus.OPEN -> MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.onSurfaceVariant
        LegStatus.CANCELLED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(status.name, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

/** Build a CSV of the coordinator's trips and hand it to the Android share sheet (no backend needed). */
private fun shareCsv(context: android.content.Context, legs: List<Leg>) {
    val header = "office,shift,pickup,drop,area,fare_rupees,status,driver,paid,passenger"
    val rows = legs.joinToString("\n") { l ->
        listOf(l.office, l.shift, l.pickup, l.drop, l.area, (l.farePaise / 100).toString(),
            l.status.name, l.claimedByName ?: "", if (l.paid) "yes" else "no", l.passengerName)
            .joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
    }
    val csv = "$header\n$rows"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, "ITCABS trips export")
        putExtra(Intent.EXTRA_TEXT, csv)
    }
    context.startActivity(Intent.createChooser(send, "Export trips"))
}

@Preview(showBackground = true)
@Composable
fun CoordinatorHomePreview() {
    val sampleLegs = listOf(
        Leg(1, 10, 100, "DLF Cyber City", "Login 09:00", "Gachibowli", "DLF Cyber City", "Gachibowli",
            "09:00 - 10:00", "Sedan", 45000, 4, LegStatus.OPEN, null, null, version = 1),
    )
    ItCabsTheme { CoordinatorHomeContent(state = CoordinatorHomeUiState(legs = sampleLegs)) }
}
