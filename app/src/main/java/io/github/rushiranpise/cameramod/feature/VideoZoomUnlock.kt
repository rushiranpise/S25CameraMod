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

/**
 * Raises the camera app's own maximum-zoom value for the back camera in photo and video modes.
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
 */
object VideoZoomUnlock {

    private const val MAX_ZOOM_ERROR = "Not supported maximum digital zoom : "
    private const val CAM_CAPABILITY = "com.samsung.android.camera.core2.CamCapability"

    /**
     * 8K video passes l9.a2 resolutions named RESOLUTION_7680X... (7680x4320, 7680x3296,
     * 7680x3248, 7680x2784, 7680x2160 and fps variants). HIGH_RESOLUTION is also used for
     * high-resolution stills (8160x..., 16320x..., 12000x...), which must keep their stock
     * photo ceiling. Matching the 7680 prefix keeps the override on 8K video only.
     */
    private const val HIGH_RES_VIDEO_PREFIX = "RESOLUTION_7680X"

    /**
     * These are the l9.g3 values that belong to still-photo and video capture.
     *
     * NORMAL is the default returned by l9.n2.X for SHOOTING_MODE_PHOTO (gd.i has no override),
     * and is also used by food, hyperlapse and single-bokeh when their seamless-zoom variants are
     * unavailable. Overriding it raises the default photo mode ceiling, which the stock photo
     * slider would otherwise cap at 100x.
     *
     * HIGH_RESOLUTION covers 8K video (p8.n1.d returns it for camcorder resolutions where
     * te.c0.S0 is true), but the same type is also used by high-resolution stills. The hook
     * narrows HIGH_RESOLUTION to RESOLUTION_7680X... video resolutions so 200MP/high-res photos
     * keep their stock ceiling.
     *
     * MULTI_RECORDING is deliberately excluded: p8.n1.a hardcodes 19.9x / 9.9x for dual
     * recording, so overriding this method alone would not lift that path.
     */
    private val ZOOM_TYPES = setOf(
        "NORMAL",
        "VIDEO",
        "LENS_PHYSICAL",
        "PORTRAIT_VIDEO",
        "HIGH_RESOLUTION",
    )

    @Volatile
    private var installed = false

    /** Resolved once; the camera APK has one CamCapability class for all u8.e instances. */
    @Volatile
    private var capabilityField: Field? = null

    @Volatile
    private var facingMethod: Method? = null

    @Volatile
    private var cameraIdMethod: Method? = null

    private val loggedZoomTypes = ConcurrentHashMap.newKeySet<String>()
    private val loggedFailures = ConcurrentHashMap.newKeySet<String>()

    fun install(classLoader: ClassLoader) {
        if (installed) return

        val method = findMaxZoomMethod(classLoader)
        if (method == null) {
            CameraModHook.log("Max-zoom method not found; no zoom hook installed")
            return
        }

        installed = true
        CameraModHook.log(
            "Hooking ${method.declaringClass.name}.${method.name} " +
                "(${method.parameterTypes.joinToString { it.simpleName }}) -> configured ceiling"
        )
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!SettingsBridge.isEnabled(ModFeature.ZOOM)) {
                    logOnce("zoom-disabled", "Zoom toggle is off; leaving stock ceiling")
                    return
                }

                val zoomType = (param.args.getOrNull(0) as? Enum<*>)?.name ?: return
                if (zoomType !in ZOOM_TYPES) return
                if (zoomType == "HIGH_RESOLUTION" && !isHighResVideo(param.args.getOrNull(1))) {
                    return
                }
                if (!isBackCamera(param.thisObject)) return

                var ceiling = SettingsBridge.zoomCeiling()
                if (ceiling > 100f && !ZoomReadoutFix.installed) {
                    ceiling = 100f
                    logOnce(
                        "readout-missing",
                        "Zoom readout fix unavailable; capping the ceiling at 100x",
                    )
                }
                if (ceiling <= 0f) return

                param.result = ceiling

                if (loggedZoomTypes.add(zoomType)) {
                    CameraModHook.log("Back-camera $zoomType max query -> ${ceiling}x")
                }
            }
        })

        CameraModHook.log("Back-camera photo & video zoom hook ready (configured ceiling when enabled)")
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

    private fun logOnce(key: String, message: String) {
        if (loggedFailures.add(key)) {
            CameraModHook.log(message)
        }
    }
}
