package com.astheral.bikedashboard.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Color

/**
 * The launcher icons: a fuel light on a coloured background. Monochrome matches the app's default
 * look, Fuel light is the amber warning light, and the rest follow the colour presets. Each is an
 * activity alias in the manifest; only the chosen one is switched on. Colours here match the
 * generated drawables (res/drawable/ic_launcher_bg_* and _fg_*).
 */
enum class AppIcon(val label: String, val alias: String, val top: Color, val bottom: Color, val glyph: Color) {
    MONOCHROME("Monochrome", "IconMonochrome", Color(0xFF2E2E2E), Color(0xFF121212), Color(0xFFE0E0E0)),
    FUEL_LIGHT("Fuel light", "IconFuelLight", Color(0xFF1A1A1A), Color(0xFF050505), Color(0xFFFFB300)),
    YAMAHA("Yamaha", "IconYamaha", Color(0xFF2A55C4), Color(0xFF132C6E), Color(0xFF00B0FF)),
    HONDA("Honda", "IconHonda", Color(0xFFE01E1E), Color(0xFF8E0000), Color(0xFFFFFFFF)),
    KAWASAKI("Kawasaki", "IconKawasaki", Color(0xFF6CC734), Color(0xFF3D8A17), Color(0xFF1E1E1E)),
    SUZUKI("Suzuki", "IconSuzuki", Color(0xFF2A6BC0), Color(0xFF123F7A), Color(0xFFCFD8DC)),
    DUCATI("Ducati", "IconDucati", Color(0xFFD9213C), Color(0xFF8C0A1E), Color(0xFFF2F2F2)),
    BMW("BMW", "IconBmw", Color(0xFF2B7BE6), Color(0xFF1450A8), Color(0xFFFFFFFF)),
    KTM("KTM", "IconKtm", Color(0xFFFF7A1A), Color(0xFFD45200), Color(0xFF1E272C)),
    APRILIA("Aprilia", "IconAprilia", Color(0xFF2B2B2B), Color(0xFF0E0E0E), Color(0xFFE8202A)),
    MV_AGUSTA("MV Agusta", "IconMvAgusta", Color(0xFFC62828), Color(0xFF7A1111), Color(0xFFD6D6D6)),
    TRIUMPH("Triumph", "IconTriumph", Color(0xFF333333), Color(0xFF141414), Color(0xFFFFC72C)),
    ;

    companion object {
        /** Switches the launcher to [icon]. The home screen may take a moment to update. */
        fun apply(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            fun component(i: AppIcon) = ComponentName(context.packageName, "${context.packageName}.${i.alias}")
            // Turn the new one on before the others off, so there's always an icon to open the app.
            pm.setComponentEnabledSetting(component(icon), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            entries.filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(component(it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
