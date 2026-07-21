package com.itcabs.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itcabs.Job
import com.itcabs.Leg
import com.itcabs.UserProfile

/** Draft leg held in UI state before posting. */
private data class LegDraft(
    var pickup: String = "", var drop: String = "", var area: String = "",
    var timeWindow: String = "", var vehicleType: String = "", var fare: String = "", var seats: String = "1",
) {
    fun valid() = pickup.isNotBlank() && drop.isNotBlank() && fare.toLongOrNull() != null
    fun toLeg() = Leg(
        pickup = pickup, drop = drop, area = area, timeWindow = timeWindow,
        vehicleType = vehicleType, fare = fare.toLongOrNull() ?: 0, seats = seats.toIntOrNull() ?: 1,
    )
}

@Composable
fun PostJobScreen(vm: AppViewModel, profile: UserProfile, onPosted: () -> Unit) {
    var office by remember { mutableStateOf("") }
    var shift by remember { mutableStateOf("") }
    val legs = remember { mutableStateListOf(LegDraft()) }
    var posting by remember { mutableStateOf(false) }

    val canPost = office.isNotBlank() && shift.isNotBlank() && legs.all { it.valid() } && legs.isNotEmpty()

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("New requirement", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(office, { office = it }, label = { Text("Office / drop hub") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(shift, { shift = it }, label = { Text("Shift (e.g. Login 09:00)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        legs.forEachIndexed { i, draft ->
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Leg ${i + 1}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (legs.size > 1) IconButton({ legs.removeAt(i) }) { Icon(Icons.Filled.Delete, "Remove leg") }
            }
            LegFields(draft) { legs[i] = it }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton({ legs.add(LegDraft()) }, Modifier.fillMaxWidth()) { Text("+ Add pickup leg") }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                posting = true
                vm.postJob(
                    Job(coordinatorName = profile.name, office = office, shift = shift),
                    legs.map { it.toLeg() },
                ) { posting = false; onPosted() }
            },
            enabled = canPost && !posting, modifier = Modifier.fillMaxWidth(),
        ) { Text(if (posting) "Posting…" else "Post ${legs.size} leg(s)") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LegFields(draft: LegDraft, onChange: (LegDraft) -> Unit) {
    // Local mirror so each keystroke updates the parent list entry.
    Column {
        two(draft.pickup, "Pickup", draft.drop, "Drop",
            { onChange(draft.copy(pickup = it)) }, { onChange(draft.copy(drop = it)) })
        two(draft.area, "Area tag", draft.timeWindow, "Time window",
            { onChange(draft.copy(area = it)) }, { onChange(draft.copy(timeWindow = it)) })
        two(draft.vehicleType, "Vehicle type", draft.seats, "Seats",
            { onChange(draft.copy(vehicleType = it)) }, { onChange(draft.copy(seats = it)) })
        OutlinedTextField(draft.fare, { onChange(draft.copy(fare = it)) },
            label = { Text("Fare ₹") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun two(a: String, la: String, b: String, lb: String, oa: (String) -> Unit, ob: (String) -> Unit) {
    Row {
        OutlinedTextField(a, oa, label = { Text(la) }, singleLine = true, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(b, ob, label = { Text(lb) }, singleLine = true, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
}
