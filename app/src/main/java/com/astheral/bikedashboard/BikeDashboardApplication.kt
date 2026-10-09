package com.astheral.bikedashboard

import android.app.Application
import com.astheral.bikedashboard.diagnostics.CrashLogs

/** Starts the crash logger before anything else runs, so even a crash at start-up is saved. */
class BikeDashboardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogs.install(this)
    }
}
