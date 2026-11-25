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
 * the keys covered by a rule are copied and changed; every other map is returned untouched.
 *
 * A forced support flag usually only opens the matching menu. The per-resolution map keys are a
 * second gate, so every enabled flag also flips the capability keys that feature needs. Without
 * that, forcing e.g. SUPPORT_APV shows the APV capture type but te.c0.o(resolution) still returns
 * false and the setting is removed again.
 */
object FeatureMaps {

    private const val MAP_GETTER_CLASS = "e5.g"
    private const val MAP_GETTER_METHOD = "n"
    private const val MAP_PARAM_TYPE = "e5.l"

    private const val BACK_CAMCORDER_MAP_PREFIX = "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_"
    private const val FRONT_CAMCORDER_MAP_PREFIX = "FRONT_CAMCORDER_RESOLUTION_FEATURE_MAP_"

    private val loggedMaps = ConcurrentHashMap.newKeySet<String>()

    /** Selects the maps a rule applies to. */
    private sealed class Target {
        abstract fun matches(key: String): Boolean

        object BackCamcorder : Target() {
            override fun matches(key: String) = key.startsWith(BACK_CAMCORDER_MAP_PREFIX)
        }

        object FrontCamcorder : Target() {
            override fun matches(key: String) = key.startsWith(FRONT_CAMCORDER_MAP_PREFIX)
        }

        object AllCamcorder : Target() {
            override fun matches(key: String) =
                key.startsWith(BACK_CAMCORDER_MAP_PREFIX) ||
                    key.startsWith(FRONT_CAMCORDER_MAP_PREFIX)
        }

        class Named(vararg mapNames: String) : Target() {
            private val names = mapNames.toSet()

            override fun matches(key: String) = key in names
        }
    }

    /**
     * A forced support flag that also has to be mirrored into the per-resolution map. The flag
     * opens the menu; the map key decides whether each resolution offers it.
     */
    private class CapabilityRule(
        val flag: String,
        val target: Target,
        val entries: Map<String, String>,
    )

    private val capabilityRules = listOf(
        // APV is a per-resolution capability, not a shooting mode. This firmware omits the
        // "apv" attribute entirely, so te.c0.o(resolution) stays false even when SUPPORT_APV is
        // forced on and the APV capture type, profile and indicator settings are removed again.
        CapabilityRule("SUPPORT_APV", Target.BackCamcorder, mapOf("apv" to "true")),
        // HDR10/HLG recording is gated per resolution by te.c0.r and vc.v.
        CapabilityRule(
            "SUPPORT_HDR_VIDEOS_MENU",
            Target.BackCamcorder,
            mapOf("hdr10" to "true", "hlg" to "true"),
        ),
        // VIDEO_STABILISATION exists when either anti-shake flag is on, but te.c0.x still hides
        // it for resolutions whose map says vdis=false.
        CapabilityRule(
            "SUPPORT_CAMCORDER_ANTI_SHAKE",
            Target.BackCamcorder,
            mapOf("vdis" to "true"),
        ),
        CapabilityRule(
            "SUPPORT_CAMCORDER_FRONT_ANTI_SHAKE",
            Target.FrontCamcorder,
            mapOf("vdis" to "true"),
        ),
        CapabilityRule(
            "SUPPORT_SEAMLESS_ZOOM",
            Target.BackCamcorder,
            mapOf("seamless-zoom-support" to "true"),
        ),
        // TRACKING_AF is gated per resolution by te.c0.s.
        CapabilityRule(
            "SUPPORT_OBJECT_TRACKING_AF",
            Target.AllCamcorder,
            mapOf("object-tracking" to "true"),
        ),
        // BACK_VIDEO_BEAUTY_LEVEL and manual color tune are gated by te.c0.p ("effect").
        CapabilityRule(
            "SUPPORT_BACK_VIDEO_BEAUTY",
            Target.BackCamcorder,
            mapOf("effect" to "true"),
        ),
        CapabilityRule(
            "SUPPORT_DUAL_RECORDING_IN_VIDEO_MODE",
            Target.Named("SHOOTING_MODE_DUAL_RECORDING_V2"),
            mapOf("enable" to "true"),
        ),
    )

    /** A curated mod switch that is not backed by a single raw support flag. */
    private class FeatureRule(
        val feature: ModFeature,
        val target: Target,
        val entries: Map<String, String>,
    )

    private val featureRules = listOf(
        FeatureRule(
            ModFeature.SUPER_STEADY_4K60,
            Target.Named(
                "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_3840X2160_60FPS",
                "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_3840X1644_60FPS",
                "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_3840X1648_60FPS",
            ),
            mapOf("super-vdis" to "true"),
        ),
    )

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

                val changedKeys = change.changedKeys(original)
                if (changedKeys.isEmpty()) return

                val copy = LinkedHashMap<Any?, Any?>(original.size)
                original.forEach { (mapKey, value) -> copy[mapKey] = value }
                change.applyTo(copy)

                param.result = copy
                if (loggedMaps.add(key)) {
                    CameraModHook.log(
                        "Rewrote $key (${change.label}: ${changedKeys.joinToString()})",
                    )
                }
            }
        })

        CameraModHook.log("Feature-map overrides installed")
    }

    private sealed class MapChange(val label: String) {
        abstract fun applyTo(map: MutableMap<Any?, Any?>)

        abstract fun changedKeys(map: Map<*, *>): List<String>

        class Put(val entries: Map<String, String>, label: String) : MapChange(label) {
            override fun applyTo(map: MutableMap<Any?, Any?>) {
                entries.forEach { (key, value) -> map[key] = value }
            }

            override fun changedKeys(map: Map<*, *>): List<String> =
                entries.filter { (key, value) -> map.valueFor(key)?.toString() != value }.keys.toList()
        }
    }

    private fun changeFor(key: String): MapChange? {
        val overrides = SettingsBridge.flagOverrides()
        val entries = LinkedHashMap<String, String>()
        val labels = ArrayList<String>()

        capabilityRules.forEach { rule ->
            if (rule.target.matches(key) && overrides[rule.flag] == true) {
                entries.putAll(rule.entries)
                labels += rule.flag
            }
        }

        featureRules.forEach { rule ->
            if (rule.target.matches(key) && SettingsBridge.isEnabled(rule.feature)) {
                entries.putAll(rule.entries)
                labels += rule.feature.key
            }
        }

        return if (entries.isEmpty()) null else MapChange.Put(entries, labels.joinToString("+"))
    }

    private fun Map<*, *>.valueFor(key: String): Any? =
        entries.firstOrNull { it.key == key }?.value

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
