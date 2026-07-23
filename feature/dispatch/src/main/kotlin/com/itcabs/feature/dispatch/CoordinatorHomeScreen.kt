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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.tooling.preview.Preview
import com.itcabs.core.designsystem.ItCabsTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableIntStateOf
import com.itcabs.domain.model.Leg
import com.itcabs.domain.model.LegStatus

/** Coordinator home: the jobs I posted and whether a driver has claimed them, plus a way to post a new one. */
@Composable
fun CoordinatorHomeScreen(viewModel: CoordinatorHomeViewModel = hiltViewModel()) {
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var ratingLegId by rememberSaveable { mutableStateOf<Long?>(null) }
    var chatLegId by rememberSaveable { mutableStateOf<Long?>(null) }

    chatLegId?.let { id ->
        ChatScreen(legId = id, onBack = { chatLegId = null })
        return
    }

    if (showCreate) {
        CreateJobScreen(onPublished = {
            showCreate = false
            viewModel.refresh()
        })
        return
    }

    val state by viewModel.state.collectAsState()
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
    )

    ratingLegId?.let { id ->
        RatingDialog(
            onDismiss = { ratingLegId = null },
            onRate = { stars ->
                viewModel.rate(id, stars)
                ratingLegId = null
            }
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
) {
    // Cash still owed to drivers: completed trips not yet settled. High-signal for the coordinator.
    val outstanding = state.legs.filter { it.status == LegStatus.COMPLETED && !it.paid }.sumOf { it.farePaise }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("My Jobs", style = MaterialTheme.typography.headlineMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRefresh) { Text("Refresh") }
                Button(onClick = onCreateJob, shape = MaterialTheme.shapes.small) {
                    Icon(Icons.Filled.Add, null)
                    Text("New Job")
                }
            }
        }

        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (outstanding > 0) {
            Text(
                "${formatRupees(outstanding)} outstanding to drivers",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        when {
            state.loading && state.legs.isEmpty() ->
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            state.legs.isEmpty() ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        "No jobs yet. Tap “New Job” to post one.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.legs, key = { it.id }) { leg ->
                    CoordinatorLegCard(
                        leg = leg,
                        onConfirm = { onConfirm(leg.id) },
                        onComplete = { onComplete(leg.id) },
                        onCancel = { onCancel(leg.id) },
                        onRate = { onRateClick(leg.id) },
                        onRepost = { onRepost(leg.jobId) },
                        onChat = { onChat(leg.id) },
                        onNoShow = { onNoShow(leg.id) },
                        onMarkPaid = { onMarkPaid(leg.id) },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CoordinatorHomePreview() {
    val sampleLegs = listOf(
        Leg(
            id = 1, jobId = 10, coordinatorId = 100, office = "DLF Cyber City", shift = "Login 09:00",
            pickup = "Gachibowli", drop = "DLF Cyber City", area = "Gachibowli",
            timeWindow = "09:00 - 10:00", vehicleType = "Sedan", farePaise = 45000,
            seats = 4, status = LegStatus.OPEN, claimedBy = null, claimedByName = null, version = 1
        ),
        Leg(
            id = 2, jobId = 11, coordinatorId = 100, office = "DLF Cyber City", shift = "Logout 18:30",
            pickup = "DLF Cyber City", drop = "Kondapur", area = "Kondapur",
            timeWindow = "18:30 - 19:30", vehicleType = "SUV", farePaise = 40000,
            seats = 6, status = LegStatus.CLAIMED, claimedBy = 200, claimedByName = "Ramesh Kumar", version = 2
        )
    )
    ItCabsTheme {
        CoordinatorHomeContent(state = CoordinatorHomeUiState(legs = sampleLegs))
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
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatRupees(leg.farePaise), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            StatusPill(leg.status)
        }
        Text("${leg.office} · ${leg.shift}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${leg.pickup} → ${leg.drop}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)

        if (leg.claimedByName != null) {
            Text(
                "Driver: ${leg.claimedByName}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.secondary
            )
            // Live trip status the driver is reporting — no need to ping "where are you?".
            when (leg.tripStage) {
                "EN_ROUTE" -> "On the way to pickup"
                "ARRIVED" -> "Arrived at pickup"
                "STARTED" -> "Trip in progress"
                else -> null
            }?.let { stage ->
                Text(stage, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            // Reliability at a glance — who you're trusting with the shift.
            leg.claimedByTrips?.let { trips ->
                val noShows = leg.claimedByNoShows ?: 0
                Text(
                    "$trips trips done" + if (noShows > 0) " · $noShows no-show${if (noShows > 1) "s" else ""}" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (noShows > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Repost the whole route as a fresh OPEN job — one-tap reuse of a daily run (M6).
            TextButton(onClick = onRepost) { Text("Repost") }
            // Chat with the claiming driver once there is one (M7).
            if (leg.claimedByName != null) TextButton(onClick = onChat) { Text("Chat") }
            when (leg.status) {
                // OPEN or CLAIMED can still be called off; a Cancel sits next to the primary action.
                LegStatus.OPEN -> {
                    TextButton(onClick = onCancel) {
                        Text("Cancel Job", color = MaterialTheme.colorScheme.error)
                    }
                }
                LegStatus.CLAIMED -> {
                    TextButton(onClick = onNoShow) {
                        Text("No-show", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onCancel) {
                        Text("Cancel", color = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = onConfirm, shape = MaterialTheme.shapes.small) {
                        Text("Confirm Driver")
                    }
                }
                LegStatus.CONFIRMED -> {
                    TextButton(onClick = onNoShow) {
                        Text("No-show", color = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = onComplete, shape = MaterialTheme.shapes.small) {
                        Text("Mark Completed")
                    }
                }
                LegStatus.COMPLETED -> {
                    if (leg.paid) {
                        Text("Paid ✓", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    } else {
                        TextButton(onClick = onMarkPaid) { Text("Mark Paid") }
                    }
                    TextButton(onClick = onRate) {
                        Text("Rate Driver")
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun RatingDialog(onDismiss: () -> Unit, onRate: (Int) -> Unit) {
    var stars by remember { mutableIntStateOf(5) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rate Driver") },
        text = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                (1..5).forEach { i ->
                    IconButton(onClick = { stars = i }) {
                        Icon(
                            if (i <= stars) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "$i stars",
                            tint = if (i <= stars) Color(0xFFFFB400) else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onRate(stars) }) { Text("Submit") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun StatusPill(status: LegStatus) {
    // OPEN reads as "waiting"; anything past OPEN reads as "taken" so the flip is obvious.
    val (bg, fg) = when (status) {
        LegStatus.OPEN -> MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.onSurfaceVariant
        LegStatus.CANCELLED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
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
