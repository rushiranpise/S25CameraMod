package io.github.rushiranpise.cameramod.feature

import android.hardware.camera2.CameraCharacteristics
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.rushiranpise.cameramod.CameraModHook
import io.github.rushiranpise.cameramod.settings.ModFeature
import io.github.rushiranpise.cameramod.settings.SettingsBridge
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Raises the camera app's own maximum-zoom value for the back camera.
 *
 * The ceiling is configurable (default 100x, max 999.9x). [ZoomReadoutFix] extends the stock
 * ZoomRockerView's four text-size buckets to five so fractional readouts like 100.4 display
 * instead of throwing "Invalid string length". If that fix is unavailable, the ceiling is
 * capped at 100x.
 *
 * How the stock value is produced:
 *
 *   u8.e.f(zoomType, resolution) reads
 *   samsung.android.scaler.availableMaxDigitalZoomList from the HAL, indexed by zoom type
 *   (NORMAL = 0, VIDEO = 1, LENS_PHYSICAL = 6, ...), and returns -1 / throws if the entry is
 *   unsupported.
 *
 *   p8.n1.a(cameraId) multiplies that float by 1000 and returns milli-zoom. The UI (p8.e0.B),
 *   the zoom-range clamp (p8.n1.V), and the Pro-video clamp (xd.i.d) all consume that value.
 *
 * Returning the configured value therefore raises both the slider range and the accepted range.
 * The HAL still receives the final crop request; if it rejects or clamps anything above 25x, an
 * APK-side hook cannot fix that.
 *
 * The master [ModFeature.ZOOM] switch keeps the release's original scope: NORMAL, VIDEO,
 * LENS_PHYSICAL, PORTRAIT_VIDEO and 8K video. The remaining zoom types are separately opt-in, so
 * a disabled switch leaves that mode's stock ceiling untouched.
 */
object VideoZoomUnlock {

    private const val MAX_ZOOM_ERROR = "Not supported maximum digital zoom : "
    private const val MULTI_SCAN_ERROR =
        "getMaxZoomLevel : maximum zoom level set because multi scan mode is running. " +
            "maxZoomLevel ="
    private const val CAM_CAPABILITY = "com.samsung.android.camera.core2.CamCapability"
    private const val SETTINGS_CLASS = "vc.j"

    /**
     * 8K video passes l9.a2 resolutions named RESOLUTION_7680X... (7680x4320, 7680x3296,
     * 7680x3248, 7680x2784, 7680x2160 and fps variants). HIGH_RESOLUTION is also used for
     * high-resolution stills (8160x..., 16320x..., 12000x...), which have their own toggle.
     * Matching the 7680 prefix keeps 8K video on the master switch.
     */
    private const val HIGH_RES_VIDEO_PREFIX = "RESOLUTION_7680X"

    @Volatile
    private var installed = false

    /** Resolved once; the camera APK has one CamCapability class for all u8.e instances. */
    @Volatile
    private var capabilityField: Field? = null

    @Volatile
    private var facingMethod: Method? = null

    @Volatile
    private var cameraIdMethod: Method? = null

    /** p8.n1's vc.j field and vc.j.K(), used to detect the multi-scan hardcode. */
    @Volatile
    private var multiScanSettingsField: Field? = null

    @Volatile
    private var multiScanModeMethod: Method? = null

    private val loggedZoomTypes = ConcurrentHashMap.newKeySet<String>()
    private val loggedFailures = ConcurrentHashMap.newKeySet<String>()
    private val multiScanLogged = AtomicBoolean(false)

