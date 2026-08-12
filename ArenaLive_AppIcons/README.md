# ArenaLive – App Icon & TV Banner Package

Redesigned as an icon-only mark (TV + play symbol, no wordmark) so it stays
legible down to 24px, plus the full logo for the Android TV banner, which
Google requires to include the app name.

## Folder guide

### `android_mobile/res/` → copy straight into `app/src/main/res/`
- `mipmap-*/ic_launcher.png` + `ic_launcher_round.png` — legacy square/round
  icons (Android 7.1 and below), sizes 48–192px per density.
- `mipmap-*/ic_launcher_foreground.png` + `ic_launcher_background.png` +
  `ic_launcher_monochrome.png` — Adaptive Icon layers (Android 8.0+, and the
  monochrome layer for Android 13+ themed/Material You icons), 108–432px.
- `mipmap-anydpi-v26/ic_launcher.xml` — the adaptive-icon descriptor that
  ties the three layers together. Reference it in your manifest as
  `android:icon="@mipmap/ic_launcher"`.
- `drawable-*/ic_notification.png` — white monochrome silhouette for the
  status bar / push notifications, 24–96px.

### `android_tv/res/` → copy into your TV module's `res/`
- `mipmap-*/tv_banner.png` — 16:9 home-screen banner (includes the full
  "ARENA LIVE" wordmark, as Google requires text in the banner), 160×90 up
  to 640×360. Reference as `android:banner="@mipmap/tv_banner"`.
- `mipmap-*/tv_icon.png` — flat 1:1 launcher icon for Settings / Now Playing
  card, 80–320px. Reference as `android:icon="@mipmap/tv_icon"`.
- `mipmap-*/tv_icon_foreground.png` + `tv_icon_background.png` +
  `mipmap-anydpi-v26/tv_icon.xml` — adaptive version of the TV icon.
  (Android TV doesn't support themed/monochrome icons, so there's no
  monochrome layer here.)

### `play_store/`
- `play_store_icon_512.png` — 512×512, flat, no transparency, for the Play
  Console "App icon" listing field.

### `masters/`
High-resolution source layers if you want to re-edit or regenerate sizes
yourself later (e.g. in Figma/Photoshop):
- `adaptive_foreground_master.png` (1024, transparent) — the mark alone
- `adaptive_background_master.png` (1024) — the background layer
- `icon_flat_1024.png` — flattened combo, used for legacy/Play Store/TV icon
- `icon_monochrome_1024.png` — white silhouette (notification/themed)
- `tv_banner_1920x1080.png` — full wordmark banner
- `full_logo_transparent.png` — original full "ARENA LIVE" lockup, cutout
  with transparent background, for anything else you need it for

## Notes
- All icon PNGs use the standard Android safe-zone sizing (mark occupies
  ~60% of the canvas) so nothing gets clipped by circular/squircle/rounded
  masks on different launchers.
- Source artwork was a compressed JPEG at moderate resolution, so the
  largest sizes (512px Play Store icon, 640×360 TV banner) are upscaled and
  very slightly softer than a true vector source would give. They still
  look clean on-device; if you ever get the original vector/PSD file, the
  same pipeline would produce crisper results at those largest sizes.
