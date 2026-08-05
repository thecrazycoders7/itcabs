package com.itcabs.feature.dispatch

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.itcabs.domain.model.Area
import com.itcabs.domain.model.NewCompanyJob
import com.itcabs.domain.model.NewStop
import com.itcabs.domain.model.TripType

/** One employee stop: employee + a Google Places location (address + exact coords + place_id). */
private data class StopForm(
    val name: String = "",
    val address: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val placeId: String? = null,
    val phone: String = "",
)

/** Coordinator create/edit flow: one company + trip type + ordered employee stops, one cab. */
@Composable
fun CreateCompanyJobScreen(onDone: () -> Unit, editJob: com.itcabs.domain.model.CompanyJob? = null, viewModel: CompanyJobViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(state.published) { if (state.published) onDone() }

    // Places SDK reads the API key from the manifest meta-data; init once.
    LaunchedEffect(Unit) {
        if (!Places.isInitialized()) {
            val key = context.packageManager
                .getApplicationInfo(context.packageName, android.content.pm.PackageManager.GET_META_DATA)
                .metaData?.getString("com.google.android.geo.API_KEY")
            if (!key.isNullOrBlank()) Places.initialize(context.applicationContext, key)
        }
    }

    var company by remember { mutableStateOf(editJob?.companyName ?: "") }
    var tripType by remember { mutableStateOf(editJob?.tripType ?: TripType.DROP) }
    var officeAddr by remember { mutableStateOf(editJob?.officeAddress?.ifBlank { editJob.office } ?: "") }
    var officeLat by remember { mutableStateOf(editJob?.officeLat) }
    var officeLng by remember { mutableStateOf(editJob?.officeLng) }
    var officePlaceId by remember { mutableStateOf<String?>(null) }
    var pickupTime by remember { mutableStateOf(editJob?.pickupTime ?: "") }
    var dropTime by remember { mutableStateOf(editJob?.dropTime ?: "") }
    var vehicleType by remember { mutableStateOf(editJob?.vehicleType?.uppercase()?.takeIf { it == "SEDAN" || it == "SUV" } ?: "SEDAN") }
    var ac by remember { mutableStateOf(editJob?.vehicleAc ?: true) }
    var fare by remember { mutableStateOf(editJob?.let { (it.farePaise / 100).toString() } ?: "") }
    var stops by remember { mutableStateOf(editJob?.stops?.map { StopForm(it.employeeName, it.address, it.lat, it.lng, it.placeId, it.phone) }?.ifEmpty { listOf(StopForm()) } ?: listOf(StopForm())) }
    var searchIndex by remember { mutableStateOf(-1) }   // -2 = company office, >=0 = stop index
    var placesError by remember { mutableStateOf<String?>(null) }
    val maxStops = if (vehicleType == "SUV") 6 else 4

    // Places autocomplete returns a Place → fill the stop being searched; surface any error so we
    // can see why (usually "legacy Places API not enabled", billing, or key-restriction propagation).
    val placesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        when (res.resultCode) {
            Activity.RESULT_OK -> res.data?.let { data ->
                val p = Autocomplete.getPlaceFromIntent(data)
                placesError = null
                if (searchIndex == -2) {
                    officeAddr = p.address ?: p.name ?: officeAddr
                    officeLat = p.latLng?.latitude; officeLng = p.latLng?.longitude; officePlaceId = p.id
                } else if (searchIndex in stops.indices) {
                    stops = stops.mapIndexed { j, s ->
                        if (j == searchIndex) s.copy(
                            address = p.address ?: p.name ?: s.address,
                            lat = p.latLng?.latitude, lng = p.latLng?.longitude, placeId = p.id,
                        ) else s
                    }
                }
            }
            AutocompleteActivity.RESULT_ERROR -> {
                // The overlay opens then closes on its own — surface WHY loudly (usually "Places API
                // legacy not enabled", billing off, or key/SHA-1 restriction) instead of a silent back.
                placesError = res.data?.let { Autocomplete.getStatusFromIntent(it).statusMessage } ?: "Places error"
                android.widget.Toast.makeText(context, "Maps: $placesError", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    fun launchSearch(i: Int) {
        // Never crash if the Maps/Places key is missing (empty-key build): degrade to a message.
        if (!Places.isInitialized()) {
            placesError = "Address search unavailable — maps key not configured in this build."
            android.widget.Toast.makeText(context, placesError, android.widget.Toast.LENGTH_LONG).show()
            return
        }
        searchIndex = i
        val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG)
        placesLauncher.launch(
            Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields).setCountries(listOf("IN")).build(context),
        )
    }

    val canPublish = company.isNotBlank() && fare.toLongOrNull() != null &&
        stops.isNotEmpty() && stops.all { it.name.isNotBlank() }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(if (editJob != null) "Edit Company Job" else "New Company Job", style = MaterialTheme.typography.headlineMedium)

        Card {
            SectionLbl("COMPANY")
            LblField("Company name", company, { company = it }, "e.g. ABC Technologies")
            Text("Trip type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tripType == TripType.PICKUP, onClick = { tripType = TripType.PICKUP }, label = { Text("Pickup") })
                FilterChip(selected = tripType == TripType.DROP, onClick = { tripType = TripType.DROP }, label = { Text("Drop") })
            }
            // Company office via Places (same picker as employee stops).
            Text(if (tripType == TripType.DROP) "Office address (destination)" else "Office address (origin)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { searchIndex = -2; launchSearch(-2) }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Place, null, Modifier.height(18.dp))
                Text("  " + officeAddr.ifBlank { "Search company address…" }, modifier = Modifier.fillMaxWidth())
            }
            // Pickup + drop time.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField("Pickup time", pickupTime, Modifier.weight(1f)) { pickupTime = it }
                TimeField("Drop time", dropTime, Modifier.weight(1f)) { dropTime = it }
            }
            // Vehicle type + category.
            Text("Vehicle type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = vehicleType == "SEDAN", onClick = { vehicleType = "SEDAN" }, label = { Text("Sedan") })
                FilterChip(selected = vehicleType == "SUV", onClick = { vehicleType = "SUV" }, label = { Text("SUV") })
            }
            Text("Vehicle category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = ac, onClick = { ac = true }, label = { Text("AC") })
                FilterChip(selected = !ac, onClick = { ac = false }, label = { Text("Non-AC") })
            }
            LblField("Fare for the whole job (₹)", fare, { fare = it.filter(Char::isDigit) }, "e.g. 900")
        }

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Employees / Stops", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Reorder stops for a shorter route (nearest-neighbour on the picked coordinates).
                OutlinedButton(
                    onClick = { stops = optimizeStops(stops) },
                    enabled = stops.count { it.lat != null && it.lng != null } >= 3,
                    shape = MaterialTheme.shapes.small,
                ) { Icon(Icons.Filled.Route, null, Modifier.height(18.dp)); Text("Optimize") }
                OutlinedButton(onClick = { if (stops.size < maxStops) stops = stops + StopForm() }, enabled = stops.size < maxStops, shape = MaterialTheme.shapes.small) {
                    Icon(Icons.Filled.Add, null, Modifier.height(18.dp)); Text("Add")
                }
            }
        }
        Text(
            if (stops.size >= maxStops) "$vehicleType is full — max $maxStops stops."
            else if (tripType == TripType.PICKUP) "Pickup order (first → last), then drop at the office."
            else "Drop order (first → last), starting from the office.",
            style = MaterialTheme.typography.labelSmall,
            color = if (stops.size >= maxStops) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        stops.forEachIndexed { i, s ->
            StopCard(
                index = i, stop = s, areas = state.areas, canRemove = stops.size > 1, canUp = i > 0, canDown = i < stops.size - 1,
                onChange = { upd -> stops = stops.mapIndexed { j, x -> if (j == i) upd else x } },
                onRemove = { stops = stops.filterIndexed { j, _ -> j != i } },
                onUp = { stops = stops.toMutableList().also { it.add(i - 1, it.removeAt(i)) } },
                onDown = { stops = stops.toMutableList().also { it.add(i + 1, it.removeAt(i)) } },
                onSearch = { launchSearch(i) },
            )
        }

        placesError?.let { Text("Location search error: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

        Button(
            onClick = {
                val job = NewCompanyJob(
                    companyName = company, tripType = tripType, office = officeAddr, officeAddress = officeAddr,
                    officeLat = officeLat, officeLng = officeLng, officePlaceId = officePlaceId,
                    pickupTime = pickupTime, dropTime = dropTime, vehicleType = vehicleType, vehicleAc = ac,
                    farePaise = (fare.toLongOrNull() ?: 0) * 100,
                    stops = stops.map { f -> NewStop(f.name, f.address, f.lat, f.lng, f.placeId, f.phone) },
                )
                if (editJob != null) viewModel.edit(editJob.id, job) else viewModel.create(job)
            },
            enabled = canPublish && stops.size <= maxStops && !state.loading,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (state.loading) CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text(if (editJob != null) "Save Changes" else "Publish Job", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun StopCard(
    index: Int, stop: StopForm, areas: List<Area>, canRemove: Boolean, canUp: Boolean, canDown: Boolean,
    onChange: (StopForm) -> Unit, onRemove: () -> Unit, onUp: () -> Unit, onDown: () -> Unit, onSearch: () -> Unit,
) {
    var areaMenu by remember { mutableStateOf(false) }
    Card {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("STOP ${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row {
                IconButton(onClick = onUp, enabled = canUp) { Icon(Icons.Filled.ArrowUpward, "Move up") }
                IconButton(onClick = onDown, enabled = canDown) { Icon(Icons.Filled.ArrowDownward, "Move down") }
                if (canRemove) IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, "Remove", tint = MaterialTheme.colorScheme.error) }
            }
        }
        LblField("Employee name", stop.name, { onChange(stop.copy(name = it)) }, "Name")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Location", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Precise: Google Places search → exact address + coords (needs active billing).
            OutlinedButton(onClick = onSearch, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Place, null, Modifier.height(18.dp))
                Text("  " + stop.address.ifBlank { "Search location…" }, modifier = Modifier.fillMaxWidth())
            }
            // Fallback: quick-pick an area centroid — works without Places/billing.
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { areaMenu = true }) { Text("or pick area") }
                DropdownMenu(expanded = areaMenu, onDismissRequest = { areaMenu = false }) {
                    areas.forEach { a ->
                        DropdownMenuItem(text = { Text(a.name) }, onClick = {
                            onChange(stop.copy(address = a.name, lat = a.lat, lng = a.lng, placeId = null))
                            areaMenu = false
                        })
                    }
                }
            }
        }
        LblField("Phone (optional)", stop.phone, { onChange(stop.copy(phone = it)) }, "10-digit")
    }
}

