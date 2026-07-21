package com.itcabs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.itcabs.core.designsystem.ItCabsTheme
import com.itcabs.feature.auth.AuthScreen
import com.itcabs.feature.dispatch.CreateJobScreen
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

@Composable
private fun ItCabsApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "auth") {
        composable("auth") {
            AuthScreen(
                onSignedIn = {
                    navController.navigate("home") {
                        popUpTo("auth") { inclusive = true }
                    }
                },
            )
        }
        // ponytail: a manual role hub until home routing keys off /auth/me (next slice).
        composable("home") { RoleHub(navController) }
        composable("feed") { DriverFeedScreen() }
        composable("create") { CreateJobScreen(onPublished = { navController.popBackStack() }) }
    }
}

@Composable
private fun RoleHub(navController: NavController) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Signed in", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = { navController.navigate("feed") }, modifier = Modifier.fillMaxWidth()) {
            Text("Driver: Browse & Claim Trips")
        }
        OutlinedButton(onClick = { navController.navigate("create") }, modifier = Modifier.fillMaxWidth()) {
            Text("Coordinator: Create Job")
        }
    }
}
