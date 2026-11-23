package io.github.rushiranpise.cameramod.feature

import android.util.Pair
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.rushiranpise.cameramod.CameraModHook
import io.github.rushiranpise.cameramod.settings.ModFeature
import io.github.rushiranpise.cameramod.settings.SettingsBridge
import java.lang.reflect.Modifier
import java.util.EnumMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Replaces the camera's auto-generated high-zoom shortcut tail with the user's presets.
 *
 * The stock UI chooses the tail from the maximum zoom in ZoomView.m(cd.b):
 *
 *   25000  -> X10, X25
 *   30000  -> X10, X20, X30
 *   100000 -> X10, X30, X100
 *   other  -> nothing, which is why a 200000 ceiling loses the stock 25x chip
 *
 * The shortcut list is a mutable ArrayList shared by ZoomView.j, the slider adapter (z9.k), the
 * lens-shortcut adapter (z9.e), and the tick slider. The adapters are constructed with that list
 * before it is rendered, so this hook keeps every shortcut at or below 10x and replaces the
 * high-zoom tail with the configured presets. Sorting the merged list gives ascending chips.
 *
 * Chips are stock l9.j0 enum constants, so arbitrary values are represented by an unused
 * high-zoom constant whose value is overridden. d8.r.d(j0) is the single stock value lookup used
 * by the button formatter, click handler, selection state, and ZoomTickSlider; overriding it for
 * the placeholder constants keeps all of them consistent without touching each view.
 *
 * The default list is [25, 100]: it restores the stock 25x chip and adds the module's 100x.
 */
object ZoomPresets {

    private const val SHORTCUT_ENUM_CLASS = "l9.j0"
    private const val SHORTCUT_VALUE_CLASS = "d8.r"
    private const val SLIDER_ADAPTER_CLASS = "z9.k"
    private const val LENS_ADAPTER_CLASS = "z9.e"
    private const val SHORTCUT_PREFIX = "BACK_CAMERA_ZOOM_SHORTCUT"
    private const val HIGH_SLOT_MIN = 10_000

    @Volatile
    private var installed = false

    @Volatile
    private var classLoader: ClassLoader? = null

    @Volatile
    private var plan: PresetPlan? = null

    @Volatile
    private var enumCache: List<ShortcutEnum>? = null

    private val loggedPlan = AtomicReference<String>()
    private val loggedFailure = AtomicBoolean(false)

    private class ShortcutEnum(
        val constant: Any,
        val name: String,
        val milli: Int,
    )

    private class PresetEntry(
        val constant: Any,
        val name: String,
        val milli: Int,
    )

    private class PresetPlan(
        val signature: String,
        val entries: List<PresetEntry>,
        val overrides: Map<String, Int>,
        val stockValues: Map<String, Int>,
    ) {
        fun valueOf(name: String): Int? = overrides[name] ?: stockValues[name]
    }

    fun install(loader: ClassLoader) {
        if (installed) return
        classLoader = loader

        val valueLookup = hookValueLookup()
        val slider = hookAdapter(SLIDER_ADAPTER_CLASS)
        val lens = hookAdapter(LENS_ADAPTER_CLASS)
        installed = slider || lens

        if (installed) {
            CameraModHook.log(
                "Zoom preset hooks ready (value=$valueLookup, slider=$slider, lens=$lens); " +
                    "stock tail above 10x is replaced by the configured presets"
            )
        } else {
            CameraModHook.log("Zoom preset adapters not found; leaving stock shortcuts")
        }
    }

