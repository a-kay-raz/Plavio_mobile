package com.pulsestream.app.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Step 70 — app-icon picker (scoped port of Nuvio's AppIconPicker).
 *
 * Three launcher-icon variants: the default icon, and the same foreground
 * on a solid black or solid white background. Each variant is an
 * activity-alias in the manifest (IconBlack / IconWhite) targeting
 * MainActivity; exactly one component is enabled at a time, so the
 * launcher shows the chosen icon. No restart is needed — the launcher
 * picks the change up on its own (it may take a moment).
 */
object AppIcon {
    const val DEFAULT = "default"
    const val BLACK = "black"
    const val WHITE = "white"

    val VARIANTS = listOf(DEFAULT, BLACK, WHITE)

    fun label(variant: String): String = when (variant) {
        BLACK -> "Black"
        WHITE -> "White"
        else -> "Default"
    }

    fun apply(context: Context, variant: String) {
        val pm = context.packageManager
        val pkg = context.packageName
        val components = mapOf(
            DEFAULT to ComponentName(pkg, "$pkg.MainActivity"),
            BLACK to ComponentName(pkg, "$pkg.IconBlack"),
            WHITE to ComponentName(pkg, "$pkg.IconWhite"),
        )
        components.forEach { (name, component) ->
            pm.setComponentEnabledSetting(
                component,
                if (name == variant) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
