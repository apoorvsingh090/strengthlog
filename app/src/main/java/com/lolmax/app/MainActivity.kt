package com.lolmax.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.lolmax.app.di.AppViewModelFactory
import com.lolmax.app.ui.navigation.LolMaxNavGraph
import com.lolmax.app.ui.navigation.rememberLolMaxAppState
import com.lolmax.app.ui.profile.SettingsViewModel
import com.lolmax.app.ui.theme.LolMaxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = LocalContext.current.applicationContext as LolMaxApp
            val settingsVm: SettingsViewModel = viewModel(
                factory = AppViewModelFactory(app.container)
            )
            val themeMode by settingsVm.themeMode.collectAsState()
            LolMaxTheme(themeMode = themeMode) {
                LolMaxRoot()
            }
        }
    }
}

@Composable
fun LolMaxRoot(navController: NavHostController = rememberNavController()) {
    val appState = rememberLolMaxAppState(navController)
    LolMaxNavGraph(appState = appState)
}
