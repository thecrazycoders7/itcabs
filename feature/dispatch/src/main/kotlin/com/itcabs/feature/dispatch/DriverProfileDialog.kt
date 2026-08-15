package com.itcabs.feature.dispatch

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.PublicDriverProfile
import com.itcabs.domain.repository.DriverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DriverProfileViewModel @Inject constructor(private val driver: DriverRepository) : ViewModel() {
    private val _profile = MutableStateFlow<PublicDriverProfile?>(null)
    val profile: StateFlow<PublicDriverProfile?> = _profile.asStateFlow()
    private var loadedFor: Long? = null

    fun load(driverId: Long) {
        if (loadedFor == driverId) return
        loadedFor = driverId
        viewModelScope.launch {
            (driver.publicProfile(driverId) as? AppResult.Ok)?.let { _profile.value = it.value }
        }
    }
}

/** Full driver profile the coordinator sees once a driver is on their job (with a Call button). */
@Composable
fun DriverProfileDialog(driverId: Long, onDismiss: () -> Unit, viewModel: DriverProfileViewModel = hiltViewModel()) {
    LaunchedEffect(driverId) { viewModel.load(driverId) }
    val p by viewModel.profile.collectAsState()
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p?.name?.ifBlank { "Driver" } ?: "Driver") },
        text = {
            val prof = p
            if (prof == null) Text("Loading…")
            else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Face photo so the coordinator sees who they're dispatching.
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    NetworkPhoto(prof.photoUrl, size = 84.dp)
                }
                Line("Rating", if (prof.ratingCount > 0 && prof.avgRating != null) "★ %.1f (%d)".format(prof.avgRating, prof.ratingCount) else "—")
                Line("Trips completed", prof.tripsCompleted.toString())
                Line("No-shows", prof.noShows.toString())
                Line("Vehicle", listOfNotNull(prof.vehicleType, prof.vehicleReg).joinToString(" · ").ifBlank { "—" })
                Line("Verification", prof.kycStatus.name)
                prof.phone?.takeIf { it.isNotBlank() }?.let { Line("Phone", it) }
                prof.email?.takeIf { it.isNotBlank() }?.let { Line("Email", it) }
            }
        },
        confirmButton = {
            val phone = p?.phone
            if (!phone.isNullOrBlank()) TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
            }) { Text("Call") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
