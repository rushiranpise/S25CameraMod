# Changelog

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
