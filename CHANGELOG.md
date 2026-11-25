# Changelog

## 1.3.0

Generic per-resolution capability mirroring.

- Forcing a support flag now also writes the matching per-resolution capability keys into the
  camera-feature maps. The flag opens the menu; the map key is a second gate that could still
  hide the feature at every resolution.
- Covered: **SUPPORT_APV** → `apv`; **SUPPORT_HDR_VIDEOS_MENU** → `hdr10`/`hlg`;
  **SUPPORT_CAMCORDER_ANTI_SHAKE** → `vdis` (back); **SUPPORT_CAMCORDER_FRONT_ANTI_SHAKE** →
  `vdis` (front); **SUPPORT_SEAMLESS_ZOOM** → `seamless-zoom-support`;
  **SUPPORT_OBJECT_TRACKING_AF** → `object-tracking`; **SUPPORT_BACK_VIDEO_BEAUTY** → `effect`.
- Only explicit true overrides trigger a rewrite, and a map is only replaced when a value
  actually changes. The 8K HDR10+/HLG rewrite is now part of the same rule table.
- APV: this firmware omits `apv` from `camera-feature.xml`, which made `te.c0.o(resolution)`
  false even with **SUPPORT_APV** forced on. The mirror keeps the APV capture-type, profile, and
  HDR/LOG indicator settings available instead of hiding them at every resolution.
- VersionCode 15, versionName 1.3.0.

## 1.2.0

Camera Assistant bridge.

- Added **Unhide Assistant settings** on the **Camera** tab. It after-hooks Camera's
  `CameraSettingProvider.call("get", ...)` reply and appends the Camera Assistant preference keys
  that this firmware filters out, using Camera's own stored value and stock default for each key.
- Camera Assistant stays untouched. It renders the extra keys through its normal preference
  pipeline, and its signature-level `CameraSettingProvider.READ_WRITE` permission keeps working.
- Only keys that already exist in the installed Camera Assistant are appended. Some entries can
  duplicate Camera's own menu or be inert on this firmware.
- Skips **Mirror view**, **Quick launch mirror**, **Launch from Lock screen**, and the two 24 MP
  keys. This firmware has no `SHOOTING_MODE_MIRROR` feature data, so opening Mirror view crashes
  Camera with `IllegalArgumentException: getResolution : argument is invalid - null`; 24 MP has
  the HAL limitation described in 1.1.1.
- VersionCode 14, versionName 1.2.0.

## 1.1.1

Fix flag-induced Camera crashes.

- Forcing **SUPPORT_FRONT_HIGH_RESOLUTION** on made Camera 17.0.00.43 crash at startup with
  `getResolution : argument is invalid -` because the front resolution entries are empty in
  `camera-feature.xml`. The module now supplies the stock 12 MP front sizes
  (`4000x3000`, `4000x2252`, `2992x2992`, `4000x1848`) before the picture-size menu is built.
- The same fallback now covers **SUPPORT_BACK_PORTRAIT_MEDIUM_RESOLUTION** when it is enabled
  without **SUPPORT_BACK_MEDIUM_RESOLUTION**.
- Enabling **SUPPORT_PREVIEW_FIT_TO_FULL_SCREEN** kept the `view_mode` switch preference, but
  Camera 17.0.00.43 has no `view_mode` entry in its settings-key map. Opening Settings threw
  `RuntimeException: No defined key view_mode` from `sc.b0.h`. The module now supplies the enum's
  own `INVALID` analytics event for that one key while the override is on, so Settings opens
  normally.
- The **On** and **Off** filters now use each flag's live value (override if present, otherwise
  stock) instead of the stock value alone.
- The zoom tick slider now follows the configured ceiling. Missing preset ranges such as
  `25x` to `100x` are added to the stock tick map, and the track is extended to the effective
  mode maximum when the last preset is below it. The thumb and the zoom animation no longer stop
  at the old 25x tick while the readout keeps increasing.
- The header now reads the installed version instead of a hardcoded string.
- Documented that 24 MP capture is not supported on this firmware. The medium-resolution and
  24 MP Motion Photo flags crash the vendor HAL; leave them on Default.
