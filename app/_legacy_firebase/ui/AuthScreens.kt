package com.itcabs.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.itcabs.Role
import com.itcabs.UserProfile

@Composable
fun PhoneAuthScreen(
    onSendOtp: (String, (String?, String?) -> Unit) -> Unit,
    onVerify: (String, String, (String) -> Unit) -> Unit,
) {
    var phone by remember { mutableStateOf("+91") }
    var otp by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("itcabs", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(4.dp))
        Text("Structured shuttle rides. No more chat chaos.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))

        if (verificationId == null) {
            OutlinedTextField(
                value = phone, onValueChange = { phone = it },
                label = { Text("Phone (with country code)") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { error = null; onSendOtp(phone) { id, err -> verificationId = id; error = err } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Send OTP") }
        } else {
            OutlinedTextField(
                value = otp, onValueChange = { otp = it },
                label = { Text("Enter OTP") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onVerify(verificationId!!, otp) { error = it } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Verify & continue") }
        }
        error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

/** First sign-in: choose a role. Drivers then fill KYC; coordinators are ready immediately. */
@Composable
fun RoleSetupScreen(vm: AppViewModel) {
    var role by remember { mutableStateOf<Role?>(null) }
    val phone = vm.repo.auth.currentUser?.phoneNumber.orEmpty()

    if (role == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("I am a…", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { role = Role.COORDINATOR }, Modifier.fillMaxWidth()) { Text("Transport Coordinator") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { role = Role.DRIVER }, Modifier.fillMaxWidth()) { Text("Driver / Vendor") }
        }
        return
    }

    if (role == Role.COORDINATOR) {
        var name by remember { mutableStateOf("") }
        Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
            Text("Coordinator profile", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(name, { name = it }, label = { Text("Your name / company") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { vm.saveProfile(UserProfile(role = Role.COORDINATOR.name, name = name, phone = phone, verified = true)) },
                enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth(),
            ) { Text("Done") }
        }
    } else {
        DriverKycForm(vm, phone)
    }
}

/** Driver KYC. All fields mandatory; profile saved with verified=false until an admin approves. */
@Composable
fun DriverKycForm(vm: AppViewModel, phone: String) {
    var name by remember { mutableStateOf("") }
    var vehicleType by remember { mutableStateOf("") }
    var vehicleReg by remember { mutableStateOf("") }
    var aadhaar by remember { mutableStateOf("") }
    var rc by remember { mutableStateOf("") }
    // ponytail: photoUrl typed as a URL for now; wire vm.repo.uploadPhoto to an
    // image picker when you add camera flow. Mandatory-non-blank still enforced.
    var photoUrl by remember { mutableStateOf("") }

    val profile = UserProfile(
        role = Role.DRIVER.name, name = name, phone = phone, vehicleType = vehicleType,
        vehicleReg = vehicleReg, aadhaar = aadhaar, rcNumber = rc, photoUrl = photoUrl, verified = false,
    )

    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text("Driver verification", style = MaterialTheme.typography.headlineSmall)
        Text("All fields required. A supervisor verifies you before your claims go live.",
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        field(name, "Full name") { name = it }
        field(vehicleType, "Vehicle type (Sedan/SUV/Tempo…)") { vehicleType = it }
        field(vehicleReg, "Vehicle registration no.") { vehicleReg = it }
        field(aadhaar, "Aadhaar no.") { aadhaar = it }
        field(rc, "RC (registration certificate) no.") { rc = it }
        field(photoUrl, "Profile photo URL") { photoUrl = it }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.saveProfile(profile) },
            enabled = profile.kycComplete, modifier = Modifier.fillMaxWidth(),
        ) { Text("Submit for verification") }
    }
}

@Composable
private fun field(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
}
