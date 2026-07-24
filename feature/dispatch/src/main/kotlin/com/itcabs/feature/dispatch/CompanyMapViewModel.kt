package com.itcabs.feature.dispatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itcabs.domain.AppResult
import com.itcabs.domain.repository.CompanyJobRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Polls the claimed driver's live location for a company job (coordinator route map). */
@HiltViewModel
class CompanyMapViewModel @Inject constructor(private val repo: CompanyJobRepository) : ViewModel() {
    private val _driver = MutableStateFlow<LatLngUi?>(null)
    val driver: StateFlow<LatLngUi?> = _driver.asStateFlow()
    private var started = false

    fun start(jobId: Long) {
        if (started) return
        started = true
        viewModelScope.launch {
            while (true) {
                (repo.driverLocation(jobId) as? AppResult.Ok)?.value?.let { loc ->
                    val lat = loc.lat; val lng = loc.lng
                    if (lat != null && lng != null) _driver.value = LatLngUi(lat, lng)
                }
                delay(5_000)
            }
        }
    }
}
