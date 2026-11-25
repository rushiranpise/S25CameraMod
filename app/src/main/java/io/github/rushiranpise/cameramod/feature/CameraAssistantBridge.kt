package io.github.rushiranpise.cameramod.feature

import android.app.AndroidAppHelper
import android.content.ContentProvider
import android.content.Context
import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.rushiranpise.cameramod.CameraModHook
import io.github.rushiranpise.cameramod.settings.ModFeature
import io.github.rushiranpise.cameramod.settings.SettingsBridge
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Advertises Camera Assistant settings that this firmware leaves out of the provider reply.
 *
 * Camera Assistant 5.0 renders exactly the keys that
 * `CameraSettingProvider.call("get", <assistant versionCode>, null)` returns in `SettingKeys`.
 * Camera builds that list in `te.h0.l(String)` by filtering its key table against the assistant
 * version, so keys that exist in both apps can still never reach the UI. The assistant also sends
 * every unknown key back through `remove`, so only keys from its own enum may be appended.
 *
 * The hook appends the missing keys and their current values to the existing bundle. The stock
 * pipeline does the rest: the assistant shows its normal preference, reads the value from the same
 * bundle and writes it back with `set`. The assistant APK is not modified, so its signature-level
 * `CameraSettingProvider.READ_WRITE` permission keeps working.
 */
object CameraAssistantBridge {

    private const val PROVIDER_CLASS = "com.sec.android.app.camera.setting.CameraSettingProvider"
    private const val CALL_METHOD = "call"
    private const val COMMAND_GET = "get"
    private const val KEY_SETTING_KEYS = "SettingKeys"

    /** `te.c0.k1(Context, key, default)` reads the stored Camera preference value. */
    private const val VALUE_HELPER_CLASS = "te.c0"
    private const val VALUE_HELPER_METHOD = "k1"

    /** `l9.f0` is Camera's setting enum; its `b()` method returns the provider key. */
    private const val SETTING_ENUM_CLASS = "l9.f0"
    private const val SETTING_KEY_METHOD = "b"

    /** `ag.c.d(l9.f0)` returns the stock default value for one setting. */
    private const val DEFAULT_HELPER_CLASS = "ag.c"
    private const val DEFAULT_HELPER_METHOD = "d"

    /**
     * The 73 Camera Assistant 5.0.00.7 `c1.f` keys that Camera also has a setting enum for.
     *
     * The assistant drops every key outside its own enum and sends it back through `remove`, so
     * the list must stay limited to names it knows. Assistant-only keys such as `usb_storage`,
     * `fast_shutter`, and `ai_zoom_x2` are excluded because Camera's provider cannot apply them.
     *
     * `UNSUPPORTED_KEYS` is then removed. Camera filters those keys out on this model for a
     * reason: Mirror view is a foldable-only shooting mode and this firmware has no
     * `SHOOTING_MODE_MIRROR` entry in `/system/cameradata/camera-feature.xml`, so opening it
     * crashes in `gd.e` -> `l9.a2.k("getResolution : argument is invalid - null")`. The two 24 MP
     * keys are excluded for the HAL limitation documented in the README.
     */
    private val UNSUPPORTED_KEYS = setOf(
        "keep_24mp_resolution",
        "mirror_from_lock_screen",
        "mirror_from_quick_launch",
        "mirror_view",
        "picture_resolution_24mp",
    )

