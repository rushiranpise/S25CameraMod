package io.github.rushiranpise.cameramod.feature

import android.util.Range
import android.util.SparseIntArray
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.rushiranpise.cameramod.CameraModHook
import io.github.rushiranpise.cameramod.settings.ModFeature
import io.github.rushiranpise.cameramod.settings.SettingsBridge
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Keeps the zoom tick slider usable across the module's raised zoom ceiling.
 *
 * The stock tick track is built by [ZoomTickSlider.b]. Every shortcut pair maps to a bar count
 * through Range(previous, current) in the presenter's sparse map. If the range is missing, that
 * shortcut gets no ticks and no anchor, and the zoom→scroll array (y9.g.d) stops at the previous
 * shortcut. y9.g.c(zoom) then returns -1 above that key and ZoomView.C2 drops the frame
 * ("translateZoomSlider, scroll value is wrong"), which is why module-enabled zoom freezes on the
 * slider while the zoom level keeps rising.
 *
 * The default preset tail 25 -> 100 hits exactly that case: the stock map has 30000 -> 100000 but
 * not 25000 -> 100000.
 *
 * This feature fixes both halves of the track:
 *
 *   1. Adds the bar count for every missing pair in the displayed shortcut list. Preset ranges
 *      such as 25 -> 100 and 100 -> 200 now create their ticks and anchors.
 *   2. When the largest shortcut is below the mode's real maximum (for example presets 25/100
 *      with a 200x ceiling), appends tick views for the remaining range and records a terminal
 *      key at the far-right scroll position. The terminal key equals y9.g.getMaxZoomLevel()'s
 *      counterpart in onScrollChange, so dragging and the stock zoom animation both reach it.
 *
 * The real maximum comes from p8.e0.B(), the same value ZoomView.m(cd.b) reads before building
 * the shortcut list. It already contains the multi-scan clamp and is the front camera's stock
 * value when the back-camera hook does not apply, so the track never claims more range than the
 * camera accepts. The value is cached per thread because the list is built and laid out on the
 * main thread.
 *
 * The tick track geometry matters: z9.n stores anchor positions relative to the LinearLayout
 * content, and y9.g.C2 sets scrollX directly. End padding means the last anchor's position is
 * slightly past maxScroll, so an anchor added at maxScroll only works if real ticks have already
 * widened the track. That is why the tail ticks are added as z9.a views instead of a bare
 * SparseIntArray entry.
 */
object ZoomSliderFix {

    private const val SCROLL_CLASS = "y9.g"
    private const val TICK_SLIDER_CLASS =
        "com.sec.android.app.camera.layer.keyscreen.zoom.widget.ZoomTickSlider"
    private const val CAMERA_CONTEXT_CLASS = "p8.e0"
    private const val TICK_VIEW_CLASS = "z9.a"

    /** Eight minor ticks per doubling, close to the stock 5x -> 10x density. */
    private const val TICKS_PER_OCTAVE = 8.0

    /** The stock map never uses more than 16 ticks in one segment. */
    private const val MAX_TICKS = 16

    /** Two ticks are the minimum that actually widens the track past the last anchor. */
    private const val MIN_TAIL_TICKS = 2

    /** Extra ticks used when the first tail is still too short for the terminal anchor. */
    private const val EXTRA_TAIL_TICKS = 8

    /** Bounded growth passes, so a zero-width tick resource cannot loop forever. */
    private const val MAX_TAIL_ATTEMPTS = 4

    @Volatile
    private var installed = false

    @Volatile
    private var sliderField: Field? = null

    @Volatile
    private var arrayField: Field? = null

    @Volatile
    private var tickFactory: Method? = null

    @Volatile
    private var tickImageField: Field? = null

    @Volatile
    private var tickPaddingField: Field? = null

    /** p8.e0.B() is the effective mode maximum, including the multi-scan clamp. */
    private val maxZoomMilli = ThreadLocal<Int>()

    /** Last shortcut value of the list handed to the current tick-slider rebuild. */
    private val lastShortcutMilli = ThreadLocal<Int>()

    // The slider and listener are only touched from the main thread.
    private var terminalSlider: View? = null
    private var terminalListener: View.OnLayoutChangeListener? = null
    private var tailAttempts = 0
    private var tailPending = false

