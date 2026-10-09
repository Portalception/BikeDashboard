package com.astheral.bikedashboard.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import com.astheral.bikedashboard.R

/**
 * The launcher icons. Each is an activity alias in the manifest; only the chosen one is switched
 * on. [background] and [foreground] are the adaptive icon's layers, used for the previews. The
 * alias names are older than the icons' names, and stay as they are so Android keeps track of them.
 */
enum class AppIcon(val label: String, val alias: String, @DrawableRes val background: Int, @DrawableRes val foreground: Int) {
    CLASSIC("Classic", "IconModern", R.drawable.ic_launcher_bg_classic, R.drawable.ic_launcher_fg_classic),
    CLASSIC_YELLOW("Classic Yellow", "IconModernYellow", R.drawable.ic_launcher_bg_classic_yellow, R.drawable.ic_launcher_fg_classic_yellow),
    SPEEDO("Speedo", "IconSpeedo", R.drawable.ic_launcher_bg_speedo, R.drawable.ic_launcher_fg_speedo),
    SPEEDO_YELLOW("Speedo Yellow", "IconSpeedoYellow", R.drawable.ic_launcher_bg_speedo_yellow, R.drawable.ic_launcher_fg_speedo_yellow),

    /** The first icon, flat. Only offered once found with the secret taps on the About card. */
    LEGACY("Legacy", "IconMonochrome", R.drawable.ic_launcher_bg_legacy, R.drawable.ic_launcher_fg_legacy),
    ;

    companion object {
        /** Aliases of retired icons, still in the manifest so nobody is left without one. */
        private val RETIRED = listOf(
            "IconFuelLight", "IconMinimal", "IconMinimalYellow", "IconYamaha", "IconHonda", "IconKawasaki", "IconSuzuki", "IconDucati",
            "IconBmw", "IconKtm", "IconAprilia", "IconMvAgusta", "IconTriumph",
        )

        /**
         * Switches the launcher to [icon], doing nothing if it's already showing. Turning off the
         * entry the app was opened from makes Android close the app, so call this only once the
         * user has left it (see MainActivity). The home screen may take a moment to update.
         */
        fun apply(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            fun component(i: AppIcon) = ComponentName(context.packageName, "${context.packageName}.${i.alias}")
            // Classic is the one switched on in the manifest.
            fun isOn(i: AppIcon) = when (pm.getComponentEnabledSetting(component(i))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> i == CLASSIC
                else -> false
            }
            // Retired icons: switched off whenever one is still on.
            val retired = RETIRED.map { ComponentName(context.packageName, "${context.packageName}.$it") }
                .filter { pm.getComponentEnabledSetting(it) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }
            if (retired.isEmpty() && entries.all { isOn(it) == (it == icon) }) return
            // Turn the new one on before the others off, so there's always an icon to open the app.
            pm.setComponentEnabledSetting(component(icon), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            entries.filter { it != icon && isOn(it) }.forEach {
                pm.setComponentEnabledSetting(component(it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
            retired.forEach {
                pm.setComponentEnabledSetting(it, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
    }
}

/**
 * The saved icon, by name. Names from before the icons were reworked map to their nearest one now:
 * the old default, Modern, Minimal and the retired brand colours to Classic, and the yellow ones
 * and Fuel Light to Classic Yellow.
 */
fun appIconNamed(name: String?): AppIcon = when (name) {
    "MODERN_YELLOW", "MINIMAL_YELLOW", "FUEL_LIGHT" -> AppIcon.CLASSIC_YELLOW
    else -> AppIcon.entries.firstOrNull { it.name == name } ?: AppIcon.CLASSIC
}
