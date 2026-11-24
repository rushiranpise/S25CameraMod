# S25 Camera Mod

An LSPosed module for the Galaxy S25 Ultra camera. It raises the photo and video zoom ceiling,
restores the stock shortcut row with configurable high-zoom presets, and exposes hidden Camera
features through a compact Material 3 Expressive UI.

[![Version](https://img.shields.io/badge/version-1.1.1-blue)](https://github.com/rushiranpise/S25CameraMod/releases/latest)
[![Android](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white)](https://www.android.com/)
[![LSPosed](https://img.shields.io/badge/LSPosed-API%2082-6C3FC5)](https://github.com/LSPosed/LSPosed)

> [!IMPORTANT]
> This is an experimental camera mod. The APK is debug-signed, requires root plus LSPosed, and is
> only tested against the firmware listed under [Compatibility](#compatibility).

## Features

- **Configurable photo and video zoom** — 100.0× to 999.9×, default **100×**, applied to the back
  camera's Photo (`NORMAL`), Video, Pro video (`LENS_PHYSICAL`), Portrait Video, and 8K video zoom
  queries.
- **Per-mode zoom opt-ins** — separate switches for Night, Portrait, Food, Hyperlapse,
  Dual/Multi recording, and high-resolution stills. Every new switch defaults off, so its mode
  keeps the stock ceiling until you enable it.
- **Zoom presets** — whole-number shortcuts from 1× to 999×, up to seven entries, sorted and
  deduplicated. Defaults are **25×** and **100×**. The gear beside the zoom switch edits the
  ceiling and presets.
- **Video and Camera tabs** — the zoom card stays on top; video experiments and still-camera
  experiments are separated below it.
- **Feature flag explorer** — browse all 243 support flags compiled into Camera 17.0.00.43, each
  with its stock on/off value, a short summary, and an optional override. Search by flag name or
  summary, filter by **All / On / Off / Overridden**, and reset one flag or every override.
- **Safe fractional readouts** — the zoom readout is extended to five characters, so values such
  as `100.4` and `150.5` render without the stock `Invalid string length` crash.
- **Independent toggles** — every experimental feature is opt-in. A disabled toggle leaves the
  stock value untouched.

### Zoom places

The camera app asks the HAL for a different maximum-zoom entry per shooting mode. The module maps
those entries to independent switches:

| Place | Camera zoom type | Switch | Default |
| --- | --- | --- | --- |
| Photo, Video, Pro/Pro video, Portrait Video, 8K video | `NORMAL`, `VIDEO`, `LENS_PHYSICAL`, `PORTRAIT_VIDEO`, `HIGH_RESOLUTION` (7680×) | Photo & video zoom | On |
| Night | `NIGHT` | Night mode zoom | Off |
| Portrait | `PORTRAIT` | Portrait zoom | Off |
| Food | `FOOD` | Food zoom | Off |
| Hyperlapse | `HYPER_LAPSE` | Hyperlapse zoom | Off |
| Dual/Multi recording | `MULTI_RECORDING` plus the app's 19.9×/9.9× hardcode | Dual/multi recording zoom | Off |
| 50 MP / 200 MP and other high-resolution stills | `HIGH_RESOLUTION` (non-7680×) | High-res photo zoom | Off |

A few places hard-block digital zoom before the maximum is ever queried. They are not exposed as
switches because a ceiling change cannot restore their zoom control:

| Place | Why a ceiling change is not enough |
| --- | --- |
| Front camera | Photo and video use the full/crop angle (`FOV`) control, not a digital slider |
| Single Take, panorama | Lens-only zoom card; the mode never adds a level slider |
| Mirror | Uses the mirror zoom animation and lens controls instead of the level slider |
| More | Reports `NOT_SUPPORTED`, and the zoom-availability check rejects it |
| Stereo video | `VideoZoomController` rejects zoom and resets it to 1× on mode entry |
| Super Steady, Auto Framing | Lens-only card; the controller clamps zoom to the supported 1×/2× levels |
| Photo with flash in the wide lens | The photo controller hides the zoom controls while flash is active |

> [!NOTE]
> The per-mode switches change the maximum the camera asks the HAL for. They do not add a zoom
> control to a mode that has none. Food, Portrait, and Hyperlapse fall back to `NORMAL` or
> `LENS_PHYSICAL` when Samsung's seamless-zoom flag is off; those fallbacks stay on the master
> **Photo & video zoom** switch.

### Video tab

| Toggle | What it enables |
| --- | --- |
| Dual/multi recording zoom | Removes the app's 19.9×/9.9× multi-scan cap and applies the configured ceiling |
| Hyperlapse zoom | Raises the `HYPER_LAPSE` max-zoom query |
| Super steady 4K60 | `super-vdis` on the 4K60 back-camcorder maps |

The remaining video behaviour — Dual Recording V2, 8K HDR10+/HLG, Tele 120 fps, APV recording,
natural-blur video, and 4K portrait video — is controlled by its underlying support flag in
**Feature flags**.

### Camera tab

| Toggle | What it enables |
| --- | --- |
| Night mode zoom | Raises the `NIGHT` max-zoom query |
| Portrait zoom | Raises the `PORTRAIT` max-zoom query |
| Food zoom | Raises the `FOOD` max-zoom query |
| High-res photo zoom | Raises the `HIGH_RESOLUTION` query for 50 MP/200 MP and other stills |

The former photo toggles — Portrait filters, Detail Enhancer button, 50 MP/24 MP motion photo,
Fusion high resolution, Photo max night shot, AI high-res memory check, and Back 24 MP
resolution — now live in **Feature flags** as raw support-flag overrides.

### Feature flags

Open the **Feature flags** row on the main screen to browse every support flag defined by
`e5.d` in Camera 17.0.00.43:

- **Stock value** — the camera process reports its real on/off state, captured without applying
  any module override. The header shows the Camera version and how many flags are on.
- **Override** — flip any flag on or off. A flag equal to its stock value stores no override; a
  different value is applied in the `e5.g.o(e5.d)` after-hook and marked **Override**.
- **Search and filters** — search matches both the flag name and its summary. Filter by
  **All / On / Off / Overridden**, and narrow to the **Video** or **Camera** category.
- **Reset** — use the reset icon in each row to return that flag to stock, or the circular-arrow
  icon in the header to clear every override at once.

> [!NOTE]
> Summaries are inferred from the flag names, not official Samsung documentation. Some hardware
> and system flags have no visible effect on this firmware, and forcing one off can destabilise
> Camera. Overrides are opt-in; leaving a flag on **Default** keeps the stock result.

> [!NOTE]
> The camera app computes feature support once per process and caches the module snapshot. After
> changing any toggle, ceiling, or preset, **force stop Camera** before reopening it.

## Compatibility

| Item | Value |
| --- | --- |
| Device | Samsung Galaxy S25 Ultra (SM-S938U1) |
| OS | One UI 9 / Android 17 (SDK 37) |
| Camera | `com.sec.android.app.camera` 17.0.00.43 (versionCode 1700043000) |
| Framework | LSPosed, legacy Xposed API 82 |
| Root | KernelSU Next or another LSPosed-compatible root |
| Module minSdk | 33 (Android 13) |

### Other Samsung devices

The module **may** work on other Samsung devices running One UI 9, but that is not guaranteed and
is not supported. Samsung does not guarantee one identical Camera APK across models, regions, and
firmware builds: the Camera version, obfuscated class and method names, support-flag catalog, and
hardware capabilities can all differ.

A build that looks compatible can still:

- miss one or more hooks, leaving that feature silently at stock;
- expose flags that do nothing or are unsafe on that device;
- crash Camera when an unsupported flag is forced on.

The 243-flag explorer is built around the `e5.d` catalog in Camera 17.0.00.43; another Camera build
may have a different flag set. Only the S25 Ultra build in the table above is tested. If you try
another device, make sure you can disable the module from LSPosed, keep a backup, and be ready to
reset overrides if Camera will not start.

## Install

1. Download `S25CameraMod-1.1.1-release.apk` (recommended, 3.2 MB) from
   [Releases](https://github.com/rushiranpise/S25CameraMod/releases/latest). The 32.3 MB
   `-debug.apk` is also available for troubleshooting.
2. Install it on the phone.
3. Enable **S25 Camera Mod** in LSPosed.
4. Confirm the module scope contains **Camera** (`com.sec.android.app.camera`).
5. Force stop Camera, then reopen it.

Both APKs are signed with the Android debug certificate and install as updates over earlier module
builds signed with the same key. The release APK is R8-minified and resource-shrunk; the debug APK
keeps full class names for logs. Neither is a Play Store release.

## Usage

1. Open **S25 Camera Mod**.
2. Use **Photo & video zoom** to enable or disable the standard photo/video ceiling. Tap its gear
   to edit the ceiling and presets.
3. Switch between **Video** and **Camera** to enable additional zoom places or experimental
   features. Each zoom place is independent of the standard switch.
4. Open **Feature flags** to browse and override the raw support flags. Use search or the
   **All / On / Off / Overridden** filters, and reset individual flags or every override from the
   header.
5. Force stop Camera after a change, then reopen Camera.

Presets above the configured ceiling remain saved but are ignored until the ceiling is raised.
Presets apply to the shortcut-list zoom UI in Video and in the expanded Photo zoom controls while
**Photo & video zoom** is enabled. The tick slider follows the raised ceiling too: the thumb
animates across the full usable range instead of stopping at the last stock tick.

## How it works

- `u8.e.f(l9.g3, l9.a2)` supplies the per-mode maximum digital zoom. The module returns the
  configured ceiling for the back-camera zoom types whose switch is enabled.
- `p8.n1.a(l9.x)` replaces the computed multi-recording maximum with 19.9×/9.9× when multi-scan
  mode is active. A second hook rewrites that value only when **Dual/multi recording zoom** is on.
- `e5.g.o(e5.d)` is the camera's support-flag getter. An after-hook applies the user's raw
  override map, so an overridden flag can be forced on or off while untouched flags keep the stock
  result. The stock snapshot is captured separately through `report_stock_flags`, before any
  override is applied.
- `e5.g.n(e5.l)` supplies the shooting-mode and camcorder maps. The module rewrites only the maps
  requested by an active override: Dual Recording V2, 8K HDR10+/HLG, and Super steady 4K60.
- `e5.g.m(e5.n)` supplies resolution strings. A small fallback fills the missing 24 MP entries
  for `SUPPORT_BACK_MEDIUM_RESOLUTION` and the missing front-camera entries for
  `SUPPORT_FRONT_HIGH_RESOLUTION`, so neither flag can feed an empty size to `l9.a2.k`.
- The **Feature flags** explorer reads the bundled 243-name `e5.d` catalog and the camera-reported
  stock snapshot, then stores only the values that differ from stock.
- `ZoomReadoutFix` extends the stock three zoom views from four to five character buckets.
- Zoom presets rewrite the shortcut adapter's high-zoom tail and override the shared value lookup
  so chip text, click value, selection, and ticks stay consistent.
- `ZoomSliderFix` fills the tick ranges the stock map does not know for custom preset pairs,
  appends minor ticks when the last preset is below the mode maximum, and records the far-right
  scroll position as the terminal zoom. The slider and its animation can then reach the ceiling.

The module app stores its settings in private `SharedPreferences` and exposes them read-only to
Camera through an exported content provider. Camera reads one snapshot per process.

## Build

Requirements:

- JDK 17
- Android SDK platform 36 and build-tools 36+
- Internet for the first Gradle run, or a populated Gradle cache

```powershell
git clone https://github.com/rushiranpise/S25CameraMod.git
cd S25CameraMod
.\gradlew.bat assembleRelease lintRelease
```

On Linux or macOS use `./gradlew assembleRelease lintRelease` instead.

Output:

```text
app\build\outputs\apk\release\app-release.apk
```

To build the larger debug APK instead, run `assembleDebug lintDebug` and use
`app\build\outputs\apk\debug\app-debug.apk`.

The release build is minified, resource-shrunk, and signed with the debug key for update
compatibility. `proguard-rules.pro` keeps the Xposed entry point and DexKit classes. Both variants
are arm64-v8a and include the DexKit native library used for class lookup. The 1.1.1 release APK is
3.2 MB; the debug APK is 32.3 MB.

## Known limitations

- Zoom above the stock optical range is digital crop/upscale; detail and stabilization degrade.
- The camera HAL may reject or clamp crop requests above its own per-mode maximum. An APK hook
  cannot fix a hard HAL limit.
- Above 100×, the stock tick map is sparse or incomplete even though the readout works.
- 50 MP Motion Photo, Fusion high resolution, and the policy flags are experimental. They may
  expose no UI or fail on capture on this firmware.
- **24 MP is not usable on this firmware.** Turning on `SUPPORT_BACK_MEDIUM_RESOLUTION`,
  `SUPPORT_BACK_PORTRAIT_MEDIUM_RESOLUTION`, or `SUPPORT_24MP_MOTION_PHOTO` makes the vendor
  camera HAL crash while configuring 24 MP streams (`GetStreamForHiResScaler24MP` SIGSEGV /
  `ChxSensorModeSelect::FindClosestMode` SIGABRT). The failure is below the app: the HAL has no
  24 MP stream or XCFA sensor mode for this sensor. Leave these flags on **Default**.
- Feature-flag summaries are inferred from the flag names. Samsung publishes no descriptions for
  `e5.d`; the explorer disclaimer reflects that.
- Forcing a hardware or system flag off can break Camera features or destabilise the app. Leave
  flags on **Default** unless the effect is understood.
- The front camera is 12 MP. Forcing **SUPPORT_FRONT_HIGH_RESOLUTION** on shows the front
  picture-size toggle with 12 MP sizes; it cannot add detail that the sensor does not capture.
- `SUPPORT_AI_HIGH_RESOLUTION_MEMORY_CHECK` is not queried by Camera 17.0.00.43, so overriding it
  has no effect on this build.
- The per-mode zoom switches are experimental. Some `NOT_SUPPORTED` places hide the zoom UI
  entirely, and the HAL may still clamp capture crop even when the slider moves.
- The front camera has no digital-zoom slider; it is intentionally not covered.
- Preset values that reuse placeholder enum IDs can show the placeholder's stock value in some
  accessibility labels and tick positions.
- Disabling **Photo & video zoom** restores the stock shortcut row and the standard per-mode
  ceilings; disabling a per-mode zoom switch restores only that mode's ceiling.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## Disclaimer

This project is not affiliated with, endorsed by, or supported by Samsung or LSPosed. Use it at
your own risk. Camera modifications can affect image quality, thermal behavior, or stability.
Restore stock behavior by disabling the module and rebooting if needed.

## Credits

- [LSPosed](https://github.com/LSPosed/LSPosed) for the Xposed framework.
- [DexKit](https://github.com/LuckyPray/DexKit) for runtime dex lookup.
- Kotlin, Jetpack Compose, and Material 3.
