package io.github.rushiranpise.cameramod.settings

import android.content.Context
import org.json.JSONArray

/** The camera APK's support flags have no category field, so they are grouped by usage. */
enum class FlagCategory(val label: String) {
    VIDEO("Video"),
    CAMERA("Camera"),
}

data class CameraFlag(
    val name: String,
    val category: FlagCategory,
    val summary: String,
)

/**
 * The full `e5.d` support-flag list bundled from Camera 17.0.00.43.
 *
 * The asset only carries names. Stock values arrive from Camera through
 * [ModSettingsProvider] and overrides live in [ModSettings]. Flags discovered by a newer camera
 * build are merged into the UI by [FlagExplorer] even when this catalog does not know them.
 */
object FlagCatalog {

    private const val ASSET_NAME = "flag_catalog.json"

    private val videoFlags = setOf(
        "SUPPORT_CAMERA_ASSISTANT_OPTICAL_IMAGE_STABILIZATION",
        "SUPPORT_HDR10_LABS_TAG",
        "SUPPORT_NEURAL_AUDIO_ZOOM",
        "SUPPORT_SAVE_AS_FLIPPED_IN_MEDIA_RECORDER",
    )

    private val cameraFlags = setOf(
        "DEFAULT_DIGITAL_ZOOM_UPSCALE",
        "DEFAULT_SAVE_AS_FLIPPED",
        "DEFAULT_SCENE_OPTIMIZER",
        "SUPPORT_AUTO_FOCUS",
        "SUPPORT_MULTI_AF",
        "SUPPORT_OBJECT_TRACKING_AF",
        "SUPPORT_QUICK_TAKE",
        "SUPPORT_SEAMLESS_ZOOM",
        "SUPPORT_VDIS_IN_SINGLE_TAKE",
    )

    private val videoKeywords = listOf(
        "VIDEO",
        "CAMCORDER",
        "RECORDING",
        "APV",
        "HYPER_LAPSE",
        "SLOW_MOTION",
        "DIRECTORS_VIEW",
        "STEREO_CAPTURE",
        "AUTO_FRAMING",
        "AUTO_FPS",
        "MIC",
        "HEVC",
        "HIGH_BITRATE",
        "ANTI_SHAKE",
        "MEDIA_RECORDER",
        "HDR10",
        "SUPER_STEADY",
    )

    @Volatile
    private var cached: List<CameraFlag>? = null

    fun load(context: Context): List<CameraFlag> = cached ?: synchronized(this) {
        cached ?: build(context).also { cached = it }
    }

    /** A short explanation for a flag, or a readable fallback for a flag from a newer build. */
    fun describe(name: String): String = flagDescriptions[name] ?: fallbackDescription(name)

    fun categorize(name: String): FlagCategory = when {
        name.startsWith("SUPPORT_SINGLE_TAKE_") || name == "SUPPORT_VDIS_IN_SINGLE_TAKE" ->
            FlagCategory.CAMERA
        name in videoFlags -> FlagCategory.VIDEO
        name in cameraFlags -> FlagCategory.CAMERA
        name.startsWith("IS_") -> FlagCategory.CAMERA
        name.startsWith("DEFAULT_") ->
            if (name.contains("MIC")) FlagCategory.VIDEO else FlagCategory.CAMERA
        videoKeywords.any(name::contains) -> FlagCategory.VIDEO
        else -> FlagCategory.CAMERA
    }

    private fun build(context: Context): List<CameraFlag> {
        val names = try {
            context.assets.open(ASSET_NAME).bufferedReader().use { reader ->
                val array = JSONArray(reader.readText())
                buildList {
                    for (index in 0 until array.length()) {
                        array.optString(index)
                            .takeIf { it.isNotBlank() }
                            ?.let(::add)
                    }
                }
            }
        } catch (t: Throwable) {
            emptyList()
        }
        return names.distinct()
            .sorted()
            .map { CameraFlag(it, categorize(it), describe(it)) }
    }

    private fun fallbackDescription(name: String): String {
        val words = name
            .removePrefix("SUPPORT_")
            .removePrefix("DEFAULT_")
            .removePrefix("IS_")
            .split('_')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { token ->
                if (token in acronyms) token else token.lowercase()
            }
            .replaceFirstChar { it.uppercase() }
        return "Newer flag; name suggests: $words."
    }

    private val acronyms = setOf(
        "3D", "AE", "AF", "AI", "APV", "BT", "DOF", "ESIM", "FPS", "FRC", "HDR",
        "HDR10", "HEIF", "HEVC", "JPEG", "LUT", "MDM", "MP", "OIS", "OTG", "PIP",
        "QR", "UHD", "USB", "VDIS", "VI",
    )
}
