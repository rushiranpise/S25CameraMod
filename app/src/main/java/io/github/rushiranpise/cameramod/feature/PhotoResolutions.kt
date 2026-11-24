package io.github.rushiranpise.cameramod.feature

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.rushiranpise.cameramod.CameraModHook
import io.github.rushiranpise.cameramod.settings.SettingsBridge
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Fills in resolution entries that this camera build leaves empty in camera-feature.xml.
 *
 * The stock flags `SUPPORT_BACK_MEDIUM_RESOLUTION` and `SUPPORT_FRONT_HIGH_RESOLUTION` are
 * computed from those XML values. Forcing either flag on makes the app parse
 * `e5.g.m(BACK_CAMERA_RESOLUTION_MEDIUM_RESOLUTION*)` / `FRONT_CAMERA_RESOLUTION_*`, which are
 * stored as empty strings and then crash `l9.a2.k` with "getResolution : argument is invalid".
 * Returning valid sensor sizes here keeps the experimental options from taking the camera
 * process down when the flags are overridden on.
 */
object PhotoResolutions {

    private const val GETTER_CLASS = "e5.g"
    private const val GETTER_METHOD = "m"
    private const val RESOLUTION_TYPE = "e5.n"
    private const val MEDIUM_RESOLUTION_FLAG = "SUPPORT_BACK_MEDIUM_RESOLUTION"
    private const val PORTRAIT_MEDIUM_RESOLUTION_FLAG = "SUPPORT_BACK_PORTRAIT_MEDIUM_RESOLUTION"
    private const val FRONT_HIGH_RESOLUTION_FLAG = "SUPPORT_FRONT_HIGH_RESOLUTION"

    private val mediumResolution = mapOf(
        "BACK_CAMERA_RESOLUTION_MEDIUM_RESOLUTION" to "5712x4284",
        "BACK_CAMERA_RESOLUTION_MEDIUM_RESOLUTION_16BY9" to "5712x3212",
        "BACK_CAMERA_RESOLUTION_MEDIUM_RESOLUTION_1BY1" to "4272x4272",
        "BACK_CAMERA_RESOLUTION_MEDIUM_RESOLUTION_FULL_RATIO" to "5712x2636",
    )

    // The 12 MP front camera has no larger sensor mode; these mirror the *_LARGE values that
    // Samsung ships in camera-feature.xml so the front picture-size menu can be constructed.
    private val frontResolution = mapOf(
        "FRONT_CAMERA_RESOLUTION_4BY3" to "4000x3000",
        "FRONT_CAMERA_RESOLUTION_4BY3_LARGE" to "4000x3000",
        "FRONT_CAMERA_RESOLUTION_HIGH_RESOLUTION" to "4000x3000",
        "FRONT_CAMERA_RESOLUTION_16BY9" to "4000x2252",
        "FRONT_CAMERA_RESOLUTION_16BY9_LARGE" to "4000x2252",
        "FRONT_CAMERA_RESOLUTION_1BY1" to "2992x2992",
        "FRONT_CAMERA_RESOLUTION_1BY1_LARGE" to "2992x2992",
        "FRONT_CAMERA_RESOLUTION_FULL_RATIO" to "4000x1848",
    )

    private val loggedKeys = ConcurrentHashMap.newKeySet<String>()

    fun install(classLoader: ClassLoader) {
        val method = findResolutionGetter(classLoader)
        if (method == null) {
            CameraModHook.log("$GETTER_CLASS.$GETTER_METHOD not found; resolution fallback unavailable")
            return
        }

        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val name = (param.args.getOrNull(0) as? Enum<*>)?.name ?: return
                if ((param.result as? String)?.isNotEmpty() == true) return

                val overrides = SettingsBridge.flagOverrides()
                val mediumActive = overrides[MEDIUM_RESOLUTION_FLAG] == true ||
                    overrides[PORTRAIT_MEDIUM_RESOLUTION_FLAG] == true
                val frontActive = overrides[FRONT_HIGH_RESOLUTION_FLAG] == true
                val value = mediumResolution[name]?.takeIf { mediumActive }
                    ?: frontResolution[name]?.takeIf { frontActive }
                    ?: return

                param.result = value
                if (loggedKeys.add(name)) {
                    CameraModHook.log("Resolution fallback: $name -> $value")
                }
            }
        })

        CameraModHook.log("Resolution fallback installed")
    }

    private fun findResolutionGetter(classLoader: ClassLoader): Method? = try {
        val clazz = Class.forName(GETTER_CLASS, false, classLoader)
        clazz.declaredMethods.firstOrNull { method ->
            method.name == GETTER_METHOD &&
                Modifier.isStatic(method.modifiers) &&
                method.returnType == String::class.java &&
                method.parameterCount == 1 &&
                method.parameterTypes[0].name == RESOLUTION_TYPE
        }
    } catch (t: Throwable) {
        CameraModHook.log(
            "Resolution fallback lookup failed (${t.javaClass.simpleName}: ${t.message})"
        )
        null
    }
}
