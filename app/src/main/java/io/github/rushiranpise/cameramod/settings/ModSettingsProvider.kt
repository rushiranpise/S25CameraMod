package io.github.rushiranpise.cameramod.settings

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.util.Log

/**
 * Bridge between the camera process and the module's settings.
 *
 * `get_all` is read-only and returns the curated toggles, zoom ceiling/presets, and the raw
 * feature-flag override JSON. `report_stock_flags` is the one write path: it accepts the camera's
 * own support-flag snapshot, but only from Camera's UID. The camera process caches `get_all` once
 * per launch, so changing a toggle requires force-stopping Camera.
 */
class ModSettingsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        return when (method) {
            METHOD_GET_ALL -> Bundle().apply {
                ModSettings.snapshot(context).forEach { (key, enabled) ->
                    putBoolean(key, enabled)
                }
                putFloat(
                    ModSettings.ZOOM_CEILING_KEY,
                    ModSettings.zoomCeiling(context),
                )
                putString(
                    ModSettings.ZOOM_PRESETS_KEY,
                    ModSettings.zoomPresets(context).joinToString(","),
                )
                putString(
                    ModSettings.FLAG_OVERRIDES_KEY,
                    ModSettings.flagOverridesJson(context),
                )
            }

            METHOD_REPORT_STOCK_FLAGS -> reportStockFlags(extras)
            else -> null
        }
    }

    private fun reportStockFlags(extras: Bundle?): Bundle? {
        val context = context ?: return null
        if (!isCameraCaller(context)) {
            Log.w(TAG, "Rejected flag report from uid ${Binder.getCallingUid()}")
            return null
        }
        val raw = extras?.getString(EXTRA_FLAGS_JSON)
        if (raw.isNullOrBlank() || raw.length > MAX_REPORT_CHARS) {
            Log.w(TAG, "Rejected empty or oversized flag report")
            return null
        }
        val saved = ModSettings.saveStockFlagSnapshot(context, raw)
        return Bundle().apply { putBoolean(EXTRA_OK, saved) }
    }

    private fun isCameraCaller(context: android.content.Context): Boolean = try {
        val cameraUid = context.packageManager.getPackageUid(CAMERA_PACKAGE, 0)
        Binder.getCallingUid() == cameraUid
    } catch (t: Throwable) {
        Log.w(TAG, "Camera UID lookup failed: ${t.message}")
        false
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        const val METHOD_GET_ALL = "get_all"
        const val METHOD_REPORT_STOCK_FLAGS = "report_stock_flags"
        const val EXTRA_FLAGS_JSON = "flags_json"
        const val EXTRA_OK = "ok"

        private const val CAMERA_PACKAGE = "com.sec.android.app.camera"
        private const val MAX_REPORT_CHARS = 512 * 1024
        private const val TAG = "S25CameraMod"
    }
}
