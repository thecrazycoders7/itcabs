package com.itcabs.feature.dispatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/** Standalone KYC form so a driver can complete verification after onboarding. */
@Composable
fun DriverKycScreen(onDone: () -> Unit, viewModel: DriverKycViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.submitted) { if (state.submitted) onDone() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            TextButton(onClick = onDone) { Text("Back") }
        }
        Text("Complete your KYC", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Submit your vehicle and identity details to get verified and start claiming trips.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(state.vehicleType, viewModel::onVehicleTypeChange, label = { Text("Vehicle type (Sedan/SUV)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.vehicleReg, viewModel::onVehicleRegChange, label = { Text("Registration number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.aadhaar, viewModel::onAadhaarChange, label = { Text("Aadhaar number") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(state.rcNumber, viewModel::onRcNumberChange, label = { Text("RC number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Button(onClick = viewModel::submit, enabled = state.canSubmit && !state.loading, modifier = Modifier.fillMaxWidth()) {
            if (state.loading) CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp) else Text("Submit KYC")
        }
    }
}
