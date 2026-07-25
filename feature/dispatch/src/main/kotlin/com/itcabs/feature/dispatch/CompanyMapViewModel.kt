package com.itcabs.feature.dispatch

import androidx.lifecycle.ViewModel
import com.itcabs.domain.AppResult
import com.itcabs.domain.repository.CompanyJobRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Polls the claimed driver's live location for a company job (coordinator route map). */
@HiltViewModel
class CompanyMapViewModel @Inject constructor(private val repo: CompanyJobRepository) : ViewModel() {
    private val _driver = MutableStateFlow<LatLngUi?>(null)
    val driver: StateFlow<LatLngUi?> = _driver.asStateFlow()

    /**
     * Polls the driver every 5s in the CALLER's coroutine (the screen's LaunchedEffect), so polling
     * stops when the map leaves the screen — this VM is Activity-scoped, so a self-launched loop
     * would poll for the whole session.
     */
    suspend fun start(jobId: Long) {
        while (true) {
            (repo.driverLocation(jobId) as? AppResult.Ok)?.value?.let { loc ->
                val lat = loc.lat; val lng = loc.lng
                if (lat != null && lng != null) _driver.value = LatLngUi(lat, lng)
            }
            delay(5_000)
        }
    }
}