    private fun hookValueLookup(): Boolean {
        val loader = classLoader ?: return false
        return try {
            val valueClass = Class.forName(SHORTCUT_VALUE_CLASS, false, loader)
            val method = valueClass.declaredMethods.firstOrNull { candidate ->
                candidate.name == "d" &&
                    candidate.returnType == Int::class.javaPrimitiveType &&
                    candidate.parameterCount == 1 &&
                    candidate.parameterTypes[0].name == SHORTCUT_ENUM_CLASS
            } ?: run {
                CameraModHook.log("Zoom preset value lookup not found; preset clicks may be off")
                return false
            }

            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!SettingsBridge.isEnabled(ModFeature.ZOOM)) return
                    val current = plan ?: return
                    val name = (param.args.getOrNull(0) as? Enum<*>)?.name ?: return
                    current.overrides[name]?.let { param.result = it }
                }
            })
            true
        } catch (t: Throwable) {
            logFailure("value lookup", t)
            false
        }
    }

    private fun hookAdapter(className: String): Boolean {
        val loader = classLoader ?: return false
        val clazz = try {
            Class.forName(className, false, loader)
        } catch (t: Throwable) {
            CameraModHook.log("Zoom preset adapter missing: $className")
            return false
        }

        val constructor = clazz.declaredConstructors.firstOrNull { candidate ->
            candidate.parameterCount == 3 &&
                List::class.java.isAssignableFrom(candidate.parameterTypes[1])
        }
        if (constructor == null) {
            CameraModHook.log("Zoom preset adapter shape changed: $className")
            return false
        }

        XposedBridge.hookMethod(constructor, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!SettingsBridge.isEnabled(ModFeature.ZOOM)) return
                @Suppress("UNCHECKED_CAST")
                val list = param.args.getOrNull(1) as? MutableList<Any> ?: return
                try {
                    rewrite(list)
                } catch (t: Throwable) {
                    logFailure("rewrite $className", t)
                }
            }
        })
        return true
    }

    /** Mutates the shared list before the adapter stores and renders it. */
    private fun rewrite(list: MutableList<Any>) {
        if (list.isEmpty()) return

        val current = plan ?: buildPlan() ?: return
        if (current.entries.isEmpty()) return

        val original = list.toList()
        val base = original.filter { entry ->
            val value = current.valueOf(enumName(entry)) ?: return@filter false
            value in 1..HIGH_SLOT_MIN
        }
        if (base.isEmpty()) return

        val merged = ArrayList<Any>(base.size + current.entries.size)
        val seen = HashSet<String>(base.size + current.entries.size)

        base.forEach { entry ->
            val name = enumName(entry)
            // An exact preset match replaces its stock entry so the preset order/value wins.
            if (name !in current.overrides && seen.add(name)) {
                merged.add(entry)
            }
        }
        current.entries.forEach { preset ->
            if (seen.add(preset.name)) {
                merged.add(preset.constant)
            }
        }

        merged.sortWith(
            compareBy { entry -> current.valueOf(enumName(entry)) ?: Int.MAX_VALUE },
        )

        if (merged.size == original.size && merged.indices.all { merged[it] === original[it] }) {
            return
        }

        list.clear()
        list.addAll(merged)

        val signature = merged.joinToString(",") { "${enumName(it)}=${current.valueOf(enumName(it))}" }
        if (loggedPlan.getAndSet(signature) != signature) {
            CameraModHook.log(
                "Zoom presets ${current.entries.joinToString(",") { (it.milli / 1000).toString() }} " +
                    "-> chips ${chipLabels(merged, current)}"
            )
        }
    }

    private fun buildPlan(): PresetPlan? {
        val desired = SettingsBridge.zoomPresets()
        val ceiling = SettingsBridge.zoomCeiling()
        val signature = desired.joinToString(",") + "@" + ceiling

        plan?.takeIf { it.signature == signature }?.let { return it }

        synchronized(this) {
            plan?.takeIf { it.signature == signature }?.let { return it }

            val all = shortcutEnums() ?: return null
            if (all.isEmpty()) return null

            val stockValues = all.associate { it.name to it.milli }
            val ceilingMilli = (ceiling * 1000f).roundToInt()
            val wanted = desired
                .asSequence()
                .map { it * 1000 }
                .filter { it in 1..ceilingMilli }
                .distinct()
                .sorted()
                .toList()

            if (wanted.isEmpty()) {
                return PresetPlan(signature, emptyList(), emptyMap(), stockValues).also {
                    plan = it
                }
            }

            val used = HashSet<String>()
            val chosen = LinkedHashMap<Int, ShortcutEnum>()

            // First claim exact constants so a preset such as 12x does not lose X12 to a
            // neighbouring value that merely needed a placeholder.
            wanted.forEach { milli ->
                all.firstOrNull { it.milli == milli && it.name !in used }?.let { exact ->
                    used.add(exact.name)
                    chosen[milli] = exact
                }
            }

            val highSlots = all.filter { it.milli > HIGH_SLOT_MIN }.sortedBy { it.milli }
            wanted.forEach { milli ->
                if (milli in chosen) return@forEach
                val slot = highSlots
                    .asSequence()
                    .filter { it.name !in used }
                    .minByOrNull { abs(it.milli - milli) }
                    ?: return@forEach
                used.add(slot.name)
                chosen[milli] = slot
            }

            val entries = chosen.entries
                .sortedBy { it.key }
                .map { (milli, slot) -> PresetEntry(slot.constant, slot.name, milli) }
            val overrides = entries.associate { it.name to it.milli }

            return PresetPlan(signature, entries, overrides, stockValues).also {
                plan = it
            }
        }
    }

    /**
     * Reads the stock command-id table directly instead of calling d8.r.d, because the value
     * lookup is hooked once a preset plan exists. The enum names also encode the zoom level, so
     * they are used as a fallback if the table has not been populated yet.
     */
    private fun shortcutEnums(): List<ShortcutEnum>? {
        enumCache?.let { return it }
        val loader = classLoader ?: return null

        return try {
            val enumClass = Class.forName(SHORTCUT_ENUM_CLASS, true, loader)
            val valueClass = Class.forName(SHORTCUT_VALUE_CLASS, true, loader)
            val table = stockTable(valueClass, enumClass)

            val loaded = (enumClass.enumConstants ?: return null).mapNotNull { constant ->
                val name = (constant as Enum<*>).name
                if (!name.startsWith(SHORTCUT_PREFIX + "_")) return@mapNotNull null
                val milli = table?.get(constant) ?: valueFromName(name) ?: return@mapNotNull null
                if (milli <= 0) null else ShortcutEnum(constant, name, milli)
            }.sortedBy { it.milli }

            if (loaded.isEmpty()) {
                CameraModHook.log("Zoom preset enum values unavailable; leaving stock shortcuts")
                null
            } else {
                enumCache = loaded
                loaded
            }
        } catch (t: Throwable) {
            logFailure("shortcut enum lookup", t)
            null
        }
    }

    private fun stockTable(valueClass: Class<*>, enumClass: Class<*>): Map<Any, Int>? {
        // d8.r holds two EnumMaps. The zoom lookup is the one whose values are
        // Pair<f0, Integer>; the other maps command ids to sub-option lists.
        var best: Map<Any, Int>? = null
        valueClass.declaredFields.forEach { candidate ->
            if (!Modifier.isStatic(candidate.modifiers)) return@forEach
            if (!EnumMap::class.java.isAssignableFrom(candidate.type)) return@forEach
            candidate.isAccessible = true
            val map = candidate.get(null) as? Map<*, *> ?: return@forEach

            val values = HashMap<Any, Int>(map.size)
            map.forEach { (constant, entry) ->
                if (constant != null && constant.javaClass == enumClass) {
                    val milli = (entry as? Pair<*, *>)?.second as? Int
                    if (milli != null && milli > 0) values[constant] = milli
                }
            }
            val existing = best
            if (values.isNotEmpty() && (existing == null || values.size > existing.size)) {
                best = values
            }
        }
        return best
    }

    /** X0_5 -> 500, X25 -> 25000. */
    private fun valueFromName(name: String): Int? {
        val raw = name.removePrefix(SHORTCUT_PREFIX + "_X")
        if (raw == name) return null
        val value = raw.replace('_', '.').toFloatOrNull() ?: return null
        return (value * 1000f).roundToInt()
    }

    private fun enumName(value: Any): String = (value as? Enum<*>)?.name ?: value.toString()

    private fun chipLabels(entries: List<Any>, current: PresetPlan): String =
        entries.joinToString(",") { entry ->
            val value = current.valueOf(enumName(entry)) ?: 0
            when {
                value < 1000 -> String.format(java.util.Locale.US, "%.1f", value / 1000f)
                else -> (value / 1000).toString()
            }
        }

    private fun logFailure(operation: String, throwable: Throwable) {
        if (loggedFailure.compareAndSet(false, true)) {
            CameraModHook.log(
                "Zoom preset $operation failed " +
                    "(${throwable.javaClass.simpleName}: ${throwable.message})"
            )
        }
    }
}
