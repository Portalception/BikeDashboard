package com.astheral.bikedashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.astheral.bikedashboard.ui.importer.ImportScreen
import com.astheral.bikedashboard.ui.theme.BikeDashboardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BikeDashboardTheme {
                // Temporary: the import screen is the only screen until navigation is added (step 8).
                ImportScreen()
            }
        }
    }
}
