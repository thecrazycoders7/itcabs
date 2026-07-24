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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
            "Verify your phone, then submit your vehicle and identity details to start claiming trips.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 1. Phone verification (Firebase Phone Auth) — required before submitting KYC.
        PhoneVerifySection(state, viewModel)

        Text("Vehicle type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.vehicleType.equals("SEDAN", true), onClick = { viewModel.onVehicleTypeChange("SEDAN") }, label = { Text("Sedan") })
            FilterChip(selected = state.vehicleType.equals("SUV", true), onClick = { viewModel.onVehicleTypeChange("SUV") }, label = { Text("SUV") })
        }
        OutlinedTextField(state.vehicleReg, viewModel::onVehicleRegChange, label = { Text("Registration number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.aadhaar, viewModel::onAadhaarChange, label = { Text("Aadhaar number") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(state.rcNumber, viewModel::onRcNumberChange, label = { Text("RC number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Button(onClick = viewModel::submit, enabled = state.canSubmit && !state.loading, modifier = Modifier.fillMaxWidth()) {
            if (state.loading) CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp) else Text("Submit KYC")
        }
    }
}

/** Phone number → Send OTP → enter code → Verify, with a "Verified ✓" badge on success. */
@Composable
private fun PhoneVerifySection(state: DriverKycUiState, viewModel: DriverKycViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var otp by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var verificationId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    if (state.phoneVerified) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Phone verified ✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
            state.phone?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        return
    }

    Text("Mobile number", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        state.phone, viewModel::onPhoneChange, label = { Text("+91 98765 43210") }, singleLine = true,
        modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
    )
    if (state.otpSent) {
        OutlinedTextField(otp, { if (it.length <= 6) otp = it.filter(Char::isDigit) }, label = { Text("6-digit OTP") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Button(
            onClick = { verificationId?.let { vid -> confirmOtp(vid, otp, viewModel) } },
            enabled = otp.length == 6 && !state.verifyingPhone, modifier = Modifier.fillMaxWidth(),
        ) { Text("Verify OTP") }
    } else {
        Button(
            onClick = { sendOtp(context, toE164(state.phone), viewModel) { verificationId = it } },
            enabled = state.phone.length >= 10 && !state.verifyingPhone, modifier = Modifier.fillMaxWidth(),
        ) { if (state.verifyingPhone) CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp) else Text("Send OTP") }
    }
    state.phoneError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}

private fun toE164(raw: String) = if (raw.startsWith("+")) raw else "+91" + raw.filter(Char::isDigit).takeLast(10)

private fun sendOtp(context: android.content.Context, phone: String, viewModel: DriverKycViewModel, onCodeSent: (String) -> Unit) {
    val activity = context as? android.app.Activity ?: run { viewModel.onPhoneError("Cannot start verification"); return }
    viewModel.onOtpSending()
    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    val callbacks = object : com.google.firebase.auth.PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(cred: com.google.firebase.auth.PhoneAuthCredential) = signIn(auth, cred, viewModel)
        override fun onVerificationFailed(e: com.google.firebase.FirebaseException) { viewModel.onPhoneError(e.message) }
        override fun onCodeSent(id: String, token: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken) {
            onCodeSent(id); viewModel.onOtpSent()
        }
    }
    val options = com.google.firebase.auth.PhoneAuthOptions.newBuilder(auth)
        .setPhoneNumber(phone).setTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .setActivity(activity).setCallbacks(callbacks).build()
    com.google.firebase.auth.PhoneAuthProvider.verifyPhoneNumber(options)
}

private fun confirmOtp(verificationId: String, code: String, viewModel: DriverKycViewModel) {
    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    signIn(auth, com.google.firebase.auth.PhoneAuthProvider.getCredential(verificationId, code), viewModel)
}

private fun signIn(auth: com.google.firebase.auth.FirebaseAuth, cred: com.google.firebase.auth.PhoneAuthCredential, viewModel: DriverKycViewModel) {
    auth.signInWithCredential(cred).addOnCompleteListener { task ->
        if (task.isSuccessful) {
            task.result?.user?.getIdToken(false)?.addOnSuccessListener { r ->
                r.token?.let { viewModel.submitPhoneToken(it) } ?: viewModel.onPhoneError("No token")
            }?.addOnFailureListener { viewModel.onPhoneError(it.message) }
        } else viewModel.onPhoneError(task.exception?.message ?: "Wrong code")
    }
}
