package com.itcabs.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itcabs.core.designsystem.ItCabsColors
import com.itcabs.domain.model.UserRole
import androidx.compose.ui.tooling.preview.Preview
import com.itcabs.core.designsystem.ItCabsTheme

/**
 * The login flow, styled to the IT Cars design (login_screen + otp_verification).
 * Renders [AuthUiState], forwards events to [AuthViewModel], signals [onSignedIn] on success.
 */
@Composable
fun AuthScreen(
    onSignedIn: (com.itcabs.domain.model.UserRole) -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    AuthContent(
        state = state,
        onSignedIn = onSignedIn,
        onPhoneChange = viewModel::onPhoneChange,
        requestOtp = viewModel::requestOtp,
        onCodeChange = viewModel::onCodeChange,
        onRoleChange = viewModel::onRoleChange,
        onNameChange = viewModel::onNameChange,
        verify = viewModel::verify,
        onVehicleTypeChange = viewModel::onVehicleTypeChange,
        onVehicleRegChange = viewModel::onVehicleRegChange,
        onAadhaarChange = viewModel::onAadhaarChange,
        onRcNumberChange = viewModel::onRcNumberChange,
        onPhotoUrlChange = viewModel::onPhotoUrlChange,
        submitKyc = viewModel::submitKyc
    )
}

