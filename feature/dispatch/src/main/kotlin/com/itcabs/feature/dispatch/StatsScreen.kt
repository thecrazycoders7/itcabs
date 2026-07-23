package com.itcabs.feature.dispatch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itcabs.domain.model.CoordinatorStats

/** Insights tab: a coordinator's own performance at a glance. */
@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Insights", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = viewModel::refresh) { Text("Refresh") }
        }

        // Time-window selector — recomputes the stats for the last 7/30 days or all time.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatsRange.entries.forEach { r ->
                FilterChip(
                    selected = state.range == r,
                    onClick = { viewModel.setRange(r) },
                    label = { Text(r.label) },
                )
            }
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
        }

        val stats = state.stats
        when {
            state.loading && stats == null ->
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            stats == null ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("No data yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            else -> StatsBody(stats)
        }
    }
}

@Composable
private fun StatsBody(s: CoordinatorStats) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Trips posted", s.posted.toString(), Modifier.weight(1f))
            StatTile("Fill rate", "${s.fillRatePct}%", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Completed", s.completed.toString(), Modifier.weight(1f))
            StatTile("Cancelled", s.cancelled.toString(), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Paid out", formatRupees(s.totalPaidPaise), Modifier.weight(1f))
            StatTile(
                "Outstanding", formatRupees(s.outstandingPaise), Modifier.weight(1f),
                valueColor = if (s.outstandingPaise > 0) MaterialTheme.colorScheme.error else null,
            )
        }

        // Outcome breakdown — a minimal stacked bar so the completed/cancelled/other split is visible at a glance.
        val other = (s.posted - s.completed - s.cancelled).coerceAtLeast(0)
        if (s.posted > 0) {
            Text("Outcome breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Row(
                Modifier.fillMaxWidth().height(20.dp).clip(MaterialTheme.shapes.small),
            ) {
                if (s.completed > 0) Box(Modifier.fillMaxHeight().weight(s.completed.toFloat()).background(MaterialTheme.colorScheme.primary))
                if (s.cancelled > 0) Box(Modifier.fillMaxHeight().weight(s.cancelled.toFloat()).background(MaterialTheme.colorScheme.error))
                if (other > 0) Box(Modifier.fillMaxHeight().weight(other.toFloat()).background(MaterialTheme.colorScheme.surfaceVariant))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend("Completed ${s.completed}", MaterialTheme.colorScheme.primary)
                Legend("Cancelled ${s.cancelled}", MaterialTheme.colorScheme.error)
                Legend("Other $other", MaterialTheme.colorScheme.surfaceVariant)
            }
        }

        if (s.topDrivers.isNotEmpty()) {
            Text("Top drivers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            s.topDrivers.forEach { d ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(d.name.ifBlank { "(no name)" }, style = MaterialTheme.typography.bodyLarge)
                    Text("${d.trips} trips", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Legend(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: androidx.compose.ui.graphics.Color? = null) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = valueColor ?: MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
