package com.itcabs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.itcabs.core.designsystem.ItCabsTheme
import com.itcabs.feature.auth.AuthScreen
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
        // ponytail: placeholder until the role-based home features (driver/coordinator) are built.
        composable("home") { HomePlaceholder() }
    }
}

@Composable
private fun HomePlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Signed in", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Home feed comes next (driver_home / coordinator_dashboard).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
