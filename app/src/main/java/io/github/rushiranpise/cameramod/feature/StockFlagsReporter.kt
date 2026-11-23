package io.github.rushiranpise.cameramod.feature

import android.app.AndroidAppHelper
import android.content.Context
import android.net.Uri
import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.rushiranpise.cameramod.CameraModHook
import io.github.rushiranpise.cameramod.settings.ModSettings
import io.github.rushiranpise.cameramod.settings.ModSettingsProvider
import org.json.JSONObject
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reports the camera's complete stock support-flag map to the module app.
 *
 * `e5.g.q()` builds the singleton that owns `e5.f`, and `e5.f.h` is the `EnumMap<e5.d, Boolean>`
 * populated from camera-feature.xml and the device config. Reading that map directly (instead of
 * going through `e5.g.o`) keeps the report free of the module's own overrides.
 *
 * The hook runs after the singleton exists, so the map is fully populated. One report per camera
 * process is enough; a failed report is retried on the next `q()` call.
 */
object StockFlagsReporter {

    private const val CONFIG_CLASS = "e5.g"
    private const val CONFIG_FACTORY = "q"

    private val reported = AtomicBoolean(false)

    fun install(classLoader: ClassLoader) {
        val factory = findFactory(classLoader)
        if (factory == null) {
            CameraModHook.log("$CONFIG_CLASS.$CONFIG_FACTORY not found; stock flags not reported")
            return
        }

        XposedBridge.hookMethod(factory, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!reported.compareAndSet(false, true)) return
                try {
                    report(param.result)
                } catch (t: Throwable) {
                    reported.set(false)
                    CameraModHook.log(
                        "Stock flag report failed (${t.javaClass.simpleName}: ${t.message})"
                    )
                }
            }
        })

        CameraModHook.log("Stock-flag reporter installed")
    }

    private fun report(config: Any?) {
        if (config == null) return
        val context = AndroidAppHelper.currentApplication() ?: return
        val flagsMap = findBooleanFlagMap(config) ?: run {
            CameraModHook.log("Stock flag map not found; will retry")
            return
        }

        val flags = JSONObject()
        var count = 0
        flagsMap.forEach { (key, value) ->
            val name = (key as? Enum<*>)?.name ?: return@forEach
            flags.put(name, value == true)
            count++
        }
        if (count == 0) {
            CameraModHook.log("Stock flag map is empty; will retry")
            return
        }

        val root = JSONObject()
        root.put("cameraVersion", cameraVersion(context))
        root.put("capturedAt", System.currentTimeMillis())
        root.put("flags", flags)

        val extras = Bundle().apply {
            putString(ModSettingsProvider.EXTRA_FLAGS_JSON, root.toString())
        }
        val result = context.contentResolver.call(
            Uri.parse("content://${ModSettings.AUTHORITY}/"),
            ModSettingsProvider.METHOD_REPORT_STOCK_FLAGS,
            null,
            extras,
        )
        val saved = result?.getBoolean(ModSettingsProvider.EXTRA_OK) == true
        CameraModHook.log(
            "Reported $count stock flags (Camera ${cameraVersion(context)}): " +
                if (saved) "saved" else "not saved"
        )
    }

    private fun cameraVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (t: Throwable) {
        ""
    }

    /**
     * `e5.g` has one non-static field holding the `e5.f` instance. The `e5.f` class has several
     * EnumMaps; the boolean one is the only map keyed by the `e5.d` enum. Locating it by content
     * avoids depending on the obfuscated field names.
     */
    private fun findBooleanFlagMap(config: Any): Map<*, *>? {
        val holder = config.javaClass.declaredFields
            .asSequence()
            .filter { !Modifier.isStatic(it.modifiers) && !it.type.isPrimitive }
            .mapNotNull { field ->
                try {
                    field.isAccessible = true
                    field.get(config)
                } catch (t: Throwable) {
                    null
                }
            }
            .firstOrNull { value ->
                value.javaClass.name.startsWith("e5.f")
            }
            ?: return null

        return holder.javaClass.declaredFields
            .asSequence()
            .filter { !Modifier.isStatic(it.modifiers) }
            .mapNotNull { field ->
                try {
                    field.isAccessible = true
                    field.get(holder) as? Map<*, *>
                } catch (t: Throwable) {
                    null
                }
            }
            .firstOrNull { map ->
                map.isNotEmpty() && map.keys.firstOrNull()?.javaClass?.name == "e5.d"
            }
    }

    private fun findFactory(classLoader: ClassLoader): Method? = try {
        val clazz = Class.forName(CONFIG_CLASS, false, classLoader)
        clazz.declaredMethods.firstOrNull { method ->
            method.name == CONFIG_FACTORY &&
                Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 0
        }
    } catch (t: Throwable) {
        CameraModHook.log(
            "Stock-flag factory lookup failed (${t.javaClass.simpleName}: ${t.message})"
        )
        null
    }
}
