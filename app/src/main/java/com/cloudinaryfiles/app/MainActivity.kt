package com.cloudinaryfiles.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cloudinaryfiles.app.data.preferences.UserPreferences
import com.cloudinaryfiles.app.ui.screens.FilesScreen
import com.cloudinaryfiles.app.ui.screens.SetupScreen
import com.cloudinaryfiles.app.ui.theme.CloudinaryFilesTheme
import com.cloudinaryfiles.app.ui.theme.SurfaceDark
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = UserPreferences(this)
        // Determine start destination synchronously to skip login flash
        val startDest = runBlocking {
            if (prefs.credentials.first() != null) "files" else "setup"
        }

        setContent {
            CloudinaryFilesTheme {
                Box(Modifier.fillMaxSize().background(SurfaceDark)) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = startDest) {
                        composable("setup") {
                            SetupScreen(
                                onNavigateToFiles = {
                                    navController.navigate("files") {
                                        popUpTo("setup") { inclusive = true }
                                    }
                                },
                                addMode = false
                            )
                        }
                        // Route for adding another account from within the app
                        composable("add_account") {
                            SetupScreen(
                                onNavigateToFiles = {
                                    navController.popBackStack()
                                },
                                addMode = true
                            )
                        }
                        composable("files") {
                            FilesScreen(
                                onNavigateToSetup = {
                                    navController.navigate("setup") {
                                        popUpTo("files") { inclusive = true }
                                    }
                                },
                                onAddAccount = {
                                    navController.navigate("add_account")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
