package com.astheral.bikedashboard.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import com.astheral.bikedashboard.R

/**
 * The launcher icons. Each is an activity alias in the manifest; only the chosen one is switched
 * on. [background] and [foreground] are the adaptive icon's layers, used for the previews.
 */
enum class AppIcon(val label: String, val alias: String, @DrawableRes val background: Int, @DrawableRes val foreground: Int) {
    MONOCHROME("Classic", "IconMonochrome", R.drawable.ic_launcher_bg_monochrome, R.drawable.ic_launcher_fg_monochrome),
    FUEL_LIGHT("Fuel Light", "IconFuelLight", R.drawable.ic_launcher_bg_fuel_light, R.drawable.ic_launcher_fg_fuel_light),
    MODERN("Modern", "IconModern", R.drawable.ic_launcher_bg_modern, R.drawable.ic_launcher_fg_modern),
    MODERN_YELLOW("Modern Yellow", "IconModernYellow", R.drawable.ic_launcher_bg_modern_yellow, R.drawable.ic_launcher_fg_modern_yellow),
    SPEEDO("Speedo", "IconSpeedo", R.drawable.ic_launcher_bg_speedo, R.drawable.ic_launcher_fg_speedo),
    SPEEDO_YELLOW("Speedo Yellow", "IconSpeedoYellow", R.drawable.ic_launcher_bg_speedo_yellow, R.drawable.ic_launcher_fg_speedo_yellow),
    MINIMAL("Minimal", "IconMinimal", R.drawable.ic_launcher_bg_minimal, R.drawable.ic_launcher_fg_minimal),
    MINIMAL_YELLOW("Minimal Yellow", "IconMinimalYellow", R.drawable.ic_launcher_bg_minimal_yellow, R.drawable.ic_launcher_fg_minimal_yellow),
    ;

    companion object {
        /** Aliases of the old brand-colour icons, still in the manifest so nobody is left without one. */
        private val RETIRED = listOf(
            "IconYamaha", "IconHonda", "IconKawasaki", "IconSuzuki", "IconDucati",
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
            // Monochrome is the one switched on in the manifest.
            fun isOn(i: AppIcon) = when (pm.getComponentEnabledSetting(component(i))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> i == MONOCHROME
                else -> false
            }
            // The retired brand-colour icons: switched off whenever one is still on.
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
