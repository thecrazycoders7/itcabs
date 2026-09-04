package com.itcabs.feature.dispatch

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
            2 -> MyRidesTab(state, onStart = { viewModel.setStatus(it, "STARTED") }, onComplete = { viewModel.setStatus(it, "COMPLETED") }, onLoadRiders = viewModel::loadRiders, onConfirmPickup = viewModel::confirmPickup, onRate = viewModel::rate)
            else -> BookingsTab(state, onCancel = viewModel::cancelBooking, onRate = viewModel::rate)
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
private fun MyRidesTab(
    state: RidesUiState,
    onStart: (Long) -> Unit,
    onComplete: (Long) -> Unit,
    onLoadRiders: (Long) -> Unit,
    onConfirmPickup: (Long, Long, String) -> Unit,
    onRate: (Long, Long, Int, String?) -> Unit,
) {
    if (state.myRides.isEmpty()) { Empty("You haven't offered any rides yet.", null); return }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(state.myRides, key = { it.id }) { ride ->
            var showRiders by remember(ride.id) { mutableStateOf(false) }
            val ctx = LocalContext.current
            RideCard(ride, showHost = false) {
                Text("${ride.totalSeats - ride.seatsLeft}/${ride.totalSeats} booked · ${ride.status}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(
                    onClick = { showRiders = !showRiders; if (showRiders) onLoadRiders(ride.id) },
                    contentPadding = PaddingValues(0.dp),
                ) { Text(if (showRiders) "Hide riders" else "View riders") }
                if (showRiders) {
                    val riders = state.riders[ride.id]
                    when {
                        riders == null -> Text("Loading…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        riders.isEmpty() -> Text("No bookings yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            riders.forEach { RiderRow(it, canRate = ride.status == "COMPLETED", onCall = { p -> dialPhone(ctx, p) }, onConfirm = { otp -> onConfirmPickup(ride.id, it.riderId, otp) }, onRate = { stars, review -> onRate(ride.id, it.riderId, stars, review) }) }
                        }
                    }
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
private fun RiderRow(rider: com.itcabs.domain.model.RideRider, canRate: Boolean, onCall: (String) -> Unit, onConfirm: (String) -> Unit, onRate: (Int, String?) -> Unit) {
    var askOtp by remember { mutableStateOf(false) }
    var otp by remember { mutableStateOf("") }
    if (askOtp) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askOtp = false },
            title = { Text("Confirm ${rider.riderName.ifBlank { "rider" }} boarded") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ask the rider for their 4-digit pickup code.")
                    OutlinedTextField(otp, { if (it.length <= 4) otp = it.filter(Char::isDigit) }, label = { Text("Pickup code") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = { TextButton(onClick = { askOtp = false; onConfirm(otp) }, enabled = otp.length == 4) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = { askOtp = false }) { Text("Cancel") } },
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(rider.riderName.ifBlank { "Rider" }, style = MaterialTheme.typography.bodyMedium)
            Text("${rider.seats} seat(s)" + if (rider.status == "COMPLETED") " · boarded ✓" else "",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        rider.riderPhone?.takeIf { it.isNotBlank() }?.let { TextButton(onClick = { onCall(it) }) { Text("Call") } }
        if (rider.status == "CONFIRMED") TextButton(onClick = { askOtp = true }) { Text("Pickup") }
        if (canRate) {
            var rate by remember { mutableStateOf(false) }
            TextButton(onClick = { rate = true }) { Text("Rate") }
            if (rate) RateDialog("Rate ${rider.riderName.ifBlank { "rider" }}", onDismiss = { rate = false }) { stars, review -> rate = false; onRate(stars, review) }
        }
    }
}

/** 1–5 star picker + optional review. */
@Composable
private fun RateDialog(title: String, onDismiss: () -> Unit, onSubmit: (Int, String?) -> Unit) {
    var stars by remember { mutableStateOf(0) }
    var review by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { i ->
                        Text(
                            if (i <= stars) "★" else "☆",
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (i <= stars) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { stars = i },
                        )
                    }
                }
                OutlinedTextField(review, { review = it }, label = { Text("Review (optional)") }, singleLine = false, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(stars, review.trim().ifBlank { null }) }, enabled = stars in 1..5) { Text("Submit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun dialPhone(context: android.content.Context, phone: String) {
    runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$phone")))
    }
}

@Composable
private fun BookingsTab(state: RidesUiState, onCancel: (Long) -> Unit, onRate: (Long, Long, Int, String?) -> Unit) {
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
                if (ride.status == "COMPLETED") {
                    var rate by remember(ride.id) { mutableStateOf(false) }
                    OutlinedButton(onClick = { rate = true }) { Text("Rate host") }
                    if (rate) RateDialog("Rate ${ride.hostName.ifBlank { "host" }}", onDismiss = { rate = false }) { stars, review -> rate = false; onRate(ride.id, ride.hostId, stars, review) }
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