/**
 * Nearest-neighbour reorder from the first stop — a short (not provably optimal) route for a handful
 * of city stops, using the Places coordinates. Stops without coords keep their tail position.
 * ponytail: greedy NN is plenty for ~3–8 stops; road-optimal needs the Directions API + a server key.
 */
private fun optimizeStops(stops: List<StopForm>): List<StopForm> {
    val located = stops.filter { it.lat != null && it.lng != null }.toMutableList()
    val rest = stops.filter { it.lat == null || it.lng == null }
    if (located.size < 3) return stops
    val ordered = mutableListOf(located.removeAt(0))
    while (located.isNotEmpty()) {
        val last = ordered.last()
        val next = located.minByOrNull { stopKm(last, it) }!!
        located.remove(next); ordered.add(next)
    }
    return ordered + rest
}

private fun stopKm(a: StopForm, b: StopForm): Double {
    val r = 6371.0
    val dLat = Math.toRadians(b.lat!! - a.lat!!)
    val dLon = Math.toRadians(b.lng!! - a.lng!!)
    val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
    return r * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
}

@Composable
private fun TimeField(label: String, value: String, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
            onClick = {
                val now = java.util.Calendar.getInstance()
                android.app.TimePickerDialog(
                    context, { _, h, m -> onPick("%02d:%02d".format(h, m)) },
                    now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE), true,
                ).show()
            },
            shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
        ) { Text(value.ifBlank { "Pick" }) }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
    )
}

@Composable
private fun SectionLbl(text: String) =
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun LblField(label: String, value: String, onChange: (String) -> Unit, placeholder: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = value, onValueChange = onChange, placeholder = { Text(placeholder) }, singleLine = true,
            shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = if (label.contains("₹") || label.contains("Phone")) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        )
    }
}
