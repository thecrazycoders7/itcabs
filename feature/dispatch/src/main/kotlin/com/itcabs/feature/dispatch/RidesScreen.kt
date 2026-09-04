package com.itcabs.feature.dispatch

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.itcabs.domain.model.NewRide
import com.itcabs.domain.model.Ride
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Calendar

/** Carpooling home: find & book rides, offer a ride, manage my rides + bookings. */
@Composable
fun RidesScreen(viewModel: RidesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Find", "Offer", "My rides", "Bookings")

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        state.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp)) }

        when (tab) {
            0 -> FindTab(state, onBook = { id -> viewModel.book(id, 1) }, onRefresh = viewModel::refresh)
            1 -> OfferTab(state, onPost = { r -> viewModel.create(r) { tab = 2 } })
            2 -> MyRidesTab(state, onStart = { viewModel.setStatus(it, "STARTED") }, onComplete = { viewModel.setStatus(it, "COMPLETED") })
            else -> BookingsTab(state, onCancel = viewModel::cancelBooking)
        }
    }
}

@Composable
private fun FindTab(state: RidesUiState, onBook: (Long) -> Unit, onRefresh: () -> Unit) {
    if (state.loading && state.results.isEmpty()) { Loading(); return }
    if (state.results.isEmpty()) { Empty("No rides available right now.", onRefresh); return }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(state.results, key = { it.id }) { ride ->
            RideCard(ride) {
                when {
                    ride.myBookingStatus == "CONFIRMED" -> Text("Booked ✓  code ${ride.myOtp ?: ""}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    ride.seatsLeft <= 0 -> Text("Full", color = MaterialTheme.colorScheme.error)
                    else -> Button(onClick = { onBook(ride.id) }) { Text("Book a seat") }
                }
            }
        }
    }
}

@Composable
private fun OfferTab(state: RidesUiState, onPost: (NewRide) -> Unit) {
    val context = LocalContext.current
    var origin by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var departAt by remember { mutableStateOf<OffsetDateTime?>(null) }
    var seats by remember { mutableIntStateOf(3) }
    var price by remember { mutableStateOf("") }
    var car by remember { mutableStateOf("") }
    var womenOnly by remember { mutableStateOf(false) }

    val canPost = origin.isNotBlank() && destination.isNotBlank() && departAt != null && price.toIntOrNull() != null && !state.posting

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OutlinedTextField(origin, { origin = it }, label = { Text("From") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(destination, { destination = it }, label = { Text("To") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item {
            OutlinedButton(onClick = { pickDateTime(context) { departAt = it } }, modifier = Modifier.fillMaxWidth()) {
                Text(departAt?.let { "Departs: " + it.toString().take(16).replace('T', ' ') } ?: "Pick date & time")
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Seats", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { if (seats > 1) seats-- }) { Text("–") }
                Text("$seats", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { if (seats < 6) seats++ }) { Text("+") }
            }
        }
        item { OutlinedTextField(price, { price = it.filter(Char::isDigit) }, label = { Text("Price per seat (₹)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
        item { OutlinedTextField(car, { car = it }, label = { Text("Car (e.g. Swift, white)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        if (state.myGender == "FEMALE") item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = womenOnly, onCheckedChange = { womenOnly = it })
                Text("Women-only ride", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Button(
                onClick = {
                    onPost(
                        NewRide(
                            origin = origin, originLat = null, originLng = null,
                            destination = destination, destLat = null, destLng = null,
                            departAt = departAt!!.toString(), totalSeats = seats,
                            pricePaise = price.toLong() * 100, carModel = car, womenOnly = womenOnly, notes = null,
                        ),
                    )
                },
                enabled = canPost, modifier = Modifier.fillMaxWidth(),
            ) { if (state.posting) CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp) else Text("Offer ride") }
        }
    }
}

@Composable
private fun MyRidesTab(state: RidesUiState, onStart: (Long) -> Unit, onComplete: (Long) -> Unit) {
    if (state.myRides.isEmpty()) { Empty("You haven't offered any rides yet.", null); return }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(state.myRides, key = { it.id }) { ride ->
            RideCard(ride, showHost = false) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${ride.totalSeats - ride.seatsLeft}/${ride.totalSeats} booked · ${ride.status}",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (ride.status) {
                    "OPEN", "FULL" -> Button(onClick = { onStart(ride.id) }) { Text("Start ride") }
                    "STARTED" -> Button(onClick = { onComplete(ride.id) }) { Text("Complete ride") }
                }
            }
        }
    }
}

@Composable
private fun BookingsTab(state: RidesUiState, onCancel: (Long) -> Unit) {
    if (state.myBookings.isEmpty()) { Empty("You haven't booked any rides yet.", null); return }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(state.myBookings, key = { it.id }) { ride ->
            val ctx = LocalContext.current
            RideCard(ride) {
                ride.myOtp?.let {
                    Text("Show host your code: $it", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { shareTrip(ctx, ride) }) { Text("Share trip") }
                    if (ride.myBookingStatus == "CONFIRMED" && ride.status in setOf("OPEN", "FULL")) {
                        OutlinedButton(onClick = { onCancel(ride.id) }) { Text("Cancel booking") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RideCard(ride: Ride, showHost: Boolean = true, actions: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("${ride.origin}  →  ${ride.destination}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            (ride.departAt?.take(16)?.replace('T', ' ') ?: "") + "  ·  ${ride.seatsLeft} seat(s) left  ·  ${formatRupees(ride.pricePaise)}/seat" +
                if (ride.womenOnly) "  ·  Women-only" else "",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showHost) {
            // Cash model: the host sets the price, the rider pays the host directly. The app never collects.
            Text(
                "Pay ${formatRupees(ride.pricePaise)}/seat to the host directly (cash/UPI). ITCABS doesn't collect payments.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Divider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NetworkPhoto(ride.hostPhotoUrl, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(ride.hostName.ifBlank { "Host" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        (ride.hostRating?.let { "★ %.1f".format(it) } ?: "New host") +
                            (ride.carModel.takeIf { it.isNotBlank() }?.let { "  ·  $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        actions()
    }
}

@Composable
private fun Loading() = Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

@Composable
private fun Empty(msg: String, onRefresh: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant)
        onRefresh?.let { TextButton(onClick = it) { Text("Refresh") } }
    }
}

/** Share a booked trip with family/friends via any app (safety). */
private fun shareTrip(context: android.content.Context, ride: Ride) {
    val text = buildString {
        append("I'm taking a carpool ride.\n")
        append("From: ${ride.origin}\nTo: ${ride.destination}\n")
        ride.departAt?.let { append("When: ${it.take(16).replace('T', ' ')}\n") }
        append("Host: ${ride.hostName}")
        if (ride.carModel.isNotBlank()) append(" (${ride.carModel})")
        append("\nShared from ITCABS.")
    }
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    context.startActivity(android.content.Intent.createChooser(send, "Share trip"))
}

/** Chained date → time picker → an OffsetDateTime in the device zone. */
private fun pickDateTime(context: android.content.Context, onPicked: (OffsetDateTime) -> Unit) {
    val now = Calendar.getInstance()
    DatePickerDialog(context, { _, y, m, d ->
        TimePickerDialog(context, { _, h, min ->
            val c = Calendar.getInstance().apply { set(y, m, d, h, min, 0); set(Calendar.MILLISECOND, 0) }
            onPicked(OffsetDateTime.ofInstant(c.toInstant(), ZoneId.systemDefault()))
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show()
    }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
}
