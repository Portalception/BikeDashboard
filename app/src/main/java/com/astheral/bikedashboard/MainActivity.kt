package com.astheral.bikedashboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.astheral.bikedashboard.data.Settings
import com.astheral.bikedashboard.reminders.Reminders
import com.astheral.bikedashboard.ui.BikeDashboardApp
import com.astheral.bikedashboard.ui.theme.BikeDashboardTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Set when a service reminder was tapped: the bike whose servicing page to open. */
    private val openServicing = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = Settings.get(this)
        Reminders.schedule(this)
        if (savedInstanceState == null) readIntent(intent)
        setContent {
            val appearance by settings.appearance.collectAsState()
            val servicing by openServicing.collectAsState()
            BikeDashboardTheme(appearance) {
                BikeDashboardApp(openServicingFor = servicing, onOpenedServicing = { openServicing.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        val id = intent?.getLongExtra(Reminders.EXTRA_OPEN_SERVICING, -1L) ?: -1L
        if (id > 0) openServicing.value = id
    }
}
