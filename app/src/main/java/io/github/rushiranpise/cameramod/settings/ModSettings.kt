package io.github.rushiranpise.cameramod.settings

import android.content.Context
import org.json.JSONObject
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

    const val STOCK_FLAGS_KEY = "stock_flags_json"
    const val FLAG_OVERRIDES_KEY = "flag_overrides_json"
    const val FLAG_MIGRATION_KEY = "flag_migration_v2"

    data class StockFlagSnapshot(
        val cameraVersion: String?,
        val capturedAt: Long,
        val flags: Map<String, Boolean>,
    )

    /**
     * Old support-flag toggles mapped to the raw e5.d constants they used to force on. The module
     * app migrates each explicitly enabled legacy toggle into the generic override store so the
     * 1.1.0 flag explorer and the old preferences stay in sync.
     */
    private val legacyFlagMap = mapOf(
        "dual_recording_v2" to listOf("SUPPORT_DUAL_RECORDING_IN_VIDEO_MODE"),
        "portrait_video_4k" to listOf("SUPPORT_VIDEO_BOKEH_UHD"),
        "tele_120fps" to listOf(
            "SUPPORT_PRO_VIDEO_TELE_LENS_120FPS",
            "SUPPORT_PRO_VIDEO_SECOND_TELE_LENS_120FPS",
        ),
        "hdr_8k" to listOf("SUPPORT_HDR_VIDEOS_MENU"),
        "apv_recording" to listOf("SUPPORT_APV"),
        "natural_blur" to listOf("SUPPORT_VIDEO_BOKEH_NATURAL_BLUR"),
        "portrait_filter" to listOf("SUPPORT_PORTRAIT_FILTER"),
        "detail_enhancer_button" to listOf("SUPPORT_DETAIL_ENHANCER_BUTTON"),
        "motion_photo_50mp" to listOf("SUPPORT_50MP_MOTION_PHOTO"),
        "motion_photo_24mp" to listOf("SUPPORT_24MP_MOTION_PHOTO"),
        "fusion_high_resolution" to listOf("SUPPORT_FUSION_HIGH_RESOLUTION"),
        "photo_max_night_shot" to listOf("SUPPORT_PHOTO_MAX_NIGHT_SHOT"),
        "ai_high_res_memory_check" to listOf("SUPPORT_AI_HIGH_RESOLUTION_MEMORY_CHECK"),
        "back_medium_resolution" to listOf("SUPPORT_BACK_MEDIUM_RESOLUTION"),
    )

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

    fun stockFlagSnapshot(context: Context): StockFlagSnapshot? {
        val raw = prefs(context).getString(STOCK_FLAGS_KEY, null) ?: return null
        return parseStockFlagSnapshot(raw)
    }

    /** Returns true only when the report contained at least one flag. */
    fun saveStockFlagSnapshot(context: Context, raw: String?): Boolean {
        val parsed = parseStockFlagSnapshot(raw) ?: return false
        if (parsed.flags.isEmpty()) return false
        prefs(context).edit().putString(STOCK_FLAGS_KEY, raw).apply()
        return true
    }

    fun parseStockFlagSnapshot(raw: String?): StockFlagSnapshot? = try {
        if (raw.isNullOrBlank()) {
            null
        } else {
            val root = JSONObject(raw)
            val flagsObject = root.optJSONObject("flags") ?: JSONObject()
            val flags = buildMap {
                flagsObject.keys().forEach { name ->
                    put(name, flagsObject.optBoolean(name, false))
                }
            }
            StockFlagSnapshot(
                cameraVersion = root.optString("cameraVersion", "")
                    .takeIf { it.isNotBlank() && it != "null" },
                capturedAt = root.optLong("capturedAt", 0L),
                flags = flags,
            )
        }
    } catch (t: Throwable) {
        null
    }

    fun flagOverrides(context: Context): Map<String, Boolean> {
        ensureLegacyFlagMigration(context)
        return parseFlagOverrides(prefs(context).getString(FLAG_OVERRIDES_KEY, null))
    }

    fun parseFlagOverrides(raw: String?): Map<String, Boolean> = try {
        if (raw.isNullOrBlank()) {
            emptyMap()
        } else {
            val root = JSONObject(raw)
            buildMap {
                root.keys().forEach { name -> put(name, root.optBoolean(name, false)) }
            }
        }
    } catch (t: Throwable) {
        emptyMap()
    }

    fun setFlagOverride(context: Context, name: String, enabled: Boolean?) {
        val current = parseFlagOverrides(
            prefs(context).getString(FLAG_OVERRIDES_KEY, null)
        ).toMutableMap()
        if (enabled == null) current.remove(name) else current[name] = enabled
        writeFlagOverrides(context, current)
    }

    fun clearFlagOverrides(context: Context) {
        prefs(context).edit().remove(FLAG_OVERRIDES_KEY).apply()
    }

    fun flagOverridesJson(context: Context): String {
        ensureLegacyFlagMigration(context)
        return prefs(context).getString(FLAG_OVERRIDES_KEY, null) ?: "{}"
    }

    fun writeFlagOverrides(context: Context, overrides: Map<String, Boolean>) {
        val json = JSONObject()
        overrides.forEach { (name, enabled) -> json.put(name, enabled) }
        prefs(context).edit().putString(FLAG_OVERRIDES_KEY, json.toString()).apply()
    }

    /**
     * Runs once after upgrading from a 1.0.x build. Legacy toggles were force-on only; migrate
     * each explicitly enabled toggle to a generic raw-flag override. Existing overrides win.
     */
    fun ensureLegacyFlagMigration(context: Context) {
        val current = prefs(context)
        if (current.getBoolean(FLAG_MIGRATION_KEY, false)) return
        val overrides = parseFlagOverrides(
            current.getString(FLAG_OVERRIDES_KEY, null)
        ).toMutableMap()
        legacyFlagMap.forEach { (legacyKey, flags) ->
            if (current.getBoolean(legacyKey, false)) {
                flags.forEach { flag ->
                    if (!overrides.containsKey(flag)) overrides[flag] = true
                }
            }
        }
        writeFlagOverrides(context, overrides)
        current.edit().putBoolean(FLAG_MIGRATION_KEY, true).apply()
    }

    fun snapshot(context: Context): Map<String, Boolean> {
        ensureLegacyFlagMigration(context)
        return ModFeature.entries.associate { it.key to isEnabled(context, it) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
