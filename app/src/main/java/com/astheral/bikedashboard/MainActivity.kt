package com.astheral.bikedashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.astheral.bikedashboard.ui.BikeDashboardApp
import com.astheral.bikedashboard.ui.theme.BikeDashboardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BikeDashboardTheme {
                BikeDashboardApp()
            }
        }
    }
}
