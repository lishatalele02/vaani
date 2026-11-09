package com.samajhsakhi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.samajhsakhi.app.ui.AppViewModel
import com.samajhsakhi.app.ui.LocalSnack
import com.samajhsakhi.app.ui.SamajhTheme
import com.samajhsakhi.app.ui.screens.CaregiverScreen
import com.samajhsakhi.app.ui.screens.EmergencyScreen
import com.samajhsakhi.app.ui.screens.HelpScreen
import com.samajhsakhi.app.ui.screens.HistoryScreen
import com.samajhsakhi.app.ui.screens.HomeScreen
import com.samajhsakhi.app.ui.screens.OnboardingScreen
import com.samajhsakhi.app.ui.screens.PhrasesScreen
import com.samajhsakhi.app.ui.screens.QuickScreen
import com.samajhsakhi.app.ui.screens.SettingsScreen
import com.samajhsakhi.app.ui.screens.SpeakScreen
import com.samajhsakhi.app.ui.screens.TypeSpeakScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val s by vm.settings.collectAsStateWithLifecycle()
            SamajhTheme(highContrast = s?.highContrast == true) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(vm)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { snack.showSnackbar(it) }
    }

    val s = settings
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val nav = rememberNavController()
    val start = if (s.onboarded) "home" else "onboarding"

    CompositionLocalProvider(LocalSnack provides snack) {
        NavHost(navController = nav, startDestination = start) {
            composable("onboarding") {
                OnboardingScreen(vm) {
                    nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                }
            }
            composable("home") { HomeScreen(vm) { route -> nav.navigate(route) } }
            composable("speak") { SpeakScreen(vm, onBack = { nav.popBackStack() }, onQuick = { nav.navigate("quick") }) }
            composable("quick") { QuickScreen(vm) { nav.popBackStack() } }
            composable("emergency") { EmergencyScreen(vm, onBack = { nav.popBackStack() }, onSettings = { nav.navigate("settings") }) }
            composable("type") { TypeSpeakScreen(vm) { nav.popBackStack() } }
            composable("phrases") { PhrasesScreen(vm) { nav.popBackStack() } }
            composable("history") { HistoryScreen(vm) { nav.popBackStack() } }
            composable("caregiver") { CaregiverScreen(vm) { nav.popBackStack() } }
            composable("help") { HelpScreen { nav.popBackStack() } }
            composable("settings") {
                SettingsScreen(
                    vm, onBack = { nav.popBackStack() },
                    onCaregiver = { nav.navigate("caregiver") },
                    onHelp = { nav.navigate("help") },
                    onReset = { nav.navigate("onboarding") { popUpTo(0) { inclusive = true } } },
                )
            }
        }
    }
}
