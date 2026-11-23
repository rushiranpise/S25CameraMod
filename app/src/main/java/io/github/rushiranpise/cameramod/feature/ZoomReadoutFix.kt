package io.github.rushiranpise.cameramod.feature

import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.rushiranpise.cameramod.CameraModHook
import java.lang.reflect.Array
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Lets the zoom readout render five-character values such as "100.4".
 *
 * Three stock views keep four text-size and four icon-width buckets and throw
 * IllegalArgumentException("Invalid string length : 100.4, 5") when the formatted zoom is five
 * characters:
 *
 *   - ZoomRockerView.f(int)            main zoom readout
 *   - ZoomLensTextButton.i(TextView, String)     selected lens button
 *   - ZoomLensShortcutButton.a(TextView, String) shortcut button
 *
 * Each view is extended to five buckets by appending a copy of its fourth entry. If a view cannot
 * be extended, its formatter rounds the value to a whole number above 100x, which uses the stock
 * "100" / "107" path and still avoids the crash. Values up to 999.9x stay within five characters.
 */
object ZoomReadoutFix {

    private const val ROCKER_CLASS =
        "com.sec.android.app.camera.shootingmode.common.zoomrocker.ZoomRockerView"
    private const val LENS_TEXT_CLASS =
        "com.sec.android.app.camera.layer.keyscreen.zoom.widget.ZoomLensTextButton"
    private const val LENS_SHORTCUT_CLASS =
        "com.sec.android.app.camera.layer.keyscreen.zoom.widget.ZoomLensShortcutButton"
    private const val REQUIRED_LENGTH = 5
    private const val THRESHOLD_MILLI = 100_000

    @Volatile
    var installed = false
        private set

    private val extendedLogged = AtomicBoolean(false)
    private val fallbackLogged = AtomicBoolean(false)

    fun install(classLoader: ClassLoader) {
        val rocker = installRocker(classLoader)
        val lensText = installLens(classLoader, LENS_TEXT_CLASS)
        val lensShortcut = installLens(classLoader, LENS_SHORTCUT_CLASS)

        installed = rocker && lensText && lensShortcut
        if (installed) {
            CameraModHook.log("Zoom readout fix installed (5 characters, 3 views)")
        } else {
            CameraModHook.log("Zoom readout fix incomplete; ceiling capped at 100x")
        }
    }

    private fun installRocker(classLoader: ClassLoader): Boolean {
        val clazz = load(classLoader, ROCKER_CLASS) ?: return false
        val fields = integerArrayFields(clazz) ?: return false
        val readout = clazz.declaredMethods.firstOrNull { method ->
            method.name == "f" &&
                method.parameterCount == 1 &&
                method.parameterTypes[0] == Int::class.javaPrimitiveType
        } ?: return false

        hookConstructors(clazz, fields)
        XposedBridge.hookMethod(readout, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val milli = param.args.getOrNull(0) as? Int ?: return
                if (milli <= THRESHOLD_MILLI) return
                if (arraysReady(param.thisObject, fields)) return

                param.args[0] = ((milli + 500) / 1000) * 1000
                logFallback()
            }
        })
        return true
    }

    private fun installLens(
        classLoader: ClassLoader,
        className: String,
    ): Boolean {
        val clazz = load(classLoader, className) ?: return false
        val fields = integerArrayFields(clazz) ?: return false
        val formatter = clazz.declaredMethods.firstOrNull { method ->
            method.parameterCount == 2 &&
                method.parameterTypes[0] == TextView::class.java &&
                method.parameterTypes[1] == String::class.java
        } ?: return false

        hookConstructors(clazz, fields)
        XposedBridge.hookMethod(formatter, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val text = param.args.getOrNull(1) as? String ?: return
                if (text.length <= REQUIRED_LENGTH - 1) return
                if (arraysReady(param.thisObject, fields)) return

                val rounded = text.toFloatOrNull()?.roundToInt()?.toString() ?: return
                param.args[1] = rounded
                logFallback()
            }
        })
        return true
    }

    private fun load(classLoader: ClassLoader, className: String): Class<*>? =
        try {
            Class.forName(className, false, classLoader)
        } catch (t: Throwable) {
            CameraModHook.log("Zoom readout view missing: $className")
            null
        }

    private fun integerArrayFields(clazz: Class<*>): List<Field>? {
        val fields = clazz.declaredFields.filter { field ->
            !Modifier.isStatic(field.modifiers) &&
                field.type.isArray &&
                field.type.componentType?.name == "java.lang.Integer"
        }
        if (fields.size < 2) return null
        fields.forEach { it.isAccessible = true }
        return fields
    }

    private fun hookConstructors(clazz: Class<*>, fields: List<Field>) {
        XposedBridge.hookAllConstructors(clazz, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                fields.forEach { field -> extend(field, param.thisObject) }
            }
        })
    }

    private fun extend(field: Field, target: Any?) {
        if (target == null) return
        try {
            val original = field.get(target) ?: return
            val length = Array.getLength(original)
            if (length >= REQUIRED_LENGTH || length == 0) return

            val extended = Array.newInstance(field.type.componentType!!, REQUIRED_LENGTH)
            for (i in 0 until length) {
                Array.set(extended, i, Array.get(original, i))
            }
            val last = Array.get(original, length - 1)
            for (i in length until REQUIRED_LENGTH) {
                Array.set(extended, i, last)
            }
            field.set(target, extended)

            if (extendedLogged.compareAndSet(false, true)) {
                CameraModHook.log("Zoom readout extended to 5 characters")
            }
        } catch (t: Throwable) {
            logFallback()
        }
    }

    private fun arraysReady(target: Any?, fields: List<Field>): Boolean {
        if (target == null) return false
        return fields.all { field ->
            try {
                val array = field.get(target) ?: return@all false
                Array.getLength(array) >= REQUIRED_LENGTH
            } catch (t: Throwable) {
                false
            }
        }
    }

    private fun logFallback() {
        if (fallbackLogged.compareAndSet(false, true)) {
            CameraModHook.log("Zoom readout fallback active (integer display above 100x)")
        }
    }
}
