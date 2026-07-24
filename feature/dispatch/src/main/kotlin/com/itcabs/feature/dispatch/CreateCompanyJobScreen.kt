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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
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

/** Coordinator create flow: one company + trip type + ordered employee stops, one cab. */
@Composable
fun CreateCompanyJobScreen(onDone: () -> Unit, viewModel: CompanyJobViewModel = hiltViewModel()) {
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

    var company by remember { mutableStateOf("") }
    var tripType by remember { mutableStateOf(TripType.DROP) }
    var office by remember { mutableStateOf("") }
    var vehicle by remember { mutableStateOf("Sedan") }
    var fare by remember { mutableStateOf("") }
    var stops by remember { mutableStateOf(listOf(StopForm())) }
    var searchIndex by remember { mutableStateOf(-1) }

    // Places autocomplete returns a Place → fill the stop being searched.
    val placesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK && searchIndex in stops.indices) {
            res.data?.let { data ->
                val p = Autocomplete.getPlaceFromIntent(data)
                stops = stops.mapIndexed { j, s ->
                    if (j == searchIndex) s.copy(
                        address = p.address ?: p.name ?: s.address,
                        lat = p.latLng?.latitude, lng = p.latLng?.longitude, placeId = p.id,
                    ) else s
                }
            }
        }
    }
    fun launchSearch(i: Int) {
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
        Text("New Company Job", style = MaterialTheme.typography.headlineMedium)

        Card {
            SectionLbl("COMPANY")
            LblField("Company name", company, { company = it }, "e.g. ABC Technologies")
            Text("Trip type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tripType == TripType.PICKUP, onClick = { tripType = TripType.PICKUP }, label = { Text("Pickup") })
                FilterChip(selected = tripType == TripType.DROP, onClick = { tripType = TripType.DROP }, label = { Text("Drop") })
            }
            LblField(if (tripType == TripType.DROP) "Office (destination)" else "Office (origin)", office, { office = it }, "e.g. ABC HQ, Hitec City")
            LblField("Vehicle type", vehicle, { vehicle = it }, "Sedan / SUV / Tempo")
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
                OutlinedButton(onClick = { stops = stops + StopForm() }, shape = MaterialTheme.shapes.small) {
                    Icon(Icons.Filled.Add, null, Modifier.height(18.dp)); Text("Add")
                }
            }
        }
        Text(
            if (tripType == TripType.PICKUP) "Pickup order (first → last), then drop at the office."
            else "Drop order (first → last), starting from the office.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        stops.forEachIndexed { i, s ->
            StopCard(
                index = i, stop = s, canRemove = stops.size > 1, canUp = i > 0, canDown = i < stops.size - 1,
                onChange = { upd -> stops = stops.mapIndexed { j, x -> if (j == i) upd else x } },
                onRemove = { stops = stops.filterIndexed { j, _ -> j != i } },
                onUp = { stops = stops.toMutableList().also { it.add(i - 1, it.removeAt(i)) } },
                onDown = { stops = stops.toMutableList().also { it.add(i + 1, it.removeAt(i)) } },
                onSearch = { launchSearch(i) },
            )
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

        Button(
            onClick = {
                viewModel.create(
                    NewCompanyJob(
                        companyName = company, tripType = tripType, office = office, vehicleType = vehicle,
                        farePaise = (fare.toLongOrNull() ?: 0) * 100,
                        stops = stops.map { f -> NewStop(f.name, f.address, f.lat, f.lng, f.placeId, f.phone) },
                    ),
                )
            },
            enabled = canPublish && !state.loading,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (state.loading) CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text("Publish Job", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun StopCard(
    index: Int, stop: StopForm, canRemove: Boolean, canUp: Boolean, canDown: Boolean,
    onChange: (StopForm) -> Unit, onRemove: () -> Unit, onUp: () -> Unit, onDown: () -> Unit, onSearch: () -> Unit,
) {
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
        // Google Places search → exact address + coords.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Location", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onSearch, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Place, null, Modifier.height(18.dp))
                Text("  " + stop.address.ifBlank { "Search location…" }, modifier = Modifier.fillMaxWidth())
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
