package io.github.rushiranpise.cameramod.settings

/**
 * Tabs shown below the zoom control. The labels follow the camera app's own split:
 * video features versus still-camera features.
 */
enum class FeatureTab(val label: String) {
    VIDEO("Video"),
    CAMERA("Camera"),
}

/**
 * One independently switchable zoom or feature-map modification.
 *
 * Raw support flags (`e5.d`) are not listed here anymore: they are discovered from the camera
 * APK and exposed by the flag explorer, which also stores their overrides. The master zoom
 * unlock and the per-mode zoom gates are not feature flags, so they stay in this curated list.
 *
 * The keys are persisted by the module app and read by the camera process through
 * [ModSettingsProvider]. Only the zoom unlock defaults to on, matching the previous release;
 * every experimental feature is opt-in so a disabled switch leaves stock behavior.
 */
enum class ModFeature(
    val key: String,
    val label: String,
    val defaultEnabled: Boolean,
    val tab: FeatureTab?,
) {
    ZOOM("zoom_unlock", "Photo & video zoom", true, null),

    // Additional zoom places, each independent of the master zoom switch above.
    MULTI_RECORDING_ZOOM(
        "multi_recording_zoom",
        "Dual/multi recording zoom",
        false,
        FeatureTab.VIDEO,
    ),
    HYPER_LAPSE_ZOOM("hyper_lapse_zoom", "Hyperlapse zoom", false, FeatureTab.VIDEO),
    SUPER_STEADY_4K60("super_steady_4k60", "Super steady 4K60", false, FeatureTab.VIDEO),
    NIGHT_ZOOM("night_zoom", "Night mode zoom", false, FeatureTab.CAMERA),
    PORTRAIT_ZOOM("portrait_zoom", "Portrait zoom", false, FeatureTab.CAMERA),
    FOOD_ZOOM("food_zoom", "Food zoom", false, FeatureTab.CAMERA),
    HIGH_RES_STILL_ZOOM(
        "high_res_still_zoom",
        "High-res photo zoom",
        false,
        FeatureTab.CAMERA,
    ),

    // Advertises Camera Assistant settings this firmware leaves out of Camera's provider reply.
    // The assistant APK stays untouched, so its signature-level provider permission survives.
    CAMERA_ASSISTANT_BRIDGE(
        "camera_assistant_bridge",
        "Unhide Assistant settings",
        false,
        FeatureTab.CAMERA,
    ),
}