    private val loggedTail = AtomicInteger(-1)
    private val loggedTerminal = AtomicInteger(-1)
    private val loggedFailure = AtomicBoolean(false)
    private val loggedShortTrack = AtomicBoolean(false)

    fun install(classLoader: ClassLoader) {
        if (installed) return

        val scrollHooked = hookScroll(classLoader)
        val tickHooked = hookTickSlider(classLoader)
        val maxHooked = hookMaxZoom(classLoader)
        installed = scrollHooked || tickHooked

        if (installed) {
            CameraModHook.log(
                "Zoom slider fix ready (scroll=$scrollHooked, ticks=$tickHooked, max=$maxHooked)"
            )
        } else {
            CameraModHook.log("Zoom slider classes not found; leaving the stock tick track")
        }
    }

    private fun hookScroll(classLoader: ClassLoader): Boolean {
        val clazz = try {
            Class.forName(SCROLL_CLASS, false, classLoader)
        } catch (t: Throwable) {
            CameraModHook.log("Zoom slider class missing: $SCROLL_CLASS")
            return false
        }

        val method = clazz.declaredMethods.firstOrNull {
            it.name == "f" && it.parameterCount == 0 && it.returnType == Void.TYPE
        }
        if (method == null) {
            CameraModHook.log("Zoom slider rebuild method not found in $SCROLL_CLASS")
            return false
        }

        sliderField = findFieldByTypeName(clazz, TICK_SLIDER_CLASS)
        arrayField = findField(clazz, SparseIntArray::class.java)

        if (sliderField == null || arrayField == null) {
            CameraModHook.log("Zoom slider fields changed; terminal tick disabled")
            return false
        }

        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                detachTerminal()
                if (!SettingsBridge.isEnabled(ModFeature.ZOOM)) return

                val scroll = param.thisObject as? View ?: return
                val max = maxZoomMilli.get() ?: return
                val last = lastShortcutMilli.get() ?: return
                if (max <= last) return

                val slider = sliderField?.get(scroll) as? View ?: return
                val ticks = arrayField?.get(scroll) as? SparseIntArray ?: return

                // The array is filled by z9.n layout listeners, so it is still empty here on a
                // fresh track. attachTerminal waits for layout before writing the terminal key.
                attachTerminal(scroll, slider, ticks, last, max)
            }
        })
        return true
    }

    private fun hookTickSlider(classLoader: ClassLoader): Boolean {
        val clazz = try {
            Class.forName(TICK_SLIDER_CLASS, false, classLoader)
        } catch (t: Throwable) {
            CameraModHook.log("Zoom tick slider class missing: $TICK_SLIDER_CLASS")
            return false
        }

        val method = clazz.declaredMethods.firstOrNull {
            it.name == "b" &&
                it.parameterCount == 2 &&
                List::class.java.isAssignableFrom(it.parameterTypes[0]) &&
                Map::class.java.isAssignableFrom(it.parameterTypes[1])
        }
        if (method == null) {
            CameraModHook.log("Zoom tick slider initialize method not found")
            return false
        }

        // a(int imageRes, int leftPadding) appends one z9.a tick view. The stock minor ticks use
        // field d (image) and f (left padding); e is the larger shortcut anchor. Reusing the
        // minor tick factory keeps the added children valid for the onScrollChange child casts.
        tickFactory = clazz.declaredMethods.firstOrNull {
            it.name == "a" &&
                it.parameterCount == 2 &&
                it.parameterTypes[0] == Int::class.javaPrimitiveType &&
                it.parameterTypes[1] == Int::class.javaPrimitiveType &&
                it.returnType.name == TICK_VIEW_CLASS
        }
        tickImageField = clazz.declaredFields.firstOrNull {
            it.name == "d" && it.type == Int::class.javaPrimitiveType
        }?.apply { isAccessible = true }
        tickPaddingField = clazz.declaredFields.firstOrNull {
            it.name == "f" && it.type == Int::class.javaPrimitiveType
        }?.apply { isAccessible = true }

        if (tickFactory == null || tickImageField == null || tickPaddingField == null) {
            CameraModHook.log("Zoom tick factory changed; tail ticks disabled")
        }

        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                lastShortcutMilli.remove()
                removeStaleTerminalArray()

                if (!SettingsBridge.isEnabled(ModFeature.ZOOM)) return

                val max = maxZoomMilli.get() ?: return
                if (max <= 0) return

                val original = param.args.getOrNull(0) as? List<*> ?: return
                @Suppress("UNCHECKED_CAST")
                val map = param.args.getOrNull(1) as? MutableMap<Any, Any> ?: return
                if (original.isEmpty()) return

                val list = trimToMax(original, max)
                if (list !== original) {
                    param.args[0] = list
                }

                lastShortcutMilli.set(extendBarCounts(list, map) ?: return)
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                if (!SettingsBridge.isEnabled(ModFeature.ZOOM)) return

                val max = maxZoomMilli.get() ?: return
                val last = lastShortcutMilli.get() ?: return
                if (max <= last) return

                val slider = param.thisObject as? View ?: return
                param.result as? SparseIntArray ?: return

                // b() has removed the old children, so append after the stock track is built.
                appendTailTicks(slider, last, max)
            }
        })
        return true
    }

    private fun hookMaxZoom(classLoader: ClassLoader): Boolean {
        val clazz = try {
            Class.forName(CAMERA_CONTEXT_CLASS, false, classLoader)
        } catch (t: Throwable) {
            CameraModHook.log("Zoom max class missing: $CAMERA_CONTEXT_CLASS")
            return false
        }

        val method = clazz.declaredMethods.firstOrNull {
            it.name == "B" && it.parameterCount == 0 && it.returnType == Int::class.javaPrimitiveType
        }
        if (method == null) {
            CameraModHook.log("Zoom max method not found in $CAMERA_CONTEXT_CLASS")
            return false
        }

        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val value = param.result as? Int ?: return
                if (value > 0) maxZoomMilli.set(value)
            }
        })
        return true
    }

    /**
     * Removes shortcuts above the current mode maximum. The list belongs to the chip adapters
     * too, so it is only replaced for this call, and a single entry is always kept because
     * ZoomTickSlider.b reads list[0].
     */
    private fun trimToMax(list: List<*>, max: Int): List<*> {
        val kept = ArrayList<Any>(list.size)
        for (entry in list) {
            val value = entry?.let { ZoomPresets.shortcutValue(it) } ?: return list
            if (value in 1..max) {
                kept.add(entry)
            }
        }

        return when {
            kept.isEmpty() -> list.take(1)
            kept.size < list.size -> kept
            else -> list
        }
    }

    /**
     * Adds a log-scaled bar count for every pair the stock map does not know, so each shortcut's
     * closing anchor is laid out and recorded in the tick SparseIntArray. Returns the largest
     * effective value in the list.
     */
    private fun extendBarCounts(list: List<*>, map: MutableMap<Any, Any>): Int? {
        val values = ArrayList<Int>(list.size)
        for (entry in list) {
            val value = entry?.let { ZoomPresets.shortcutValue(it) } ?: return null
            values.add(value)
        }
        if (values.isEmpty()) return null

        var added = 0
        for (index in 1 until values.size) {
            val lower = values[index - 1]
            val upper = values[index]
            if (upper <= lower) continue

            val range = Range(lower, upper)
            if (map.containsKey(range)) continue

            try {
                map[range] = tickCount(lower, upper)
                added++
            } catch (t: Throwable) {
                logFailure("bar-count map", t)
                return null
            }
        }

        if (added > 0) {
            CameraModHook.log("Zoom slider added $added tick ranges up to ${values.last() / 1000f}x")
        }
        return values.last()
    }

    /**
     * Eight ticks per doubling keeps wide custom segments from collapsing into one gap. The
     * minimum is 2 because one extra tick does not widen the track past the last anchor.
     */
    private fun tickCount(lower: Int, upper: Int): Int {
        if (lower <= 0 || upper <= lower) return MIN_TAIL_TICKS
        val octaves = ln(upper.toDouble() / lower.toDouble()) / ln(2.0)
        return (octaves * TICKS_PER_OCTAVE).roundToInt().coerceIn(MIN_TAIL_TICKS, MAX_TICKS)
    }

    /** Appends the ticks between the last shortcut and the mode maximum. */
    private fun appendTailTicks(slider: View, last: Int, max: Int) {
        val count = tickCount(last, max)
        if (!appendTicks(slider, count)) return

        if (loggedTail.getAndSet(max) != max) {
            CameraModHook.log(
                "Zoom slider extended ${last / 1000f}x -> ${max / 1000f}x with $count ticks"
            )
        }
    }

    /** Adds [count] minor tick views through the same factory the stock track uses. */
    private fun appendTicks(slider: View, count: Int): Boolean {
        val factory = tickFactory ?: return false
        val resId: Int
        val padding: Int
        try {
            resId = tickImageField?.getInt(slider) ?: 0
            padding = tickPaddingField?.getInt(slider) ?: 0
        } catch (t: Throwable) {
            logFailure("tail resource", t)
            return false
        }
        if (resId == 0 || count <= 0) return false

        return try {
            repeat(count) {
                factory.invoke(slider, resId, padding)
            }
            true
        } catch (t: Throwable) {
            logFailure("tail ticks", t)
            false
        }
    }

    private fun attachTerminal(
        scroll: View,
        slider: View,
        ticks: SparseIntArray,
        last: Int,
        max: Int,
    ) {
        val listener = object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                view: View,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int,
            ) {
                if (arrayField?.get(scroll) !== ticks) return
                applyTerminal(scroll, view, ticks, last, max)
            }
        }

        terminalSlider = slider
        terminalListener = listener
        tailAttempts = 0
        tailPending = false
        slider.addOnLayoutChangeListener(listener)

        // The array can already be laid out when f() runs again for an existing mode.
        applyTerminal(scroll, slider, ticks, last, max)
        slider.post { applyTerminal(scroll, slider, ticks, last, max) }
    }

    /**
     * Records the maximum at the far-right scroll position used by onScrollChange. The last real
     * anchor must already exist, and the extended track must place the edge beyond it; otherwise
     * the mapping would run backwards.
     */
    private fun applyTerminal(
        scroll: View,
        slider: View,
        ticks: SparseIntArray,
        last: Int,
        max: Int,
    ) {
        if (scroll.width <= 0 || slider.width <= 0) return
        if (ticks.indexOfKey(last) < 0) return

        val lastX = ticks.get(last)
        val end = slider.width - scroll.width
        if (end <= lastX) {
            growTrack(slider, lastX, end)
            return
        }
        if (ticks.indexOfKey(max) >= 0 && ticks.get(max) == end) return

        ticks.put(max, end)
        if (loggedTerminal.getAndSet(max) != max) {
            CameraModHook.log("Zoom slider terminal tick at ${max / 1000f}x (scroll=$end)")
        }
    }

    /**
     * The stock anchor stores its right edge plus padding, which can sit past maxScroll until
     * real children widen the content. Add more minor ticks and let the next layout retry.
     */
    private fun growTrack(slider: View, lastX: Int, end: Int) {
        if (tailPending) return
        if (tailAttempts >= MAX_TAIL_ATTEMPTS) {
            if (loggedShortTrack.compareAndSet(false, true)) {
                CameraModHook.log(
                    "Zoom slider track stays short (anchor=$lastX, maxScroll=$end)"
                )
            }
            return
        }

        tailAttempts++
        tailPending = true
        slider.post {
            tailPending = false
            if (appendTicks(slider, EXTRA_TAIL_TICKS)) {
                CameraModHook.log("Zoom slider widened track with $EXTRA_TAIL_TICKS more ticks")
            }
        }
    }

    private fun detachTerminal() {
        val slider = terminalSlider
        val listener = terminalListener
        if (slider != null && listener != null) {
            slider.removeOnLayoutChangeListener(listener)
        }
        terminalSlider = null
        terminalListener = null
    }

    /**
     * y9.g.clear() clears the presenter's bar-count map. Drop the previous array's listener as
     * soon as a new rebuild starts so a late layout cannot write into the stale array.
     */
    private fun removeStaleTerminalArray() {
        detachTerminal()
    }

    private fun findFieldByTypeName(clazz: Class<*>, typeName: String): Field? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            current.declaredFields.firstOrNull { it.type.name == typeName }?.let {
                it.isAccessible = true
                return it
            }
            current = current.superclass
        }
        return null
    }

    private fun findField(clazz: Class<*>, type: Class<*>): Field? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            current.declaredFields.firstOrNull { type.isAssignableFrom(it.type) }?.let {
                it.isAccessible = true
                return it
            }
            current = current.superclass
        }
        return null
    }

    private fun logFailure(operation: String, throwable: Throwable) {
        if (loggedFailure.compareAndSet(false, true)) {
            CameraModHook.log(
                "Zoom slider $operation failed " +
                    "(${throwable.javaClass.simpleName}: ${throwable.message})"
            )
        }
    }
}