- VersionCode 13, versionName 1.1.1.

## 1.1.0

Full support-flag explorer.

- Added **Feature flags**, a searchable list of all 243 `e5.d` support flags compiled into
  Camera 17.0.00.43, grouped into **Video** and **Camera**.
- Each flag shows its stock on/off value reported by the camera process, a short summary, and a
  Default/Override badge.
- Added per-flag overrides, a per-row reset, a header reset-all action, and **All / On / Off /
  Overridden** filters.
- Overrides are applied in the `e5.g.o(e5.d)` after-hook, so an overridden flag can be forced
  either on or off. Flags without an override keep the exact stock result.
- Stock values are captured by the camera process through `report_stock_flags`, which is limited
  to the Camera UID, so overrides never contaminate the reported stock snapshot.
- The former raw support-flag toggles were folded into the explorer and migrate once into raw
  overrides. Dual Recording V2 and 8K HDR10+/HLG map rewrites now key off those overrides.
- Summaries are generated from the flag names; the explorer states that they are inferred hints,
  not official Samsung documentation.
- VersionCode 12, versionName 1.1.0.

## 1.0.1

Per-place zoom opt-ins.

- Added independent zoom switches for Night (`NIGHT`), Portrait (`PORTRAIT`), Food (`FOOD`),
  Hyperlapse (`HYPER_LAPSE`), dual/multi recording (`MULTI_RECORDING`), and high-resolution stills
  (`HIGH_RESOLUTION` outside 7680× video).
- Added a `p8.n1.a` override for the app's hardcoded 19.9×/9.9× multi-scan ceiling.
- Every new switch defaults off; the existing **Photo & video zoom** master keeps its 1.0.0 scope
  and remains on by default.
- Documented the zoom-type map and the places whose zoom control is hard-blocked (front camera,
  Single Take, panorama, Mirror, More, stereo video, Super Steady/Auto Framing, and flash photo).
- VersionCode 11, versionName 1.0.1.

## 1.0.0

First public release.

- Photo and video zoom ceiling configurable from 100.0× to 999.9× (default 100×).
- Photo mode (`NORMAL`), Video, Pro video, Portrait Video, and 8K video support.
- Configurable high-zoom presets: 1×–999×, up to seven entries, sorted and deduplicated.
- Five-character zoom readout fix for fractional values above 100×.
- Material 3 Expressive UI with the zoom control on top and **Video** / **Camera** tabs below.
- Video toggles: Dual Recording V2, 4K portrait video, Super steady 4K60, 8K HDR10+/HLG,
  Tele 120 fps, APV recording, and natural-blur video.
- Camera toggles: Portrait filters, Detail Enhancer button, 50 MP motion photo,
  24 MP motion photo, Fusion high resolution, Photo max night shot, AI high-res memory check,
  and Back 24 MP resolution.
- 24 MP resolution fallback for firmware that omits the medium-resolution XML keys.
- The release APK is R8-minified and resource-shrunk (3.1 MB) and ships alongside the debug APK
  (31.9 MB). Both are signed with the Android debug key for update compatibility.
- VersionCode 10, versionName 1.0.0.

### Known issue

- `SUPPORT_AI_HIGH_RESOLUTION_MEMORY_CHECK` is compiled into Camera 17.0.00.43 but never queried,
  so that toggle has no effect on this build.

## 0.9.0

- Added the Video and Camera tabs.
- Added the first seven experimental photo support-flag toggles.
- Added the 24 MP resolution fallback.
- VersionCode 9, versionName 0.9.0.

## 0.8.0

- Added `NORMAL` to the zoom hook, raising the default Photo mode ceiling.
- Renamed the feature to **Photo & video zoom**.
- VersionCode 8, versionName 0.8.0.

## 0.7.0

- Added configurable zoom presets and restored the stock 25× shortcut.
- VersionCode 7, versionName 0.7.0.

## 0.6.0

- Added the five-character zoom readout fix and capped the ceiling at 999.9×.
- VersionCode 6, versionName 0.6.0.
