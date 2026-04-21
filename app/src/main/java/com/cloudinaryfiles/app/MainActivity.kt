package com.cloudinaryfiles.app

import android.content.Intent
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

    private val LOG_TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.i(LOG_TAG, "─── onCreate ───────────────────────────────────────────────")
        AppLogger.i(LOG_TAG, "savedInstanceState = ${if (savedInstanceState == null) "null (fresh start)" else "non-null (restored)"}")
        AppLogger.i(LOG_TAG, "Intent action  : ${intent?.action}")
        AppLogger.i(LOG_TAG, "Intent data    : ${intent?.data}")
        AppLogger.d(LOG_TAG, "Log files are at:")
        AppLogger.d(LOG_TAG, "  INTERNAL : ${AppLogger.getInternalLogDir()?.absolutePath}")
        AppLogger.d(LOG_TAG, "  EXTERNAL : ${AppLogger.getExternalLogDir()?.absolutePath}")
        AppLogger.d(LOG_TAG, "Pull logs via adb:")
        AppLogger.d(LOG_TAG, "  adb pull /data/data/com.cloudinaryfiles.app/files/cloudvault_logs/")

        enableEdgeToEdge()

        val prefs = UserPreferences(this)

        AppLogger.d(LOG_TAG, "Checking active account to determine start destination…")
        val startDest = runBlocking {
            val account = prefs.activeAccount.first()
            AppLogger.i(LOG_TAG, "Active account check → ${
                if (account == null) "null → navigating to 'setup'"
                else "found: id=${account.id}, provider=${account.providerKey}, name='${account.name}' → navigating to 'files'"
            }")
            if (account != null) "files" else "setup"
        }
        AppLogger.i(LOG_TAG, "startDestination = '$startDest'")

        setContent {
            CloudinaryFilesTheme {
                Box(Modifier.fillMaxSize().background(SurfaceDark)) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = startDest) {
                        composable("setup") {
                            AppLogger.d(LOG_TAG, "Composing 'setup' screen")
                            SetupScreen(
                                onNavigateToFiles = {
                                    AppLogger.i(LOG_TAG, "SetupScreen → onNavigateToFiles")
                                    navController.navigate("files") {
                                        popUpTo("setup") { inclusive = true }
                                    }
                                },
                                addMode = false
                            )
                        }
                        composable("add_account") {
                            AppLogger.d(LOG_TAG, "Composing 'add_account' screen")
                            SetupScreen(
                                onNavigateToFiles = {
                                    AppLogger.i(LOG_TAG, "add_account SetupScreen → onNavigateToFiles")
                                    navController.popBackStack()
                                },
                                addMode = true
                            )
                        }
                        composable("files") {
                            AppLogger.d(LOG_TAG, "Composing 'files' screen")
                            FilesScreen(
                                onNavigateToSetup = {
                                    AppLogger.i(LOG_TAG, "FilesScreen → onNavigateToSetup")
                                    navController.navigate("setup") {
                                        popUpTo("files") { inclusive = true }
                                    }
                                },
                                onAddAccount = {
                                    AppLogger.i(LOG_TAG, "FilesScreen → onAddAccount")
                                    navController.navigate("add_account")
                                },
                                onEditAccount = { accountId ->
                                    AppLogger.i(LOG_TAG, "FilesScreen → onEditAccount(accountId=$accountId)")
                                    navController.navigate("edit_account/$accountId")
                                }
                            )
                        }
                        composable("edit_account/{accountId}") { backStackEntry ->
                            val accountId = backStackEntry.arguments?.getString("accountId") ?: ""
                            AppLogger.d(LOG_TAG, "Composing 'edit_account' screen for accountId=$accountId")
                            SetupScreen(
                                onNavigateToFiles = { navController.popBackStack() },
                                addMode = true,
                                editAccountId = accountId
                            )
                        }
                    }
                }
            }
        }

        AppLogger.i(LOG_TAG, "onCreate complete")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        AppLogger.i(LOG_TAG, "onNewIntent: action=${intent.action}, data=${intent.data}")
    }

    override fun onStart()   { super.onStart();  AppLogger.d(LOG_TAG, "onStart") }
    override fun onResume()  { super.onResume(); AppLogger.d(LOG_TAG, "onResume") }
    override fun onPause()   { super.onPause();  AppLogger.d(LOG_TAG, "onPause") }
    override fun onStop()    { super.onStop();   AppLogger.d(LOG_TAG, "onStop") }
    override fun onDestroy() { super.onDestroy(); AppLogger.i(LOG_TAG, "onDestroy") }
}
