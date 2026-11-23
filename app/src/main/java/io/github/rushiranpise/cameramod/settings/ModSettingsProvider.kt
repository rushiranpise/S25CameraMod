package io.github.rushiranpise.cameramod.settings

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * Read-only bridge from the camera process to the module's toggle state.
 *
 * The provider is exported because Camera runs under a different UID; it exposes only the
 * toggle booleans and the zoom ceiling, and cannot be written through. The camera process reads
 * it once per launch and caches the snapshot, so changing a toggle or the ceiling requires
 * force-stopping Camera.
 */
class ModSettingsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != METHOD_GET_ALL) return null
        val context = context ?: return null

        return Bundle().apply {
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
        }
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
    }
}
