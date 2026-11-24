package io.github.rushiranpise.cameramod.feature

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.rushiranpise.cameramod.CameraModHook
import io.github.rushiranpise.cameramod.settings.SettingsBridge
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Supplies the missing `view_mode` analytics key used by the Settings list.
 *
 * Enabling `SUPPORT_PREVIEW_FIT_TO_FULL_SCREEN` keeps the `view_mode` switch preference instead of
 * removing it. `PreferenceSettingFragment.onResume` then maps every switch key through
 * `sc.b0.h(String)`, but this Camera build never compiled a `view_mode` entry into that map, so it
 * throws `RuntimeException: No defined key view_mode` and Settings crashes.
 *
 * The mapped value is only used for Samsung Analytics when the switch is tapped. Returning the
 * enum's own `INVALID` event for this one key keeps Settings usable without changing any camera
 * behavior. The hook is inert unless the user has overridden the flag on.
 */
object SettingsKeyFallback {

    private const val SETTINGS_CLASS = "sc.b0"
    private const val LOOKUP_METHOD = "h"
    private const val VIEW_MODE_KEY = "view_mode"
    private const val PREVIEW_FIT_FLAG = "SUPPORT_PREVIEW_FIT_TO_FULL_SCREEN"

    private val logged = ConcurrentHashMap.newKeySet<String>()

    fun install(classLoader: ClassLoader) {
        val method = findLookup(classLoader)
        if (method == null) {
            CameraModHook.log("$SETTINGS_CLASS.$LOOKUP_METHOD not found; view_mode fallback unavailable")
            return
        }

        val invalidEvent = method.returnType.enumConstants
            ?.firstOrNull { (it as? Enum<*>)?.name == "INVALID" }
            ?: run {
                CameraModHook.log("${method.returnType.name}.INVALID not found; view_mode fallback unavailable")
                return
            }

        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val key = param.args.getOrNull(0) as? String ?: return
                if (key != VIEW_MODE_KEY) return
                if (SettingsBridge.flagOverrides()[PREVIEW_FIT_FLAG] != true) return

                param.result = invalidEvent
                if (logged.add(key)) {
                    CameraModHook.log("Settings key fallback: $key -> INVALID")
                }
            }
        })

        CameraModHook.log("Settings key fallback installed")
    }

    private fun findLookup(classLoader: ClassLoader): Method? = try {
        val clazz = Class.forName(SETTINGS_CLASS, false, classLoader)
        clazz.declaredMethods.firstOrNull { method ->
            method.name == LOOKUP_METHOD &&
                method.parameterCount == 1 &&
                method.parameterTypes[0] == String::class.java &&
                method.returnType.isEnum
        }
    } catch (t: Throwable) {
        CameraModHook.log(
            "Settings key lookup failed (${t.javaClass.simpleName}: ${t.message})"
        )
        null
    }
}