    private val ASSISTANT_KEYS = listOf(
        "adaptive_pixel",
        "af_shift_sensitivity_photo",
        "af_shift_sensitivity_video",
        "af_transition_speed_photo",
        "af_transition_speed_video",
        "anamorphic_lens_type",
        "auto_hdr",
        "auto_lens_switching",
        "auto_zoom_style",
        "camera_timeout",
        "capture_when_pressed",
        "clean_hdmi",
        "crop_zoom_x10",
        "crop_zoom_x2",
        "customize_indicators",
        "customize_indicators_photo_exposure_value",
        "customize_indicators_photo_face",
        "customize_indicators_photo_filter",
        "customize_indicators_photo_flash",
        "customize_indicators_photo_motion_photo",
        "customize_indicators_photo_ratio",
        "customize_indicators_photo_resolution",
        "customize_indicators_photo_timer",
        "customize_indicators_photo_white_balance",
        "customize_indicators_video_auto_framing",
        "customize_indicators_video_exposure_value",
        "customize_indicators_video_filter",
        "customize_indicators_video_flash",
        "customize_indicators_video_ratio",
        "customize_indicators_video_resolution",
        "customize_indicators_video_super_steady",
        "digital_zoom_upscale",
        "directors_view_mode",
        "distortion_correction",
        "dof_adapter",
        "dual_recording_mode",
        "focus_peaking_always_on",
        "focus_peaking_color",
        "focus_peaking_intensity",
        "focus_priority_mode",
        "hdr10_plus",
        "inactivity_timer",
        "keep_24mp_resolution",
        "maximum_iso",
        "minimum_shutter_speed",
        "mirror_from_lock_screen",
        "mirror_from_quick_launch",
        "mirror_view",
        "motion_photo_shutter_sound_timing",
        "motion_photo_shutter_sound_type",
        "night_mode",
        "night_video_menu",
        "optical_image_stabilization",
        "picture_resolution_24mp",
        "pro_mode_presets",
        "quick_take",
        "recording_8k_when_mirroring",
        "recording_audio_monitoring",
        "recording_inactivity_timer",
        "reset_camera_assistant_settings",
        "single_take_mode",
        "soften_picture",
        "soften_picture_v2",
        "stereo_capture",
        "swipe_shutter_button_to",
        "tilta_wireless_lens_controller",
        "timer_interval",
        "timer_shot_count",
        "timer_shot_interval",
        "touch_af_ae_in_pro_video_mode",
        "video_softening",
        "zoom_x100",
        "zoom_x2",
    )

    private val reported = AtomicBoolean(false)
    private val failureReported = AtomicBoolean(false)
    private val defaultFailureReported = AtomicBoolean(false)

    @Volatile
    private var valueLookup: Method? = null

    @Volatile
    private var settingEnum: Class<*>? = null

    @Volatile
    private var settingKeyLookup: Method? = null

    @Volatile
    private var defaultLookup: Method? = null

    @Volatile
    private var defaults: Map<String, Int>? = null

    @Volatile
    private var lastAppended: Set<String> = emptySet()

