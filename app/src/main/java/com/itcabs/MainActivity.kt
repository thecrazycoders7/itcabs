package com.itcabs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.itcabs.core.designsystem.ItCabsTheme
import com.itcabs.domain.model.UserRole
import com.itcabs.feature.auth.AuthScreen
import com.itcabs.feature.dispatch.CoordinatorHomeScreen
import com.itcabs.feature.dispatch.DriverFeedScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ItCabsTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ItCabsApp()
                }
            }
        }
    }
}

/**
 * Top-level routing driven by [RootViewModel]: a loading gate while the persisted session is
 * checked, then either the auth flow or the role's home. No manual role hub — the role comes
 * from the backend (verify response on sign-in, /auth/me on cold start).
 */
@Composable
private fun ItCabsApp(root: RootViewModel = hiltViewModel()) {
    val state by root.state.collectAsState()
    when (val s = state) {
        RootState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        RootState.SignedOut -> AuthScreen(onSignedIn = { role -> root.onSignedIn(role) })
        is RootState.SignedIn -> RoleHome(role = s.role, onSignOut = { root.signOut() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleHome(role: UserRole, onSignOut: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (role == UserRole.DRIVER) "Available Trips" else "IT Cars Dispatch") },
                actions = { TextButton(onClick = onSignOut) { Text("Sign out") } },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (role) {
                UserRole.DRIVER -> DriverFeedScreen()
                UserRole.COORDINATOR -> CoordinatorHomeScreen()
            }
        }
    }
}
