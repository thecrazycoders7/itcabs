package com.itcabs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itcabs.core.designsystem.ItCabsTheme
import com.itcabs.domain.model.UserRole
import com.itcabs.feature.auth.AuthScreen
import com.itcabs.feature.auth.ProfileScreen
import com.itcabs.feature.dispatch.AdminScreen
import com.itcabs.feature.dispatch.CompanyJobsScreen
import com.itcabs.feature.dispatch.CoordinatorHomeScreen
import com.itcabs.feature.dispatch.DriverCompanyScreen
import com.itcabs.feature.dispatch.DriverFeedScreen
import com.itcabs.feature.dispatch.MyTripsScreen
import com.itcabs.feature.dispatch.StatsScreen
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
    // Gentle fade between loading / auth / home so the session gate doesn't snap.
    Crossfade(targetState = state, animationSpec = tween(250), label = "root") { s ->
        when (s) {
            RootState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            RootState.SignedOut -> AuthScreen(
                webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
                onSignedIn = { role -> root.onSignedIn(role) },
            )
            is RootState.SignedIn -> RoleHome(role = s.role, isAdmin = s.isAdmin, onSignOut = { root.signOut() })
        }
    }
}

/** Brand mark + title for the top bars: the itcabs logo beside the screen name. */
@Composable
private fun LogoTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.itcabs_logo),
            contentDescription = null,
            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Text(text)
    }
}

@Composable
private fun RoleHome(role: UserRole, isAdmin: Boolean, onSignOut: () -> Unit) {
    when (role) {
        UserRole.DRIVER -> DriverHome(onSignOut)
        UserRole.COORDINATOR -> CoordinatorHome(isAdmin, onSignOut)
    }
}

/** Coordinator: My Jobs + Insights, plus an Admin tab (approve drivers) for is_admin users. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoordinatorHome(isAdmin: Boolean, onSignOut: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val title = when (tab) {
        0 -> "IT Cars Dispatch"
        1 -> "Company Jobs"
        2 -> "Insights"
        else -> "Admin"
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { LogoTitle(title) },
                actions = { TextButton(onClick = onSignOut) { Text("Sign out") } },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("Legs") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Business, contentDescription = null) },
                    label = { Text("Company") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.BarChart, contentDescription = null) },
                    label = { Text("Insights") },
                )
                if (isAdmin) NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    label = { Text("Admin") },
                )
            }
        },
    ) { padding ->
        Crossfade(targetState = tab, animationSpec = tween(200), modifier = Modifier.padding(padding), label = "coordTab") { t ->
            when (t) {
                0 -> CoordinatorHomeScreen()
                1 -> CompanyJobsScreen()
                2 -> StatsScreen()
                else -> AdminScreen()
            }
        }
    }
}

/** Driver home: two tabs — the open-leg feed and the driver's own claimed trips. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriverHome(onSignOut: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val title = when (tab) {
        0 -> "Available Trips"
        1 -> "My Trips"
        2 -> "Company Trips"
        else -> "Profile"
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { LogoTitle(title) },
                actions = { TextButton(onClick = onSignOut) { Text("Sign out") } },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("Available") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.DirectionsCar, contentDescription = null) },
                    label = { Text("My Trips") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Business, contentDescription = null) },
                    label = { Text("Company") },
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    label = { Text("Profile") },
                )
            }
        },
    ) { padding ->
        Crossfade(targetState = tab, animationSpec = tween(200), modifier = Modifier.padding(padding), label = "driverTab") { t ->
            when (t) {
                0 -> DriverFeedScreen()
                1 -> MyTripsScreen()
                2 -> DriverCompanyScreen()
                else -> ProfileScreen(onSignOut = onSignOut)
            }
        }
    }
}

