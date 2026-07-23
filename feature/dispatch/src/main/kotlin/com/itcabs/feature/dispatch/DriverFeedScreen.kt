package com.itcabs.feature.dispatch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.itcabs.domain.model.KycStatus
import com.itcabs.domain.model.Leg
import com.itcabs.domain.model.LegStatus
import androidx.compose.ui.tooling.preview.Preview
import com.itcabs.core.designsystem.ItCabsTheme

/** Money is stored in paise; render as ₹ with no decimals for whole rupees. */
internal fun formatRupees(paise: Long): String = "₹${paise / 100}"

@Composable
fun DriverFeedScreen(viewModel: DriverFeedViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var showKyc by rememberSaveable { mutableStateOf(false) }

    if (showKyc) {
        DriverKycScreen(onDone = { showKyc = false; viewModel.refresh() })
        return
    }

    DriverFeedContent(
        state = state,
        onAreaChange = viewModel::onAreaChange,
        onDismissNotice = viewModel::dismissNotice,
        onClaim = viewModel::claim,
        onCompleteKyc = { showKyc = true },
    )
}

@Composable
fun DriverFeedContent(
    state: DriverFeedUiState,
    onAreaChange: (String) -> Unit = {},
    onDismissNotice: () -> Unit = {},
    onClaim: (Long) -> Unit = {},
    onCompleteKyc: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "IT Cars Mobility",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        OutlinedTextField(
            value = state.area,
            onValueChange = onAreaChange,
            placeholder = { Text("Search jobs by area…") },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("Nearby Jobs", style = MaterialTheme.typography.headlineMedium)
            Text(
                "${state.legs.size} AVAILABLE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.kycStatus?.takeIf { it != KycStatus.VERIFIED }?.let { status ->
            VerificationBanner(status)
            // NONE = never submitted, REJECTED = redo; PENDING just waits for admin verify.
            if (status == KycStatus.NONE || status == KycStatus.REJECTED) {
                Button(
                    onClick = onCompleteKyc,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("Complete KYC") }
            }
        }
        state.notice?.let { NoticeBar(it, onDismiss = onDismissNotice) }
        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (state.loading && state.legs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.legs, key = { it.id }) { leg ->
                    LegCard(
                        leg = leg,
                        claiming = state.claimingId == leg.id,
                        canClaim = state.canClaim,
                        onClaim = { onClaim(leg.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeBar(text: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(
            "Dismiss",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onDismiss).padding(start = 12.dp),
        )
    }
}

/** Amber banner telling an unverified driver why they can't claim yet. */
@Composable
private fun VerificationBanner(status: KycStatus) {
    val text = when (status) {
        KycStatus.NONE -> "Complete your KYC to start claiming trips."
        KycStatus.PENDING -> "Verification pending — you can't claim trips yet."
        KycStatus.REJECTED -> "Verification rejected — please re-submit your KYC."
        KycStatus.VERIFIED -> return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.small)
            .background(Color(0xFFFDECC8))
            .padding(12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF7A5200), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LegCard(leg: Leg, claiming: Boolean, canClaim: Boolean, onClaim: () -> Unit) {
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
                formatRupees(leg.farePaise),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            StatusChip(leg.area.ifBlank { leg.vehicleType.ifBlank { "OPEN" } })
        }

        // Pickup → drop timeline
        RoutePoint(label = "PICKUP", place = leg.pickup, dotColor = MaterialTheme.colorScheme.primary)
        RoutePoint(label = "DROP", place = leg.drop, dotColor = MaterialTheme.colorScheme.outline)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (leg.timeWindow.isNotBlank()) {
                    Icon(Icons.Filled.Schedule, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(leg.timeWindow, style = MaterialTheme.typography.bodyMedium)
                }
                if (leg.vehicleType.isNotBlank()) {
                    Icon(Icons.Filled.DirectionsCar, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(leg.vehicleType, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Button(
                onClick = onClaim,
                enabled = !claiming && canClaim,
                shape = MaterialTheme.shapes.small,
            ) {
                if (claiming) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Claim Trip", style = MaterialTheme.typography.titleLarge)
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
private fun StatusChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true)
@Composable
fun DriverFeedPreview() {
    val sampleLegs = listOf(
        Leg(
            id = 1, jobId = 10, coordinatorId = 100, office = "DLF Cyber City", shift = "Login",
            pickup = "Gachibowli", drop = "DLF Cyber City", area = "Gachibowli",
            timeWindow = "09:00 - 10:00", vehicleType = "Sedan", farePaise = 45000,
            seats = 4, status = LegStatus.OPEN, claimedBy = null, claimedByName = null, version = 1
        ),
        Leg(
            id = 2, jobId = 11, coordinatorId = 101, office = "Google", shift = "Logout",
            pickup = "Kondapur", drop = "Google", area = "Kondapur",
            timeWindow = "18:00 - 19:00", vehicleType = "SUV", farePaise = 35000,
            seats = 6, status = LegStatus.OPEN, claimedBy = null, claimedByName = null, version = 1
        )
    )
    ItCabsTheme {
        DriverFeedContent(state = DriverFeedUiState(legs = sampleLegs))
    }
}
