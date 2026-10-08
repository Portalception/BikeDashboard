package com.astheral.bikedashboard

import android.app.ActivityManager
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
import com.astheral.bikedashboard.ui.theme.AppIcon
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

    // A new app icon is applied once you've left the app (Home, the app switcher or backing out):
    // switching it while the app is open makes Android close it. Opening the file picker also
    // counts as leaving, but the picker sits on top of the app, so it's skipped then — otherwise
    // the picker would be closed along with the app.
    private var userLeft = false

    override fun onResume() {
        super.onResume()
        userLeft = false
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        userLeft = true
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing || (userLeft && !coveredByAnotherApp())) AppIcon.apply(this, Settings.get(this).appIcon.value)
    }

    /** Whether another app's screen (like the file picker) is on top of ours in this task. */
    private fun coveredByAnotherApp(): Boolean {
        val top = getSystemService(ActivityManager::class.java)?.appTasks?.firstOrNull()?.taskInfo?.topActivity
        return top != null && top.packageName != packageName
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
