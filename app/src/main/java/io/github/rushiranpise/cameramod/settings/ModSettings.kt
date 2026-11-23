package io.github.rushiranpise.cameramod.settings

import android.content.Context
import kotlin.math.roundToInt

/**
 * Module-side persistence. The camera process never reads this file directly; it asks
 * [ModSettingsProvider] for a snapshot so the module's private storage stays private.
 */
object ModSettings {

    const val AUTHORITY = "io.github.rushiranpise.cameramod.settings"
    const val PREFS_NAME = "flags"

    const val ZOOM_CEILING_KEY = "zoom_ceiling"
    const val DEFAULT_ZOOM_CEILING = 100f
    const val MIN_ZOOM_CEILING = 100f
    const val MAX_ZOOM_CEILING = 999.9f

    const val ZOOM_PRESETS_KEY = "zoom_presets"

    /**
     * 25x is the stock video shortcut that the camera's own switch drops as soon as the max zoom
     * moves past 25000. Keeping it in the default list restores it; 100 is the module default.
     */
    val DEFAULT_ZOOM_PRESETS = listOf(25, 100)
    const val MIN_ZOOM_PRESET = 1
    const val MAX_ZOOM_PRESET = 999

    /**
     * The stock list already supplies the 0.5x-10x chips. Seven high-zoom constants
     * (12/15/20/25/30/50/100) are available as extra chips or placeholders.
     */
    const val MAX_ZOOM_PRESETS = 7

    fun isEnabled(context: Context, feature: ModFeature): Boolean =
        prefs(context).getBoolean(feature.key, feature.defaultEnabled)

    fun setEnabled(context: Context, feature: ModFeature, enabled: Boolean) {
        prefs(context).edit().putBoolean(feature.key, enabled).apply()
    }

    fun zoomCeiling(context: Context): Float =
        prefs(context).getFloat(ZOOM_CEILING_KEY, DEFAULT_ZOOM_CEILING)
            .coerceIn(MIN_ZOOM_CEILING, MAX_ZOOM_CEILING)

    fun setZoomCeiling(context: Context, value: Float) {
        val rounded = (value * 10f).roundToInt() / 10f
        prefs(context).edit()
            .putFloat(ZOOM_CEILING_KEY, rounded.coerceIn(MIN_ZOOM_CEILING, MAX_ZOOM_CEILING))
            .apply()
    }

    fun zoomPresets(context: Context): List<Int> {
        val current = prefs(context)
        if (!current.contains(ZOOM_PRESETS_KEY)) return DEFAULT_ZOOM_PRESETS
        return sanitizeZoomPresets(current.getString(ZOOM_PRESETS_KEY, null))
    }

    fun setZoomPresets(context: Context, values: List<Int>) {
        prefs(context).edit()
            .putString(ZOOM_PRESETS_KEY, sanitizeZoomPresets(values).joinToString(","))
            .apply()
    }

    /**
     * Presets are whole zoom levels. Fractional live zoom is still supported by the ceiling; the
     * shortcut chips use the stock integer formatter. Duplicates are collapsed and the list is
     * always ascending.
     */
    fun sanitizeZoomPresets(values: List<Int>): List<Int> =
        values.asSequence()
            .filter { it in MIN_ZOOM_PRESET..MAX_ZOOM_PRESET }
            .distinct()
            .sorted()
            .take(MAX_ZOOM_PRESETS)
            .toList()

    fun sanitizeZoomPresets(raw: String?): List<Int> =
        if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            sanitizeZoomPresets(
                raw.split(',').mapNotNull { it.trim().toIntOrNull() },
            )
        }

    fun snapshot(context: Context): Map<String, Boolean> =
        ModFeature.entries.associate { it.key to isEnabled(context, it) }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
