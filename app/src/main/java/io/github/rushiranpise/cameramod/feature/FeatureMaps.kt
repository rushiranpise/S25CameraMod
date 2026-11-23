package io.github.rushiranpise.cameramod.feature

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.rushiranpise.cameramod.CameraModHook
import io.github.rushiranpise.cameramod.settings.ModFeature
import io.github.rushiranpise.cameramod.settings.SettingsBridge
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Rewrites individual camera-feature maps after the app builds them.
 *
 * e5.g.n(e5.l) returns both shooting-mode maps and per-resolution feature maps. The camera app
 * reads these maps for mode visibility, stabilization availability and HDR capabilities. Only
 * the requested key is copied and changed; every other map is returned untouched.
 *
 * Dual Recording and 8K HDR are keyed to the same raw support-flag overrides shown in the flag
 * explorer, so the old curated switches and the new overrides cannot disagree.
 */
object FeatureMaps {

    private const val MAP_GETTER_CLASS = "e5.g"
    private const val MAP_GETTER_METHOD = "n"
    private const val MAP_PARAM_TYPE = "e5.l"

    private const val DUAL_RECORDING_V2_MAP = "SHOOTING_MODE_DUAL_RECORDING_V2"
    private const val DUAL_RECORDING_FLAG = "SUPPORT_DUAL_RECORDING_IN_VIDEO_MODE"
    private const val HIGH_RES_VIDEO_PREFIX = "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_7680X"
    private const val HDR_VIDEO_FLAG = "SUPPORT_HDR_VIDEOS_MENU"

    private val superSteadyMaps = setOf(
        "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_3840X2160_60FPS",
        "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_3840X1644_60FPS",
        "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_3840X1648_60FPS",
    )

    private val loggedMaps = ConcurrentHashMap.newKeySet<String>()

    fun install(classLoader: ClassLoader) {
        val method = findMapGetter(classLoader)
        if (method == null) {
            CameraModHook.log("$MAP_GETTER_CLASS.$MAP_GETTER_METHOD not found; maps unchanged")
            return
        }

        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val key = (param.args.getOrNull(0) as? Enum<*>)?.name ?: return
                val change = changeFor(key) ?: return

                val original = param.result as? Map<*, *> ?: return
                val copy = HashMap<Any?, Any?>(original.size)
                original.forEach { (mapKey, value) -> copy[mapKey] = value }
                change.applyTo(copy)

                param.result = copy
                if (loggedMaps.add(key)) {
                    CameraModHook.log("Rewrote $key (${change.label})")
                }
            }
        })

        CameraModHook.log("Feature-map overrides installed")
    }

    private sealed class MapChange(val label: String) {
        abstract fun applyTo(map: HashMap<Any?, Any?>)

        class Put(val entries: Map<String, String>, label: String) : MapChange(label) {
            override fun applyTo(map: HashMap<Any?, Any?>) {
                entries.forEach { (key, value) -> map[key] = value }
            }
        }
    }

    private fun changeFor(key: String): MapChange? {
        val overrides = SettingsBridge.flagOverrides()
        return when {
            key == DUAL_RECORDING_V2_MAP &&
                overrides[DUAL_RECORDING_FLAG] == true ->
                MapChange.Put(mapOf("enable" to "true"), DUAL_RECORDING_FLAG)

            key.startsWith(HIGH_RES_VIDEO_PREFIX) &&
                overrides[HDR_VIDEO_FLAG] == true ->
                MapChange.Put(mapOf("hdr10" to "true", "hlg" to "true"), HDR_VIDEO_FLAG)

            key in superSteadyMaps && SettingsBridge.isEnabled(ModFeature.SUPER_STEADY_4K60) ->
                MapChange.Put(
                    mapOf("super-vdis" to "true"),
                    ModFeature.SUPER_STEADY_4K60.key,
                )

            else -> null
        }
    }

    private fun findMapGetter(classLoader: ClassLoader): Method? = try {
        val clazz = Class.forName(MAP_GETTER_CLASS, false, classLoader)
        clazz.declaredMethods.firstOrNull { method ->
            method.name == MAP_GETTER_METHOD &&
                Modifier.isStatic(method.modifiers) &&
                Map::class.java.isAssignableFrom(method.returnType) &&
                method.parameterCount == 1 &&
                method.parameterTypes[0].name == MAP_PARAM_TYPE
        }
    } catch (t: Throwable) {
        CameraModHook.log("Feature-map lookup failed (${t.javaClass.simpleName}: ${t.message})")
        null
    }
}
