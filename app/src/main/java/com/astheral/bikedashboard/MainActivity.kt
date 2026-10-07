package com.astheral.bikedashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.astheral.bikedashboard.data.Settings
import com.astheral.bikedashboard.ui.BikeDashboardApp
import com.astheral.bikedashboard.ui.theme.BikeDashboardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = Settings.get(this)
        setContent {
            val appearance by settings.appearance.collectAsState()
            BikeDashboardTheme(appearance) {
                BikeDashboardApp()
            }
        }
    }
}
