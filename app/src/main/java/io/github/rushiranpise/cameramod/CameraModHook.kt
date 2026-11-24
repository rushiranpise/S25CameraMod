package io.github.rushiranpise.cameramod

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.rushiranpise.cameramod.feature.FeatureMaps
import io.github.rushiranpise.cameramod.feature.PhotoResolutions
import io.github.rushiranpise.cameramod.feature.SettingsKeyFallback
import io.github.rushiranpise.cameramod.feature.StockFlagsReporter
import io.github.rushiranpise.cameramod.feature.SupportFlags
import io.github.rushiranpise.cameramod.feature.VideoZoomUnlock
import io.github.rushiranpise.cameramod.feature.ZoomPresets
import io.github.rushiranpise.cameramod.feature.ZoomReadoutFix

/**
 * Legacy Xposed API entry point, declared in assets/xposed_init.
 *
 * LSPosed loads this in every process it is scoped to. The camera package has one main process
 * plus a few service processes; installing in all of them is harmless and means the hooks are
 * present wherever feature flags or zoom limits are queried.
 */
class CameraModHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return

        log("Loaded in ${lpparam.processName}")

        install("support flags") { SupportFlags.install(lpparam.classLoader) }
        install("stock flag report") { StockFlagsReporter.install(lpparam.classLoader) }
        install("settings key fallback") { SettingsKeyFallback.install(lpparam.classLoader) }
        install("resolution fallback") { PhotoResolutions.install(lpparam.classLoader) }
        install("feature maps") { FeatureMaps.install(lpparam.classLoader) }
        install("zoom readout") { ZoomReadoutFix.install(lpparam.classLoader) }
        install("zoom") { VideoZoomUnlock.install(lpparam.classLoader) }
        install("zoom presets") { ZoomPresets.install(lpparam.classLoader) }
    }

    private fun install(name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            log("$name hook installation failed: ${t.stackTraceToString()}")
        }
    }

    companion object {
        const val TARGET_PACKAGE = "com.sec.android.app.camera"
        private const val LOG_PREFIX = "[S25CameraMod]"

        fun log(message: String) {
            XposedBridge.log("$LOG_PREFIX $message")
        }
    }
}
