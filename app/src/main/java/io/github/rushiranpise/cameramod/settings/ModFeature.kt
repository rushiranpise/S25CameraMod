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
 * One independently switchable camera modification.
 *
 * The keys are persisted by the module app and read by the camera process through
 * [ModSettingsProvider]. Only the zoom unlock defaults to on, matching the previous
 * release; every experimental feature is opt-in so a disabled switch leaves stock behavior.
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

    DUAL_RECORDING_V2("dual_recording_v2", "Dual Recording V2", false, FeatureTab.VIDEO),
    PORTRAIT_VIDEO_4K("portrait_video_4k", "4K portrait video", false, FeatureTab.VIDEO),
    SUPER_STEADY_4K60("super_steady_4k60", "Super steady 4K60", false, FeatureTab.VIDEO),
    HDR_8K("hdr_8k", "8K HDR10+ / HLG", false, FeatureTab.VIDEO),
    TELE_120("tele_120fps", "Tele 120 fps", false, FeatureTab.VIDEO),
    APV("apv_recording", "APV recording", false, FeatureTab.VIDEO),
    NIGHT_ZOOM("night_zoom", "Night mode zoom", false, FeatureTab.CAMERA),
    PORTRAIT_ZOOM("portrait_zoom", "Portrait zoom", false, FeatureTab.CAMERA),
    FOOD_ZOOM("food_zoom", "Food zoom", false, FeatureTab.CAMERA),
    HIGH_RES_STILL_ZOOM(
        "high_res_still_zoom",
        "High-res photo zoom",
        false,
        FeatureTab.CAMERA,
    ),
    PORTRAIT_FILTER("portrait_filter", "Portrait filters", false, FeatureTab.CAMERA),
    NATURAL_BLUR("natural_blur", "Natural blur video", false, FeatureTab.VIDEO),
    DETAIL_ENHANCER_BUTTON(
        "detail_enhancer_button",
        "Detail enhancer button",
        false,
        FeatureTab.CAMERA,
    ),
    MOTION_PHOTO_50MP(
        "motion_photo_50mp",
        "50 MP motion photo",
        false,
        FeatureTab.CAMERA,
    ),
    MOTION_PHOTO_24MP(
        "motion_photo_24mp",
        "24 MP motion photo",
        false,
        FeatureTab.CAMERA,
    ),
    FUSION_HIGH_RESOLUTION(
        "fusion_high_resolution",
        "Fusion high resolution",
        false,
        FeatureTab.CAMERA,
    ),
    PHOTO_MAX_NIGHT_SHOT(
        "photo_max_night_shot",
        "Photo max night shot",
        false,
        FeatureTab.CAMERA,
    ),
    AI_HIGH_RES_MEMORY_CHECK(
        "ai_high_res_memory_check",
        "AI high-res memory check",
        false,
        FeatureTab.CAMERA,
    ),
    BACK_MEDIUM_RESOLUTION(
        "back_medium_resolution",
        "Back 24 MP resolution",
        false,
        FeatureTab.CAMERA,
    ),
}
