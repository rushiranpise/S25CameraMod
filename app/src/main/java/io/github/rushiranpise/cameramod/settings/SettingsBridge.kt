package io.github.rushiranpise.cameramod.settings

import android.app.AndroidAppHelper
import android.net.Uri
import android.os.SystemClock
import io.github.rushiranpise.cameramod.CameraModHook

/**
 * Camera-process view of the toggle state.
 *
 * The first feature read performs one binder call to [ModSettingsProvider]. The result is kept
 * for the lifetime of the camera process because the camera app also computes its support maps
 * once at startup. If the provider cannot be reached (module app disabled, provider missing),
 * [ModFeature.defaultEnabled] and [ModSettings.DEFAULT_ZOOM_CEILING] win so stock behavior is
 * preserved.
 */
object SettingsBridge {

    private val URI = Uri.parse("content://${ModSettings.AUTHORITY}/")
    private const val RETRY_AFTER_MS = 10_000L

    @Volatile
    private var snapshot: Map<String, Boolean>? = null

    @Volatile
    private var ceiling: Float? = null

    @Volatile
    private var presets: List<Int>? = null

    @Volatile
    private var failedAt = 0L

    fun isEnabled(feature: ModFeature): Boolean {
        val loaded = snapshot() ?: return feature.defaultEnabled
        return loaded[feature.key] ?: feature.defaultEnabled
    }

    /** Cached with the toggles; defaults to 100x until the provider answers. */
    fun zoomCeiling(): Float {
        snapshot()
        return ceiling ?: ModSettings.DEFAULT_ZOOM_CEILING
    }

    /** Cached with the toggles; defaults to the stock 25x plus the module's 100x. */
    fun zoomPresets(): List<Int> {
        snapshot()
        return presets ?: ModSettings.DEFAULT_ZOOM_PRESETS
    }

    @Synchronized
    private fun snapshot(): Map<String, Boolean>? {
        snapshot?.let { return it }

        val context = AndroidAppHelper.currentApplication() ?: return null
        val now = SystemClock.elapsedRealtime()
        if (failedAt != 0L && now - failedAt < RETRY_AFTER_MS) return null

        return try {
            val bundle = context.contentResolver.call(
                URI,
                ModSettingsProvider.METHOD_GET_ALL,
                null,
                null,
            )
            if (bundle == null) {
                failedAt = now
                CameraModHook.log("Settings provider returned no data; using defaults")
                null
            } else {
                val loaded = ModFeature.entries.associate { feature ->
                    feature.key to bundle.getBoolean(feature.key, feature.defaultEnabled)
                }
                val loadedCeiling = bundle
                    .getFloat(ModSettings.ZOOM_CEILING_KEY, ModSettings.DEFAULT_ZOOM_CEILING)
                    .coerceIn(ModSettings.MIN_ZOOM_CEILING, ModSettings.MAX_ZOOM_CEILING)
                val loadedPresets = bundle.getString(ModSettings.ZOOM_PRESETS_KEY)
                    ?.let { ModSettings.sanitizeZoomPresets(it) }
                    ?: ModSettings.DEFAULT_ZOOM_PRESETS
                snapshot = loaded
                ceiling = loadedCeiling
                presets = loadedPresets
                CameraModHook.log(
                    "Toggles: " + loaded.entries.joinToString { "${it.key}=${it.value}" }
                )
                CameraModHook.log("Zoom ceiling: ${loadedCeiling}x")
                CameraModHook.log(
                    "Zoom presets: " +
                        loadedPresets.joinToString(",").ifEmpty { "(none)" }
                )
                loaded
            }
        } catch (t: Throwable) {
            failedAt = now
            CameraModHook.log(
                "Settings read failed (${t.javaClass.simpleName}: ${t.message}); using defaults"
            )
            null
        }
    }
}
