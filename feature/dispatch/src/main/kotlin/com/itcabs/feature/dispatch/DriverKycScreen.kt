package com.itcabs.feature.dispatch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** Standalone KYC form so a driver can complete verification after onboarding. */
@Composable
fun DriverKycScreen(onDone: () -> Unit, viewModel: DriverKycViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    // Awaiting review → show status, not the form (can't resubmit while PENDING)…
    if (state.kycStatus == com.itcabs.domain.model.KycStatus.PENDING) {
        val needsReupload = state.docs.values.any { it.status == DocStatus.REUPLOAD }
        // …unless the admin asked for a single document to be re-uploaded — let the driver fix just that.
        if (needsReupload) ReuploadView(state, viewModel, onDone) else UnderReviewView(onDone)
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            TextButton(onClick = onDone) { Text("Back") }
        }
        Text("Complete your KYC", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Verify your phone, upload your documents, then submit for review (~24h).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Rejected before → show why so the driver can fix + resubmit.
        if (state.kycStatus == com.itcabs.domain.model.KycStatus.REJECTED) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.errorContainer).padding(12.dp),
            ) {
                Text("Your last submission was rejected", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(state.rejectionReason ?: "Please recheck your documents and resubmit.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

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

        Divider(Modifier.padding(vertical = 4.dp))
        Text("Documents", style = MaterialTheme.typography.titleMedium)
        Text(
            "Clear photos, all four corners visible. Stored securely; only reviewers can see them.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DocumentsSection(state, viewModel)

        // Readiness checklist so the driver sees exactly what's left before submit.
        Divider(Modifier.padding(vertical = 4.dp))
        CheckLine(state.phoneVerified, "Phone verified")
        CheckLine(state.allDocsUploaded, "Documents uploaded (${state.uploadedCount}/${KYC_DOC_DEFS.size})")
        CheckLine(state.vehicleReg.isNotBlank() && state.aadhaar.length >= 4 && state.rcNumber.isNotBlank(), "Vehicle & identity details")

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Button(onClick = viewModel::submit, enabled = state.canSubmit && !state.loading, modifier = Modifier.fillMaxWidth()) {
            if (state.loading) CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp) else Text("Submit for review")
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Status shown after submitting — review is manual (~24h); the form is locked until it resolves. */
@Composable
private fun UnderReviewView(onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("⏳", style = MaterialTheme.typography.displayMedium)
        Text("Under Review", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Thanks — your documents are in. Our team reviews within ~24 hours. " +
                "You'll be able to claim trips once you're approved.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

/** Shown while PENDING when the admin requested re-uploads: fix just the flagged documents. */
@Composable
private fun ReuploadView(state: DriverKycUiState, viewModel: DriverKycViewModel, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            TextButton(onClick = onDone) { Text("Back") }
        }
        Text("Action needed", style = MaterialTheme.typography.headlineMedium)
        Text(
            "A reviewer asked you to re-upload the document(s) below. The rest of your review continues — " +
                "no need to resubmit.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DocumentsSection(state, viewModel, onlyReupload = true)
        Spacer(Modifier.height(8.dp))
    }
}

/** One ✓/○ readiness row. */
@Composable
private fun CheckLine(done: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (done) "✓" else "○", color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = if (done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------------------------
// Documents
// ---------------------------------------------------------------------------------------------

@Composable
private fun DocumentsSection(state: DriverKycUiState, viewModel: DriverKycViewModel, onlyReupload: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var targetType by remember { mutableStateOf<String?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    // Local preview URIs so the driver sees the photo they picked (bucket is private, no remote URL).
    val previews = remember { androidx.compose.runtime.mutableStateMapOf<String, Uri>() }

    fun handlePicked(type: String, uri: Uri) {
        previews[type] = uri
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { compressImage(context, uri) }
            if (bytes != null) viewModel.uploadDoc(type, bytes)
            else viewModel.docError(type, "Couldn't read that image — try another.")
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val t = targetType
        if (uri != null && t != null) handlePicked(t, uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val t = targetType; val uri = cameraUri
        if (ok && t != null && uri != null) handlePicked(t, uri)
    }

    val defs = if (onlyReupload) KYC_DOC_DEFS.filter { state.docs[it.type]?.status == DocStatus.REUPLOAD } else KYC_DOC_DEFS
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        defs.forEach { def ->
            DocRow(
                def = def,
                ui = state.docs[def.type] ?: DocUi(),
                preview = previews[def.type],
                onCamera = {
                    targetType = def.type
                    cameraUri = newCameraUri(context)
                    cameraUri?.let { camera.launch(it) }
                },
                onGallery = { targetType = def.type; gallery.launch("image/*") },
            )
        }
    }
}

@Composable
private fun DocRow(def: KycDocDef, ui: DocUi, preview: Uri?, onCamera: () -> Unit, onGallery: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DocThumb(preview, ui.status)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(def.label, style = MaterialTheme.typography.bodyMedium)
            when (ui.status) {
                DocStatus.UPLOADING -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Uploading…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DocStatus.UPLOADED -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Uploaded ✓", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = onGallery, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("Replace") }
                }
                DocStatus.REUPLOAD -> Column {
                    Text("Re-upload requested", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    ui.rejectReason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCamera) { Text("Camera") }
                        OutlinedButton(onClick = onGallery) { Text("Gallery") }
                    }
                }
                DocStatus.MISSING -> {
                    ui.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCamera) { Text("Camera") }
                        OutlinedButton(onClick = onGallery) { Text("Gallery") }
                    }
                }
            }
        }
    }
}

/** Small thumbnail decoded off the local preview URI (bucket is private). */
@Composable
private fun DocThumb(preview: Uri?, status: DocStatus) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(8.dp)
    val box = Modifier.size(56.dp).clip(shape)
    if (preview == null) {
        Column(box.padding(0.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (status == DocStatus.UPLOADED) "✓" else "📄", style = MaterialTheme.typography.titleLarge)
        }
        return
    }
    val bmp by produceState<Bitmap?>(initialValue = null, preview) {
        value = withContext(Dispatchers.IO) { decodeThumb(context, preview, 160) }
    }
    val b = bmp
    if (b != null) Image(b.asImageBitmap(), contentDescription = null, modifier = box, contentScale = ContentScale.Crop)
    else Column(box, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
    }
}

/** Temp FileProvider URI the camera app writes the full-resolution capture into. */
private fun newCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "kyc").apply { mkdirs() }
    val file = File(dir, "cap_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** Decode + downscale to <=1600px longest side, JPEG q70. Keeps document uploads small but legible. */
private fun compressImage(context: Context, uri: Uri): ByteArray? = runCatching {
    val bmp = decodeThumb(context, uri, 1600) ?: return null
    ByteArrayOutputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 70, it); it.toByteArray() }
}.getOrNull()

/** Bounds-first decode with inSampleSize so large camera photos never OOM. */
private fun decodeThumb(context: Context, uri: Uri, maxPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxPx || bounds.outHeight / sample > maxPx) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}

// ---------------------------------------------------------------------------------------------
// Phone verification
// ---------------------------------------------------------------------------------------------

/** Phone number → Send OTP → enter code → Verify, with a "Verified ✓" badge on success. */
@Composable
private fun PhoneVerifySection(state: DriverKycUiState, viewModel: DriverKycViewModel) {
    val context = LocalContext.current
    var otp by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }

    if (state.phoneVerified) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

private fun sendOtp(context: Context, phone: String, viewModel: DriverKycViewModel, onCodeSent: (String) -> Unit) {
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
