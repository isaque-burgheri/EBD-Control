package com.ebd.controle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ebd.controle.ui.SettingsViewModel
import com.ebd.controle.ui.nav.AppRoot
import com.ebd.controle.ui.theme.EBDTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsVm: SettingsViewModel = viewModel()
            val isDark by settingsVm.isDarkMode.collectAsState()

            EBDTheme(useDark = isDark) {
                AppRoot()
            }
        }
    }
}
