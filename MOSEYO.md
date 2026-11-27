# Moseyo — every device

All Moseyo apps share one account (the self-hosted MOSEYO backend) and one
feature set. Downloads are on this repository's **Releases** page.

| Device | What to install | Built by | Updates |
|---|---|---|---|
| iPhone / iPad | SideStore/AltStore source (`store.json`), release `sideload-…` | `ios-test-build.yml` | new version number each update |
| Android phone | `Moseyo-<version>-android-arm64-v8a.apk`, release `android-…` | `moseyo-android.yml` | same version as iOS |
| Android TV / Google TV | `Moseyo-AndroidTV-<version>.apk`, release `androidtv-…` | `moseyo-androidtv.yml` | TV features update by themselves |
| Hisense (VIDAA) | hosted app, installed with Sidee | Cloudflare (adadshah/nuvio-tv) | by themselves |
| LG (webOS) | `Moseyo-LG-webOS-….ipk`, release `moseyo-webos-…` | `moseyo-webos.yml` in adadshah/nuvio-tv | new .ipk |

## Where the code lives
- **Phones (iOS + Android):** this repo (`cmp-rewrite`), one Kotlin Multiplatform
  codebase — a feature added here lands on both phones.
- **TVs (Hisense, LG, Android TV):** adadshah/nuvio-tv — one TV web app. Hisense
  and Android TV load it from Cloudflare, so they always have the latest; LG gets
  it as an .ipk. `moseyo-androidtv/` here is only the thin Android TV shell.

## One-time setup (Settings → Secrets and variables → Actions)
This repo:
- `MOSEYO_ANDROID_KEYSTORE_BASE64`, `MOSEYO_ANDROID_KEYSTORE_PASSWORD`,
  `MOSEYO_ANDROID_KEY_ALIAS`, `MOSEYO_ANDROID_KEY_PASSWORD` — the Android signing
  key (phone and TV). Never change it, or Android refuses updates.
- `MOSEYO_TMDB_API_KEY` (optional) — built-in TMDB key for the Android phone app.

adadshah/nuvio-tv:
- `MOSEYO_TMDB_API_KEY`, `MOSEYO_MDBLIST_API_KEY` — built-in keys for the LG .ipk
  (Cloudflare build variables `TMDB_API_KEY` / `MDBLIST_API_KEY` do the same for
  Hisense and Android TV).
- `MOSEYO_RELEASES_TOKEN` (optional) — fine-grained token with Contents: write on
  this repo, so the LG .ipk is published here next to the other apps.
