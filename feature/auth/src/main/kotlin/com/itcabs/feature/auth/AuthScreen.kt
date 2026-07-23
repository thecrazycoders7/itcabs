package com.itcabs.feature.auth

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.itcabs.domain.model.UserRole
import kotlinx.coroutines.launch

/**
 * Supabase auth: sign in with Google or email/password, then (first time) pick a role and — for
 * drivers — submit KYC. [webClientId] is the Google Web OAuth client id (from :app BuildConfig).
 */
@Composable
fun AuthScreen(
    webClientId: String,
    onSignedIn: (UserRole) -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var localError by remember { mutableStateOf<String?>(null) }

    if (state.signedIn) onSignedIn(state.signedInRole ?: UserRole.DRIVER)

    fun googleSignIn() {
        localError = null
        scope.launch {
            runCatching {
                val option = GetGoogleIdOption.Builder()
                    .setServerClientId(webClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val result = CredentialManager.create(context).getCredential(context, request)
                val cred = result.credential
                if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    viewModel.signInWithGoogle(GoogleIdTokenCredential.createFrom(cred.data).idToken)
                } else {
                    localError = "Unexpected Google credential"
                }
            }.onFailure { localError = it.message ?: "Google sign-in failed" }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("IT Cars", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)

        when (state.step) {
            AuthUiState.Step.SignIn -> SignIn(state, viewModel, ::googleSignIn)
            AuthUiState.Step.Onboard -> Onboard(state, viewModel)
            AuthUiState.Step.Kyc -> Kyc(state, viewModel)
        }

        if (state.loading) CircularProgressIndicator()
        (state.error ?: localError)?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SignIn(state: AuthUiState, vm: AuthViewModel, onGoogle: () -> Unit) {
    Button(onClick = onGoogle, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
        Text("Sign in with Google")
    }
    Text("or use email", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        value = state.email, onValueChange = vm::onEmailChange, label = { Text("Email") },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
    )
    OutlinedTextField(
        value = state.password, onValueChange = vm::onPasswordChange, label = { Text("Password") },
        singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = vm::signInEmail, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text("Sign in") }
    TextButton(onClick = vm::signUpEmail, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text("Create account") }
}

@Composable
private fun Onboard(state: AuthUiState, vm: AuthViewModel) {
    Text("Welcome! Tell us who you are.", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(state.role == UserRole.COORDINATOR, { vm.onRoleChange(UserRole.COORDINATOR) }, label = { Text("Coordinator") })
        FilterChip(state.role == UserRole.DRIVER, { vm.onRoleChange(UserRole.DRIVER) }, label = { Text("Driver") })
    }
    OutlinedTextField(
        value = state.name, onValueChange = vm::onNameChange, label = { Text("Your name") },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = vm::onboard, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
}

@Composable
private fun Kyc(state: AuthUiState, vm: AuthViewModel) {
    Text("Driver details", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(state.vehicleType, vm::onVehicleTypeChange, label = { Text("Vehicle type (Sedan/SUV)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(state.vehicleReg, vm::onVehicleRegChange, label = { Text("Registration number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(state.aadhaar, { vm.onAadhaarChange(it.filter(Char::isDigit)) }, label = { Text("Aadhaar number") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    OutlinedTextField(state.rcNumber, vm::onRcNumberChange, label = { Text("RC number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Button(onClick = vm::submitKyc, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text("Submit") }
}
