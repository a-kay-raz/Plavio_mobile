# Avio

An open-source streaming app for Android with a built-in player. Install addons to browse movies, series, and live TV — or install CloudStream plugins for extra sources. An account is optional; everything works on-device by default.

## Features

- **Addons** — paste any addon manifest URL (`…/manifest.json`) and browse its catalogs. Cinemeta (metadata), OpenSubtitles and SubSense are installed during setup
- **CloudStream plugins (CS3)** — add a plugin repo, install providers, and browse them on Home, in Search, and on Details. Pick one provider (or all) from the Home pill, pin favourite sources
- **Smart Streams** — scored stream ranking with filters, so the best source comes first
- **Movies, series & live TV** — details, posters, backdrops, genres, ratings, cast with profile photos; a dedicated Live tab for TV channels
- **Downloads** — per-show downloads with resolution and concurrency controls, offline subtitles, custom download location
- **Live TV** — Xtream Codes, Stalker Portal, and M3U playlists, with EPG, sports, and on-demand
- **Continue Watching** — progress saved on-device, with resume prompts, Next Up, and a release calendar
- **Library** — Saved and Downloaded views, filters, sorting, and collections
- **Player** — ExoPlayer with HLS, DASH, and progressive streams; swipe to seek, volume/brightness gestures, double-tap skip, pinch zoom, hold-to-speed, skip intro, audio boost, subtitle sync-by-ear, background audio
- **Hero Play** — the Play button on the Home hero starts the movie or the next episode right away
- **Trakt & Simkl** — two-way watch-history sync and scrobbling
- **Media servers** — Jellyfin, Emby, Plex, and Silo libraries in one place
- **Debrid** — Real-Debrid, Premiumize, and TorBox for cached streams
- **Profiles** — local multi-profile with optional PIN, each with its own library and settings
- **TMDB enrichment** — ratings, cast, trailers, and artwork from TMDB (needs your own TMDB key)
- **Gold accent** — the default theme is deep black with a gold accent; more accents in Appearance

## Get the app

Download the latest APK from the [Releases](../../releases) page and install it on your Android phone (Android 8.0+, arm64).

## Add an addon

1. Open **Addons** → tap **+**
2. Paste the addon's manifest URL (it usually ends with `/manifest.json`)
3. Avio validates it and adds its catalogs to your Home screen

To add a CloudStream provider instead, go to **Settings → CloudStream**, add a repo URL, and install a plugin.

## Build from source

Requirements: JDK 17, Android SDK (compileSdk 36), Gradle 8.11.1 (wrapper included).

```bash
./gradlew :app:assembleMobileRelease
# APK: app/build/outputs/apk/mobile/release/app-mobile-release.apk
```

Set `ANDROID_HOME` to your SDK or create an untracked `local.properties` with `sdk.dir=/path/to/android-sdk`. Install Android SDK Platform 36 and Build Tools 35.0.0. The first build needs internet access to download dependencies.

Release builds are unsigned unless the owner key is configured. Set `AVIO_SIGNING_STORE`, `AVIO_SIGNING_STORE_PASSWORD`, `AVIO_SIGNING_KEY_ALIAS`, and `AVIO_SIGNING_KEY_PASSWORD` in your private `~/.gradle/gradle.properties`. A build with a different signing key cannot update an already-installed owner-signed app; do not uninstall it without backing up your data.

## Tech

Kotlin 2.3.10 · Jetpack Compose (Material 3) · Navigation Compose · Media3 ExoPlayer 1.4.1 · OkHttp · Room · Coil · kotlinx.serialization

## Architecture

```
app/src/main/java/com/pulsestream/app/
├── data/
│   ├── StreamRepository.kt  # multi-addon merging, search, CS3 home rows
│   ├── cloudstream/         # CloudStream plugin runtime, repos, manager
│   ├── db/                  # Room: addons, library, downloads, profiles
│   └── update/              # in-app updater (checks this repo's stable.json)
├── ui/
│   ├── screens/     # Home, Search, Addons, Detail, Player, Settings…
│   ├── components/  # cards, SafePosterImage
│   └── theme/       # ONYX black theme, gold accent
└── MainActivity.kt  # NavHost + bottom navigation
```

- **Multi-addon merging** — each addon declares its capabilities (catalog / metadata / stream). Catalogs aggregate across addons, metadata is tried across addons until one answers, streams are fetched concurrently from all stream addons and merged.
- **Player** — Media3 ExoPlayer: gesture controls (seek / volume / brightness), double-tap skip, speed 0.25x–2x, external subtitles, resume playback.
- **Offline-first** — core library data persists in Room, but addon, metadata, playback, integration, updater, and optional sync features make network requests.

## Data use and privacy

Avio can be used without an Avio Cloud account. Signing in enables profile restore and automatic versioned backups of portable settings, watchlist, Continue Watching, custom lists/membership, and addon metadata to its Supabase-backed service. Ordinary snapshots omit supported integration secrets and protect configured addon URL components. You can separately enable passphrase-protected, end-to-end encrypted sync of supported integration keys and full configured addon URLs; another device needs the same passphrase to unlock them after profile restore. Ordinary library metadata is not covered by that credential encryption. Downloads, device permissions, and cloud-login sessions are not portable backup content. Profile switching uses replace-and-restore with local recovery, but is not a parental-control or device-security boundary; real multi-device conflicts and backend authorization remain to be verified. Optional telemetry is disabled by default.

The app ships with configured addons. Addon URLs and requests can contain credentials or viewing/search identifiers; addon operators receive the requests sent to their servers. Do not publicly share an addon URL containing a key or token. Addon bundle sharing blocks URLs that appear to contain credentials, but detection is heuristic and not exhaustive.

## License

MIT — built for the love of streaming.
