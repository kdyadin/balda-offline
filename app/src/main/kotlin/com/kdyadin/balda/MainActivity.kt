package com.kdyadin.balda

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.kdyadin.balda.data.ThemeMode
import com.kdyadin.balda.data.appContainer
import com.kdyadin.balda.ui.BaldaNavHost
import com.kdyadin.balda.ui.theme.BaldaTheme
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = appContainer.settings
        val themeFlow = settings.settings.map { it.themeMode }
        setContent {
            val themeMode by themeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            BaldaTheme(themeMode = themeMode) {
                BaldaNavHost()
            }
        }
    }
}