    fun install(classLoader: ClassLoader) {
        if (installed) return

        val method = findMaxZoomMethod(classLoader)
        if (method == null) {
            CameraModHook.log("Max-zoom method not found; no zoom hook installed")
        } else {
            installed = true
            CameraModHook.log(
                "Hooking ${method.declaringClass.name}.${method.name} " +
                    "(${method.parameterTypes.joinToString { it.simpleName }}) -> configured ceiling"
            )
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val zoomType = (param.args.getOrNull(0) as? Enum<*>)?.name ?: return
                    val feature = featureFor(zoomType, param.args.getOrNull(1)) ?: return
                    if (!SettingsBridge.isEnabled(feature)) {
                        logOnce(
                            "zoom-disabled-${feature.key}",
                            "${feature.label} is off; leaving stock ceiling for $zoomType",
                        )
                        return
                    }
                    if (!isBackCamera(param.thisObject)) return

                    val ceiling = effectiveCeiling() ?: return
                    param.result = ceiling

                    if (loggedZoomTypes.add("${feature.key}:$zoomType")) {
                        CameraModHook.log(
                            "Back-camera $zoomType max query -> ${ceiling}x (${feature.key})"
                        )
                    }
                }
            })
        }

        installMultiScanOverride(classLoader)

        if (installed) {
            CameraModHook.log(
                "Back-camera zoom hooks ready (master switch plus per-mode opt-ins)"
            )
        }
    }

    /**
     * Maps l9.g3 zoom types to the switch that owns them. The master switch deliberately keeps
     * the 1.0.0 scope; everything else is opt-in.
     */
    private fun featureFor(zoomType: String, resolution: Any?): ModFeature? = when (zoomType) {
        "NORMAL", "VIDEO", "LENS_PHYSICAL", "PORTRAIT_VIDEO" -> ModFeature.ZOOM
        "HIGH_RESOLUTION" ->
            if (isHighResVideo(resolution)) ModFeature.ZOOM else ModFeature.HIGH_RES_STILL_ZOOM
        "NIGHT" -> ModFeature.NIGHT_ZOOM
        "PORTRAIT" -> ModFeature.PORTRAIT_ZOOM
        "FOOD" -> ModFeature.FOOD_ZOOM
        "HYPER_LAPSE" -> ModFeature.HYPER_LAPSE_ZOOM
        "MULTI_RECORDING" -> ModFeature.MULTI_RECORDING_ZOOM
        else -> null
    }

    /**
     * p8.n1.a computes the multi-recording ceiling from u8.e.f, then replaces it with 19900 or
     * 9900 when vc.j.K() (MULTI_SCAN_MODE == 1) is true. The u8.e.f override alone cannot lift
     * that path, so this second hook rewrites the hardcoded value only when the user enabled the
     * dual/multi-recording switch.
     */
    private fun installMultiScanOverride(classLoader: ClassLoader) {
        val method = findMultiScanMethod(classLoader)
        if (method == null) {
            CameraModHook.log("Multi-recording max-zoom method not found; leaving 19.9x/9.9x")
            return
        }

        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!SettingsBridge.isEnabled(ModFeature.MULTI_RECORDING_ZOOM)) return

                val owner = param.thisObject ?: return
                if (!isMultiScanMode(owner)) return

                val ceiling = effectiveCeiling() ?: return
                param.result = (ceiling * 1000f).roundToInt()

                if (multiScanLogged.compareAndSet(false, true)) {
                    CameraModHook.log(
                        "Multi-scan max query -> ${ceiling}x (multi_recording_zoom)"
                    )
                }
            }
        })

        CameraModHook.log(
            "Hooking ${method.declaringClass.name}.${method.name} -> multi-scan ceiling override"
        )
    }

    private fun effectiveCeiling(): Float? {
        var ceiling = SettingsBridge.zoomCeiling()
        if (ceiling > 100f && !ZoomReadoutFix.installed) {
            ceiling = 100f
            logOnce(
                "readout-missing",
                "Zoom readout fix unavailable; capping the ceiling at 100x",
            )
        }
        return ceiling.takeIf { it > 0f }
    }

    private fun findMaxZoomMethod(classLoader: ClassLoader): Method? {
        val fromDexKit = try {
            System.loadLibrary("dexkit")
            DexKitBridge.create(classLoader, true).use { bridge ->
                bridge.findMethod {
                    matcher {
                        returnType = "float"
                        paramCount = 2
                        usingStrings(MAX_ZOOM_ERROR)
                    }
                }.singleOrNull()?.getMethodInstance(classLoader)
            }
        } catch (t: Throwable) {
            CameraModHook.log(
                "DexKit lookup failed (${t.javaClass.simpleName}: ${t.message}); using symbol fallback"
            )
            null
        }

        return fromDexKit ?: fallbackLookup(classLoader)
    }

    private fun findMultiScanMethod(classLoader: ClassLoader): Method? {
        val fromDexKit = try {
            System.loadLibrary("dexkit")
            DexKitBridge.create(classLoader, true).use { bridge ->
                bridge.findMethod {
                    matcher {
                        returnType = "int"
                        paramCount = 1
                        usingStrings(MULTI_SCAN_ERROR)
                    }
                }.singleOrNull()?.getMethodInstance(classLoader)
            }
        } catch (t: Throwable) {
            CameraModHook.log(
                "DexKit multi-scan lookup failed " +
                    "(${t.javaClass.simpleName}: ${t.message}); using symbol fallback"
            )
            null
        }

        if (fromDexKit != null) return fromDexKit

        return try {
            val clazz = Class.forName("p8.n1", false, classLoader)
            val method = clazz.declaredMethods.firstOrNull {
                it.name == "a" &&
                    it.returnType == Int::class.javaPrimitiveType &&
                    it.parameterCount == 1 &&
                    it.parameterTypes[0].isEnum
            }
            if (method != null) {
                CameraModHook.log("Using fixed p8.n1.a fallback for this Camera build")
            }
            method
        } catch (t: Throwable) {
            CameraModHook.log(
                "Multi-scan symbol fallback failed: ${t.javaClass.simpleName}: ${t.message}"
            )
            null
        }
    }

    /**
     * Last-resort lookup for the exact build this module was developed against
     * (Camera 17.0.00.43). DexKit is the primary path and should be the only one needed.
     */
    private fun fallbackLookup(classLoader: ClassLoader): Method? {
        return try {
            val clazz = Class.forName("u8.e", false, classLoader)
            val method = clazz.declaredMethods.firstOrNull {
                it.name == "f" &&
                    it.returnType == Float::class.javaPrimitiveType &&
                    it.parameterCount == 2 &&
                    it.parameterTypes[0].isEnum &&
                    it.parameterTypes[1].name == "l9.a2"
            }
            if (method != null) {
                CameraModHook.log("Using fixed u8.e.f fallback for this Camera build")
            }
            method
        } catch (t: Throwable) {
            CameraModHook.log("Symbol fallback failed: ${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }

    private fun isHighResVideo(resolution: Any?): Boolean =
        (resolution as? Enum<*>)?.name?.startsWith(HIGH_RES_VIDEO_PREFIX) == true

    /**
     * u8.e holds a CamCapability whose d() is CameraCharacteristics.LENS_FACING. The field and
     * method are obfuscated in the camera APK, so resolve them by type and cache the handles.
     */
    private fun isBackCamera(zoomOwner: Any?): Boolean {
        if (zoomOwner == null) return false

        return try {
            val capability = capabilityOf(zoomOwner) ?: return false

            val facing = facingMethod?.invoke(capability) as? Int
            if (facing != null) {
                return facing == CameraCharacteristics.LENS_FACING_BACK
            }

            // d() should never be null on a real device. If it is, prefer the camera id
            // ("0" is the logical back camera on Samsung devices) and only then fail open.
            val cameraId = cameraIdMethod?.invoke(capability) as? String
            when {
                cameraId != null -> cameraId == "0"
                else -> {
                    logOnce(
                        "facing-null",
                        "CamCapability facing and camera id are both unavailable; hooking anyway",
                    )
                    true
                }
            }
        } catch (t: Throwable) {
            // Never let the safety check disable the feature: if the internals moved, prefer the
            // hook and let the user verify. The log entry makes this diagnosable.
            logOnce(
                "facing-error",
                "Back-camera check failed (${t.javaClass.simpleName}: ${t.message}); hooking anyway",
            )
            true
        }
    }

    private fun capabilityOf(zoomOwner: Any): Any? {
        capabilityField?.let { field ->
            field.isAccessible = true
            return field.get(zoomOwner)
        }

        var clazz: Class<*>? = zoomOwner.javaClass
        while (clazz != null && clazz != Any::class.java) {
            val field = clazz.declaredFields.firstOrNull { it.type.name == CAM_CAPABILITY }
            if (field != null) {
                field.isAccessible = true
                val capability = field.get(zoomOwner)
                if (capability != null) {
                    capabilityField = field
                    facingMethod = capability.javaClass.getMethod("d")
                    cameraIdMethod = capability.javaClass.getMethod("a")
                    return capability
                }
            }
            clazz = clazz.superclass
        }

        logOnce("capability-missing", "CamCapability field not found on ${zoomOwner.javaClass.name}")
        return null
    }

    private fun isMultiScanMode(zoomOwner: Any): Boolean {
        return try {
            val settings = settingsOf(zoomOwner) ?: return false
            val method = multiScanModeMethod ?: settings.javaClass.methods.firstOrNull {
                it.name == "K" &&
                    it.parameterCount == 0 &&
                    it.returnType == Boolean::class.javaPrimitiveType
            } ?: return false
            multiScanModeMethod = method
            method.invoke(settings) == true
        } catch (t: Throwable) {
            logOnce(
                "multi-scan-check",
                "Multi-scan mode check failed " +
                    "(${t.javaClass.simpleName}: ${t.message}); leaving stock ceiling",
            )
            false
        }
    }

    private fun settingsOf(zoomOwner: Any): Any? {
        multiScanSettingsField?.let { field ->
            field.isAccessible = true
            return field.get(zoomOwner)
        }

        var clazz: Class<*>? = zoomOwner.javaClass
        while (clazz != null && clazz != Any::class.java) {
            val field = clazz.declaredFields.firstOrNull { it.type.name == SETTINGS_CLASS }
            if (field != null) {
                field.isAccessible = true
                val settings = field.get(zoomOwner)
                if (settings != null) {
                    multiScanSettingsField = field
                    return settings
                }
            }
            clazz = clazz.superclass
        }

        return null
    }

    private fun logOnce(key: String, message: String) {
        if (loggedFailures.add(key)) {
            CameraModHook.log(message)
        }
    }
}