    fun install(classLoader: ClassLoader) {
        val method = findCallMethod(classLoader)
        if (method == null) {
            CameraModHook.log("$PROVIDER_CLASS.$CALL_METHOD not found; Assistant bridge unavailable")
            return
        }
        valueLookup = findValueLookup(classLoader)
        settingEnum = findClass(SETTING_ENUM_CLASS, classLoader)
        settingKeyLookup = findNoArgStringMethod(settingEnum, SETTING_KEY_METHOD)
        defaultLookup = findDefaultLookup(classLoader)

        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    onProviderCall(param)
                } catch (t: Throwable) {
                    if (failureReported.compareAndSet(false, true)) {
                        CameraModHook.log(
                            "Assistant bridge failed (${t.javaClass.simpleName}: ${t.message})"
                        )
                    }
                }
            }
        })

        CameraModHook.log(
            "Assistant bridge installed (${ASSISTANT_KEYS.size} candidate keys)"
        )
    }

    private fun onProviderCall(param: XC_MethodHook.MethodHookParam) {
        if (param.args.getOrNull(0) as? String != COMMAND_GET) return

        val bundle = param.result as? Bundle ?: return
        @Suppress("DEPRECATION")
        val rawKeys = bundle.getSerializable(KEY_SETTING_KEYS) as? ArrayList<*> ?: return
        @Suppress("UNCHECKED_CAST")
        val keys = rawKeys as ArrayList<Any?>

        val advertised = keys.mapNotNullTo(mutableSetOf()) { it as? String }
        if (reported.compareAndSet(false, true)) {
            CameraModHook.log("Assistant query: ${advertised.size} advertised setting keys")
        }

        if (!SettingsBridge.isEnabled(ModFeature.CAMERA_ASSISTANT_BRIDGE)) return

        val missing = ASSISTANT_KEYS.filterNot { it in advertised || it in UNSUPPORTED_KEYS }
        if (missing.isEmpty()) return

        val context = (param.thisObject as? ContentProvider)?.context
            ?: AndroidAppHelper.currentApplication()
        val values = resolveDefaults()
        missing.forEach { key ->
            keys.add(key)
            bundle.putInt(key, readValue(context, key, values[key] ?: 0))
        }

        val appended = missing.toSet()
        if (appended != lastAppended) {
            lastAppended = appended
            CameraModHook.log(
                "Assistant bridge: added ${missing.size} keys -> ${missing.joinToString(",")}"
            )
        }
    }

    private fun readValue(context: Context?, key: String, default: Int): Int {
        val method = valueLookup ?: return default
        val appContext = context ?: return default
        return try {
            method.invoke(null, appContext, key, default) as? Int ?: default
        } catch (t: Throwable) {
            default
        }
    }

    /**
     * Resolves the stock default for each candidate key once per process.
     *
     * `sc.n0` fills the provider reply with `te.c0.k1(context, key, ag.c.d(setting))`, so using the
     * same default keeps an unhidden switch showing the value Camera itself would show instead of
     * a misleading zero.
     */
    @Synchronized
    private fun resolveDefaults(): Map<String, Int> {
        defaults?.let { return it }

        val enumClass = settingEnum
        val keyMethod = settingKeyLookup
        val defaultMethod = defaultLookup
        if (enumClass == null || keyMethod == null || defaultMethod == null) {
            return emptyMap()
        }

        val wanted = ASSISTANT_KEYS.toHashSet()
        val resolved = HashMap<String, Int>(ASSISTANT_KEYS.size)
        try {
            enumClass.enumConstants?.forEach { constant ->
                val key = try {
                    keyMethod.invoke(constant) as? String
                } catch (t: Throwable) {
                    null
                } ?: return@forEach
                if (key !in wanted) return@forEach
                resolved[key] = try {
                    defaultMethod.invoke(null, constant) as? Int ?: 0
                } catch (t: Throwable) {
                    0
                }
            }
        } catch (t: Throwable) {
            if (defaultFailureReported.compareAndSet(false, true)) {
                CameraModHook.log(
                    "Assistant default lookup failed (${t.javaClass.simpleName}: ${t.message})"
                )
            }
            return emptyMap()
        }

        if (resolved.size < wanted.size && defaultFailureReported.compareAndSet(false, true)) {
            val unresolved = wanted - resolved.keys
            CameraModHook.log(
                "Assistant defaults missing for ${unresolved.size} keys: " +
                    unresolved.joinToString(",")
            )
        }
        defaults = resolved
        return resolved
    }

    private fun findCallMethod(classLoader: ClassLoader): Method? = try {
        Class.forName(PROVIDER_CLASS, false, classLoader)
            .declaredMethods
            .firstOrNull { method ->
                method.name == CALL_METHOD &&
                    method.parameterCount == 3 &&
                    method.parameterTypes[0] == String::class.java &&
                    method.parameterTypes[1] == String::class.java &&
                    method.parameterTypes[2] == Bundle::class.java &&
                    Bundle::class.java.isAssignableFrom(method.returnType)
            }
    } catch (t: Throwable) {
        CameraModHook.log(
            "Assistant provider lookup failed (${t.javaClass.simpleName}: ${t.message})"
        )
        null
    }

    private fun findValueLookup(classLoader: ClassLoader): Method? = try {
        Class.forName(VALUE_HELPER_CLASS, false, classLoader)
            .declaredMethods
            .firstOrNull { method ->
                method.name == VALUE_HELPER_METHOD &&
                    Modifier.isStatic(method.modifiers) &&
                    method.returnType == Int::class.javaPrimitiveType &&
                    method.parameterCount == 3 &&
                    method.parameterTypes[0] == Context::class.java &&
                    method.parameterTypes[1] == String::class.java &&
                    method.parameterTypes[2] == Int::class.javaPrimitiveType
            }
            ?.apply { isAccessible = true }
    } catch (t: Throwable) {
        CameraModHook.log(
            "Assistant value lookup failed (${t.javaClass.simpleName}: ${t.message}); using 0"
        )
        null
    }

    private fun findClass(name: String, classLoader: ClassLoader): Class<*>? = try {
        Class.forName(name, false, classLoader)
    } catch (t: Throwable) {
        CameraModHook.log(
            "Assistant class $name not found (${t.javaClass.simpleName}: ${t.message})"
        )
        null
    }

    private fun findNoArgStringMethod(owner: Class<*>?, name: String): Method? = try {
        owner?.getMethod(name)?.apply { isAccessible = true }
    } catch (t: Throwable) {
        CameraModHook.log(
            "Assistant method $name not found (${t.javaClass.simpleName}: ${t.message})"
        )
        null
    }

    private fun findDefaultLookup(classLoader: ClassLoader): Method? {
        val enumClass = settingEnum ?: return null
        return try {
            Class.forName(DEFAULT_HELPER_CLASS, false, classLoader)
                .declaredMethods
                .firstOrNull { method ->
                    method.name == DEFAULT_HELPER_METHOD &&
                        Modifier.isStatic(method.modifiers) &&
                        method.returnType == Int::class.javaPrimitiveType &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0] == enumClass
                }
                ?.apply { isAccessible = true }
        } catch (t: Throwable) {
            CameraModHook.log(
                "Assistant default helper not found (${t.javaClass.simpleName}: ${t.message})"
            )
            null
        }
    }
}
