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
 * Supplies the 24 MP resolution entries that this camera build omits from camera-feature.xml.
 *
 * The stock flag `SUPPORT_BACK_MEDIUM_RESOLUTION` is computed from those XML values, so forcing
 * the flag alone makes the app call `e5.g.m(BACK_CAMERA_RESOLUTION_MEDIUM_RESOLUTION*)`, which
 * would otherwise fail a null check. Returning the standard 24 MP sizes here keeps the
 * experimental 24 MP option from taking the camera process down when the toggle is enabled.
 */
object PhotoResolutions {

    private const val GETTER_CLASS = "e5.g"
    private const val GETTER_METHOD = "m"
    private const val RESOLUTION_TYPE = "e5.n"

    private val mediumResolution = mapOf(
        "BACK_CAMERA_RESOLUTION_MEDIUM_RESOLUTION" to "5712x4284",
        "BACK_CAMERA_RESOLUTION_MEDIUM_RESOLUTION_16BY9" to "5712x3212",
        "BACK_CAMERA_RESOLUTION_MEDIUM_RESOLUTION_1BY1" to "4272x4272",
        "BACK_CAMERA_RESOLUTION_MEDIUM_RESOLUTION_FULL_RATIO" to "5712x2636",
    )

    private val loggedKeys = ConcurrentHashMap.newKeySet<String>()

    fun install(classLoader: ClassLoader) {
        val method = findResolutionGetter(classLoader)
        if (method == null) {
            CameraModHook.log("$GETTER_CLASS.$GETTER_METHOD not found; 24 MP fallback unavailable")
            return
        }

        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!SettingsBridge.isEnabled(ModFeature.BACK_MEDIUM_RESOLUTION)) return
                val name = (param.args.getOrNull(0) as? Enum<*>)?.name ?: return
                val value = mediumResolution[name] ?: return

                param.result = value
                if (loggedKeys.add(name)) {
                    CameraModHook.log("24 MP resolution fallback: $name -> $value")
                }
            }
        })

        CameraModHook.log("24 MP resolution fallback installed")
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
            "24 MP resolution lookup failed (${t.javaClass.simpleName}: ${t.message})"
        )
        null
    }
}
