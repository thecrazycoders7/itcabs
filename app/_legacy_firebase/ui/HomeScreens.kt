package com.itcabs.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itcabs.*

@Composable
fun HomeScreen(vm: AppViewModel, profile: UserProfile) {
    if (profile.blocked) { Blocked(); return }
    if (profile.role == Role.DRIVER.name) DriverHome(vm, profile) else CoordinatorHome(vm, profile)
}

@Composable
private fun Blocked() = Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
    Text("Your account has been blocked for policy violations.", color = MaterialTheme.colorScheme.error)
}

// ---------- Driver ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriverHome(vm: AppViewModel, profile: UserProfile) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("itcabs · driver") }, actions = { SignOut(vm) }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Search, null) }, label = { Text("Browse") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.DirectionsCar, null) }, label = { Text("My trips") })
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            if (!profile.verified) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("Verification pending", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("You can claim rides once a supervisor verifies your documents. " +
                        "Until then jobs are hidden.", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (tab == 0) DriverBrowse(vm, profile) else DriverTrips(vm)
        }
    }
}

@Composable
private fun DriverBrowse(vm: AppViewModel, profile: UserProfile) {
    var area by remember { mutableStateOf("") }
    var vehicle by remember { mutableStateOf("") }
    val legs by vm.openLegs(area.ifBlank { null }, vehicle.ifBlank { null })
        .collectAsState(initial = emptyList())
    var msg by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row {
            OutlinedTextField(area, { area = it }, label = { Text("Area") }, singleLine = true, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(vehicle, { vehicle = it }, label = { Text("Vehicle") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        msg?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        if (legs.isEmpty()) EmptyHint("No open legs match. Adjust filters.")
        LazyColumn(Modifier.padding(top = 8.dp)) {
            items(legs, key = { it.id }) { leg ->
                LegCard(leg) {
                    Button(onClick = {
                        vm.claim(leg, profile.name) { err -> msg = err ?: "Claimed — check My trips" }
                    }) { Text("Claim ₹${leg.fare}") }
                }
            }
        }
    }
}

@Composable
private fun DriverTrips(vm: AppViewModel) {
    val legs by vm.myClaims().collectAsState(initial = emptyList())
    if (legs.isEmpty()) EmptyHint("You haven't claimed any legs yet.")
    LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
        items(legs, key = { it.id }) { leg -> LegCard(leg) { StatusChip(leg.status) } }
    }
}

// ---------- Coordinator ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoordinatorHome(vm: AppViewModel, profile: UserProfile) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("itcabs · coordinator") }, actions = { SignOut(vm) }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Dashboard, null) }, label = { Text("Dashboard") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.Add, null) }, label = { Text("Post job") })
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            if (tab == 0) CoordinatorDashboard(vm) else PostJobScreen(vm, profile) { tab = 0 }
        }
    }
}

@Composable
private fun CoordinatorDashboard(vm: AppViewModel) {
    val legs by vm.myLegs().collectAsState(initial = emptyList())
    if (legs.isEmpty()) EmptyHint("No requirements posted yet. Tap Post job.")
    LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
        items(legs, key = { it.id }) { leg ->
            LegCard(leg) {
                Column(horizontalAlignment = Alignment.End) {
                    StatusChip(leg.status)
                    when (leg.status) {
                        LegStatus.CLAIMED.name -> {
                            Text("by ${leg.claimedByName}", style = MaterialTheme.typography.labelSmall)
                            TextButton({ vm.advance(leg, LegStatus.CONFIRMED) }) { Text("Confirm") }
                        }
                        LegStatus.CONFIRMED.name ->
                            TextButton({ vm.advance(leg, LegStatus.COMPLETED) }) { Text("Mark completed") }
                        LegStatus.COMPLETED.name ->
                            if (!leg.rated) TextButton({ vm.rate(leg, 5) }) { Text("Rate ★5") }
                            else Text("Rated", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

// ---------- shared ----------

@Composable
private fun SignOut(vm: AppViewModel) =
    IconButton({ vm.signOut() }) { Icon(Icons.Filled.Logout, "Sign out") }

@Composable
private fun EmptyHint(text: String) =
    Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }

@Composable
fun LegCard(leg: Leg, trailing: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${leg.pickup} → ${leg.drop}", fontWeight = FontWeight.SemiBold)
                Text("${leg.office} · ${leg.shift}", style = MaterialTheme.typography.bodySmall)
                Text("${leg.vehicleType} · ${leg.seats} seat(s) · ${leg.timeWindow}",
                    style = MaterialTheme.typography.bodySmall)
                Text("₹${leg.fare}", style = MaterialTheme.typography.titleMedium)
            }
            trailing()
        }
    }
}

@Composable
fun StatusChip(status: String) {
    val color = when (status) {
        LegStatus.OPEN.name -> MaterialTheme.colorScheme.primary
        LegStatus.CLAIMED.name -> MaterialTheme.colorScheme.tertiary
        LegStatus.CONFIRMED.name -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }
    AssistChip(onClick = {}, label = { Text(status) },
        colors = AssistChipDefaults.assistChipColors(labelColor = color))
}
