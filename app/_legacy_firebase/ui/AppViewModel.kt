package com.itcabs.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.itcabs.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppViewModel(val repo: Repo) : ViewModel() {

    /** Emits true whenever a Firebase user is present. */
    val signedIn: StateFlow<Boolean> = callbackFlow {
        val l = FirebaseAuth.AuthStateListener { trySend(it.currentUser != null) }
        repo.auth.addAuthStateListener(l)
        awaitClose { repo.auth.removeAuthStateListener(l) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, repo.uid != null)

    // Re-subscribe profile whenever auth state flips (uid changes).
    val profile: StateFlow<UserProfile?> = signedIn
        .flatMapLatest { if (it) repo.myProfile() else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun openLegs(area: String?, vehicle: String?) = repo.openLegs(area, vehicle)
    fun myLegs() = repo.myLegs()
    fun myClaims() = repo.myClaims()

    fun saveProfile(p: UserProfile, onDone: () -> Unit = {}) = viewModelScope.launch {
        repo.saveProfile(p); onDone()
    }

    fun postJob(job: Job, legs: List<Leg>, onDone: () -> Unit) = viewModelScope.launch {
        runCatching { repo.postJob(job, legs) }.onSuccess { onDone() }
    }

    fun claim(leg: Leg, driverName: String, onResult: (String?) -> Unit) = viewModelScope.launch {
        runCatching { repo.claimLeg(legPath(leg), driverName) }
            .fold({ onResult(null) }, { onResult(it.message ?: "Could not claim") })
    }

    fun advance(leg: Leg, status: LegStatus) = viewModelScope.launch {
        repo.setLegStatus(legPath(leg), status)
    }

    fun rate(leg: Leg, stars: Int) = viewModelScope.launch {
        repo.rateDriver(legPath(leg), leg.claimedBy, stars)
    }

    fun signOut() = repo.auth.signOut()

    companion object {
        fun legPath(leg: Leg) = "jobs/${leg.jobId}/legs/${leg.id}"

        fun factory(repo: Repo) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(repo) as T
        }
    }
}