@Composable
fun AuthContent(
    state: AuthUiState,
    onSignedIn: (UserRole) -> Unit = {},
    onPhoneChange: (String) -> Unit = {},
    requestOtp: () -> Unit = {},
    onCodeChange: (String) -> Unit = {},
    onRoleChange: (UserRole) -> Unit = {},
    onNameChange: (String) -> Unit = {},
    verify: () -> Unit = {},
    onVehicleTypeChange: (String) -> Unit = {},
    onVehicleRegChange: (String) -> Unit = {},
    onAadhaarChange: (String) -> Unit = {},
    onRcNumberChange: (String) -> Unit = {},
    onPhotoUrlChange: (String) -> Unit = {},
    submitKyc: () -> Unit = {},
) {
    LaunchedEffect(state.signedIn) {
        if (state.signedIn) state.signedInRole?.let(onSignedIn)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        when (state.step) {
            AuthUiState.Step.Phone -> LoginStep(state, onPhoneChange, requestOtp)
            AuthUiState.Step.Code -> OtpStep(state, onCodeChange, onRoleChange, onNameChange, verify)
            AuthUiState.Step.Kyc -> KycStep(
                state = state,
                onVehicleTypeChange = onVehicleTypeChange,
                onVehicleRegChange = onVehicleRegChange,
                onAadhaarChange = onAadhaarChange,
                onRcNumberChange = onRcNumberChange,
                onPhotoUrlChange = onPhotoUrlChange,
                submitKyc = submitKyc
            )
        }

        if (state.loading) CircularProgressIndicator()
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun KycStep(
    state: AuthUiState,
    onVehicleTypeChange: (String) -> Unit,
    onVehicleRegChange: (String) -> Unit,
    onAadhaarChange: (String) -> Unit,
    onRcNumberChange: (String) -> Unit,
    onPhotoUrlChange: (String) -> Unit,
    submitKyc: () -> Unit,
) {
    BrandBadge(Icons.Filled.Verified)
    Text(
        "Driver KYC",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        "Please provide your vehicle and identity details to start claiming trips.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.vehicleType,
            onValueChange = onVehicleTypeChange,
            label = { Text("Vehicle Type (e.g. Sedan, SUV)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.vehicleReg,
            onValueChange = onVehicleRegChange,
            label = { Text("Registration Number") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.aadhaar,
            onValueChange = onAadhaarChange,
            label = { Text("Aadhaar Number") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            value = state.rcNumber,
            onValueChange = onRcNumberChange,
            label = { Text("RC Number") },
            modifier = Modifier.fillMaxWidth()
        )
        // ponytail: real driver-photo capture/upload is a later feature (image picker + storage
        // that returns a URL). Until then we don't ask the user for a URL — photoUrl is sent blank.
    }

    val kycComplete = state.vehicleType.isNotBlank() && state.vehicleReg.isNotBlank() &&
            state.aadhaar.isNotBlank() && state.rcNumber.isNotBlank()

    Button(
        onClick = submitKyc,
        enabled = !state.loading && kycComplete,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Text("Submit KYC", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun LoginStep(
    state: AuthUiState,
    onPhoneChange: (String) -> Unit,
    requestOtp: () -> Unit,
) {
    BrandBadge(Icons.Filled.DirectionsCar)
    Text(
        "Welcome to IT Cars",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
    Text(
        "Enter your mobile number to get started with your professional mobility dashboard.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )

    // Login card
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "MOBILE NUMBER",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .height(56.dp)
                    .clip(MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("🇮🇳 +91", style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedTextField(
                value = state.phone,
                onValueChange = { onPhoneChange(it.filter(Char::isDigit).take(10)) },
                placeholder = { Text("Enter 10-digit number") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(
            onClick = requestOtp,
            enabled = !state.loading && state.phone.length == 10,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text("Continue with OTP", style = MaterialTheme.typography.titleLarge)
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 8.dp))
        }
    }

    // Value props
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        InfoCard(Icons.Filled.Verified, "Verified Drivers", "Network of pre-vetted mobility experts.", Modifier.weight(1f))
        InfoCard(Icons.Filled.Analytics, "Fleet Insights", "Real-time tracking and analytics.", Modifier.weight(1f))
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            "Secure login powered by phone verification",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OtpStep(
    state: AuthUiState,
    onCodeChange: (String) -> Unit,
    onRoleChange: (UserRole) -> Unit,
    onNameChange: (String) -> Unit,
    verify: () -> Unit,
) {
    BrandBadge(Icons.Filled.Shield)
    Text(
        "Verify OTP",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        "Sent to +91 ${state.phone}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OtpBoxes(value = state.code, onValueChange = onCodeChange, modifier = Modifier.fillMaxWidth())

    // New users pick a role + name; returning users keep theirs (backend ignores these then).
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UserRole.entries.forEach { role ->
            FilterChip(
                selected = state.role == role,
                onClick = { onRoleChange(role) },
                label = { Text(role.name) },
            )
        }
    }
    OutlinedTextField(
        value = state.name,
        onValueChange = onNameChange,
        label = { Text("Name (new users)") },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    )

    Button(
        onClick = verify,
        enabled = !state.loading && state.code.length == 6,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Text("Verify", style = MaterialTheme.typography.titleLarge)
    }
}

@Preview(showBackground = true)
@Composable
fun AuthScreenPhonePreview() {
    ItCabsTheme {
        AuthContent(state = AuthUiState(step = AuthUiState.Step.Phone))
    }
}

@Preview(showBackground = true)
@Composable
fun AuthScreenOtpPreview() {
    ItCabsTheme {
        AuthContent(state = AuthUiState(step = AuthUiState.Step.Code, phone = "9988776655"))
    }
}

@Preview(showBackground = true)
@Composable
fun AuthScreenKycPreview() {
    ItCabsTheme {
        AuthContent(state = AuthUiState(step = AuthUiState.Step.Kyc))
    }
}

/** The blue rounded-square brand badge used on both auth screens. */
@Composable
private fun BrandBadge(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(ItCabsColors.Primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = ItCabsColors.OnPrimary, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Six single-digit boxes backed by one hidden field — the design's OTP input. */
@Composable
private fun OtpBoxes(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) onValueChange(it) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = modifier,
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(6) { index ->
                    val char = value.getOrNull(index)?.toString() ?: ""
                    val filled = char.isNotEmpty()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(MaterialTheme.shapes.small)
                            .border(
                                width = if (filled) 2.dp else 1.dp,
                                color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = MaterialTheme.shapes.small,
                            )
                            .background(MaterialTheme.colorScheme.surfaceContainerLow),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            char,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
    )
}
