package io.github.rushiranpise.cameramod.feature

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.rushiranpise.cameramod.CameraModHook
import io.github.rushiranpise.cameramod.settings.SettingsBridge
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Applies the user's raw support-flag overrides returned by the module app.
 *
 * `e5.g.o(e5.d)` is the single boolean getter used by the whole app. Hooking the getter leaves
 * the stock flag map untouched, so [StockFlagsReporter] can keep reporting true device values.
 * A missing override keeps the stock result; an override can force either true or false.
 */
object SupportFlags {

    private const val FLAG_GETTER_CLASS = "e5.g"
    private const val FLAG_GETTER_METHOD = "o"
    private const val FLAG_PARAM_TYPE = "e5.d"

    private val loggedFlags = ConcurrentHashMap.newKeySet<String>()

    fun install(classLoader: ClassLoader) {
        val method = findFlagGetter(classLoader)
        if (method == null) {
            CameraModHook.log("$FLAG_GETTER_CLASS.$FLAG_GETTER_METHOD not found; flags unchanged")
            return
        }

        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val name = (param.args.getOrNull(0) as? Enum<*>)?.name ?: return
                val override = SettingsBridge.flagOverrides()[name] ?: return
                val current = param.result as? Boolean ?: return
                if (current == override) return

                param.result = override
                if (loggedFlags.add("$name=$override")) {
                    CameraModHook.log("Flag override $name -> $override")
                }
            }
        })

        CameraModHook.log("Support-flag override hook installed")
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
