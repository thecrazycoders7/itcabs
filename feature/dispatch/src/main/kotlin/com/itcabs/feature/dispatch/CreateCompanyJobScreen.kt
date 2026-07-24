package com.itcabs.feature.dispatch

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itcabs.domain.model.Area
import com.itcabs.domain.model.NewCompanyJob
import com.itcabs.domain.model.NewStop
import com.itcabs.domain.model.TripType

/** One employee stop in the create form. Location is picked from the area gazetteer (→ coords). */
private data class StopForm(val name: String = "", val area: String = "", val phone: String = "")

/** Coordinator create flow: one company + trip type + ordered employee stops, one cab. */
@Composable
fun CreateCompanyJobScreen(onDone: () -> Unit, viewModel: CompanyJobViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.published) { if (state.published) onDone() }

    var company by remember { mutableStateOf("") }
    var tripType by remember { mutableStateOf(TripType.DROP) }
    var office by remember { mutableStateOf("") }
    var vehicle by remember { mutableStateOf("Sedan") }
    var fare by remember { mutableStateOf("") }
    var stops by remember { mutableStateOf(listOf(StopForm())) }

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
            OutlinedButton(onClick = { stops = stops + StopForm() }, shape = MaterialTheme.shapes.small) {
                Icon(Icons.Filled.Add, null, Modifier.height(18.dp)); Text("Add")
            }
        }
        Text(
            if (tripType == TripType.PICKUP) "Pickup order (first → last), then drop at the office."
            else "Drop order (first → last), starting from the office.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        stops.forEachIndexed { i, s ->
            StopCard(
                index = i, stop = s, areas = state.areas, canRemove = stops.size > 1,
                canUp = i > 0, canDown = i < stops.size - 1,
                onChange = { upd -> stops = stops.mapIndexed { j, x -> if (j == i) upd else x } },
                onRemove = { stops = stops.filterIndexed { j, _ -> j != i } },
                onUp = { stops = stops.toMutableList().also { it.add(i - 1, it.removeAt(i)) } },
                onDown = { stops = stops.toMutableList().also { it.add(i + 1, it.removeAt(i)) } },
            )
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

        Button(
            onClick = {
                val byName = state.areas.associateBy { it.name.lowercase() }
                viewModel.create(
                    NewCompanyJob(
                        companyName = company, tripType = tripType, office = office, vehicleType = vehicle,
                        farePaise = (fare.toLongOrNull() ?: 0) * 100,
                        stops = stops.map { f ->
                            val a: Area? = byName[f.area.lowercase()]
                            NewStop(employeeName = f.name, address = f.area, lat = a?.lat, lng = a?.lng, phone = f.phone)
                        },
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
    index: Int, stop: StopForm, areas: List<Area>, canRemove: Boolean, canUp: Boolean, canDown: Boolean,
    onChange: (StopForm) -> Unit, onRemove: () -> Unit, onUp: () -> Unit, onDown: () -> Unit,
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
        AreaDropdownField(stop.area, areas.map { it.name }) { onChange(stop.copy(area = it)) }
        LblField("Phone (optional)", stop.phone, { onChange(stop.copy(phone = it)) }, "10-digit")
    }
}

@Composable
private fun AreaDropdownField(selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text("Location (area)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { open = true }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Text(selected.ifBlank { "Choose area…" }, modifier = Modifier.fillMaxWidth())
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(name); open = false })
            }
        }
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
