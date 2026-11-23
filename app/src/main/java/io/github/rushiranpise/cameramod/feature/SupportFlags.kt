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
 * Flips the camera app's computed support flags from false to true for opt-in features.
 *
 * e5.g.o(e5.d) is the single boolean getter used by the whole app for these flags. Hooking the
 * getter (rather than the XML loader) catches flags that are derived from Samsung floating
 * features or the device feature file. A flag that is already true stays true; a disabled toggle
 * never forces it false, so stock behavior is untouched.
 */
object SupportFlags {

    private const val FLAG_GETTER_CLASS = "e5.g"
    private const val FLAG_GETTER_METHOD = "o"
    private const val FLAG_PARAM_TYPE = "e5.d"

    private val forcedByFeature = mapOf(
        // Video tab
        "SUPPORT_VIDEO_BOKEH_UHD" to ModFeature.PORTRAIT_VIDEO_4K,
        "SUPPORT_PRO_VIDEO_TELE_LENS_120FPS" to ModFeature.TELE_120,
        "SUPPORT_PRO_VIDEO_SECOND_TELE_LENS_120FPS" to ModFeature.TELE_120,
        "SUPPORT_HDR_VIDEOS_MENU" to ModFeature.HDR_8K,
        "SUPPORT_APV" to ModFeature.APV,
        "SUPPORT_VIDEO_BOKEH_NATURAL_BLUR" to ModFeature.NATURAL_BLUR,

        // Camera/photo tab
        "SUPPORT_PORTRAIT_FILTER" to ModFeature.PORTRAIT_FILTER,
        "SUPPORT_DETAIL_ENHANCER_BUTTON" to ModFeature.DETAIL_ENHANCER_BUTTON,
        "SUPPORT_50MP_MOTION_PHOTO" to ModFeature.MOTION_PHOTO_50MP,
        "SUPPORT_24MP_MOTION_PHOTO" to ModFeature.MOTION_PHOTO_24MP,
        "SUPPORT_FUSION_HIGH_RESOLUTION" to ModFeature.FUSION_HIGH_RESOLUTION,
        "SUPPORT_PHOTO_MAX_NIGHT_SHOT" to ModFeature.PHOTO_MAX_NIGHT_SHOT,
        "SUPPORT_AI_HIGH_RESOLUTION_MEMORY_CHECK" to ModFeature.AI_HIGH_RES_MEMORY_CHECK,
        "SUPPORT_BACK_MEDIUM_RESOLUTION" to ModFeature.BACK_MEDIUM_RESOLUTION,
    )

    private val loggedFlags = ConcurrentHashMap.newKeySet<String>()

    fun install(classLoader: ClassLoader) {
        val method = findFlagGetter(classLoader)
        if (method == null) {
            CameraModHook.log("$FLAG_GETTER_CLASS.$FLAG_GETTER_METHOD not found; flags unchanged")
            return
        }

        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.result == true) return

                val name = (param.args.getOrNull(0) as? Enum<*>)?.name ?: return
                val feature = forcedByFeature[name] ?: return
                if (!SettingsBridge.isEnabled(feature)) return

                param.result = true
                if (loggedFlags.add(name)) {
                    CameraModHook.log("Enabled $name for ${feature.key}")
                }
            }
        })

        CameraModHook.log(
            "Support-flag overrides installed (${forcedByFeature.keys.joinToString()})"
        )
    }

    private fun findFlagGetter(classLoader: ClassLoader): Method? = try {
        val clazz = Class.forName(FLAG_GETTER_CLASS, false, classLoader)
        clazz.declaredMethods.firstOrNull { method ->
            method.name == FLAG_GETTER_METHOD &&
                Modifier.isStatic(method.modifiers) &&
                method.returnType == Boolean::class.javaPrimitiveType &&
                method.parameterCount == 1 &&
                method.parameterTypes[0].name == FLAG_PARAM_TYPE
        }
    } catch (t: Throwable) {
        CameraModHook.log(
            "Support-flag lookup failed (${t.javaClass.simpleName}: ${t.message})"
        )
        null
    }
}
