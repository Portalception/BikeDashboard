package com.astheral.bikedashboard.ui.summary

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** A navigation app to go back to after filling up, and the button text for it. */
data class NavigationApp(val label: String, val intent: Intent)

private const val WAZE = "com.waze"
private const val GOOGLE_MAPS = "com.google.android.apps.maps"

/**
 * Waze if it's installed, else Google Maps, else any app that opens map locations, or null if
 * there's none. Opening Waze or Maps from their launcher entry brings back a route in progress.
 * (Android 11+ only lets the app see these thanks to the `<queries>` in the manifest.)
 */
fun findNavigationApp(context: Context): NavigationApp? {
    val pm = context.packageManager
    pm.getLaunchIntentForPackage(WAZE)?.let { return NavigationApp("Back to Waze", it) }
    pm.getLaunchIntentForPackage(GOOGLE_MAPS)?.let { return NavigationApp("Back to Maps", it) }
    val anyMap = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
    return if (anyMap.resolveActivity(pm) != null) NavigationApp("Open Navigation", anyMap) else null
}

/** Opens [app]; returns false if it couldn't be opened after all (e.g. it was just uninstalled). */
fun openNavigationApp(context: Context, app: NavigationApp): Boolean = try {
    context.startActivity(app.intent)
    true
} catch (_: ActivityNotFoundException) {
    false
}
