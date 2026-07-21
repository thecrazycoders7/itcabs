package com.itcabs

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.itcabs.ui.*
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = (application as ItCabsApp).repo
        setContent {
            MaterialTheme {
                Surface { AppRoot(repo, this) }
            }
        }
    }
}

@Composable
fun AppRoot(repo: Repo, activity: Activity) {
    val vm: AppViewModel = viewModel(factory = AppViewModel.factory(repo))
    val profile by vm.profile.collectAsState()
    val signedIn by vm.signedIn.collectAsState()

    when {
        !signedIn -> PhoneAuthScreen(
            onSendOtp = { phone, cb -> sendOtp(activity, repo, phone, cb) },
            onVerify = { id, code, onErr -> verifyOtp(repo, id, code, onErr) },
        )
        profile == null -> RoleSetupScreen(vm)          // first sign-in: pick role / fill profile
        else -> HomeScreen(vm, profile!!)
    }
}

/**
 * Fires Firebase phone verification. On auto-retrieval or manual code entry it
 * signs the user in; the auth-state listener in AppViewModel then advances the UI.
 * cb reports (verificationId?, error?) — verificationId present means "enter OTP".
 */
private fun sendOtp(
    activity: Activity,
    repo: Repo,
    phone: String,
    cb: (String?, String?) -> Unit,
) {
    val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(cred: PhoneAuthCredential) {
            repo.auth.signInWithCredential(cred)
        }
        override fun onVerificationFailed(e: FirebaseException) {
            cb(null, e.message ?: "Verification failed")
        }
        override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
            cb(id, null)
        }
    }
    val options = PhoneAuthOptions.newBuilder(repo.auth)
        .setPhoneNumber(phone)
        .setTimeout(60L, TimeUnit.SECONDS)
        .setActivity(activity)
        .setCallbacks(callbacks)
        .build()
    PhoneAuthProvider.verifyPhoneNumber(options)
}

/** Confirm a manually entered OTP. Exposed for the OTP screen. */
fun verifyOtp(repo: Repo, verificationId: String, code: String, onError: (String) -> Unit) {
    val cred = PhoneAuthProvider.getCredential(verificationId, code)
    repo.auth.signInWithCredential(cred).addOnFailureListener { onError(it.message ?: "Invalid code") }
}
