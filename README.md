<div align="center">

# One Player

**A modern Android video player for local media**

[![English](https://img.shields.io/badge/English-red)](README.md)
[![简体中文](https://img.shields.io/badge/简体中文-blue)](.github/docs/README.zh-CN.md)

[![Android 31+](https://img.shields.io/badge/Android-31+-34A853?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Media3](https://img.shields.io/badge/Media3-FF6F00?logo=android&logoColor=white)](https://developer.android.com/media/media3)

</div>

> Built with **Kotlin**, **Jetpack Compose**, **Hilt**, and **Media3 / ExoPlayer**.
> Continues from the [original Next Player project](https://github.com/anilbeesetti/nextplayer) with in-app language switching, settings backup & restore, and ASS subtitle effects.
>
> Special thanks to the original [Next Player](https://github.com/anilbeesetti/nextplayer) project and all upstream contributors for the solid foundation and long-term maintenance.

---

## ✨ Highlights

| | Feature | Description |
|:---:|---|---|
| 🎬 | **Smart Library** | Folder / tree / flat views, search, grid & list layouts |
| ▶️ | **Full Playback** | Resume, autoplay, PiP, gestures, per-file track memory |
| 🔤 | **Rich Subtitles** | ASS effects, external subs, encoding & appearance tweaks |
| 🎨 | **Material You** | Dynamic color, in-app language switch, full theming |
| 💾 | **Backup & Restore** | Export / import all settings across devices |
| 🚀 | **CI/CD Ready** | Signing validation and release artifacts |

---

<details>
<summary>📖 <strong>Feature Guide</strong></summary>

### Media Library

- Browse videos in folder mode, tree mode, or flat video mode
- Search media from the library screen
- Switch list and grid layouts
- Sort by title, duration, size, date, and related view options
- Exclude selected folders from scanning
- Optionally ignore `.nomedia` and include those directories in scanning
- Refresh and verify media behavior faster with debug-only commands

### Playback

- Play local videos directly from the library or external intents
- Continue playback from the previous position
- Autoplay the next video in the current folder
- Use Picture in Picture mode
- Keep background playback behavior configurable
- Adjust playback speed, long-press speed, zoom, and content scale
- Use seek, brightness, volume, zoom, and double-tap gestures
- Remember per-file selections like audio track and subtitle track

### Subtitle and Audio

- Select built-in audio tracks and subtitle tracks
- Load external subtitle files from the system document picker
- Render ASS subtitles with effect support
- Configure preferred audio language and subtitle language
- Change subtitle font, bold text, text size, background, delay, speed, and text encoding
- Control decoder preferences for audio and video playback behavior

### Personalization and Settings

- Switch app language inside the app
- Use Material 3 and dynamic color theming
- Configure gestures, player controls, orientation, decoder priority, and thumbnails
- Back up app settings and player settings to a file
- Restore settings from a backup file
- Reset settings when needed

### Release and Debugging

- CI produces debug artifacts for branch validation
- Release workflow validates signing secrets before signed builds

</details>

<details>
<summary>📱 <strong>User Manual</strong></summary>

### 1. First Launch

- Install a debug or release APK
- Grant media access permission when prompted
- If you enable `.nomedia` override, also grant all-files access when Android asks for it

### 2. Browse Your Library

- Open the home screen and choose folder, tree, or video view
- Use search to find files quickly
- Open quick settings from the media screen to change sorting, layout, and view mode
- Hide folders you do not want to see from settings

### 3. Watch Videos

- Tap any video to open the player
- Swipe horizontally to seek
- Swipe vertically to control brightness or volume
- Double tap to trigger the configured skip action
- Pinch to zoom if zoom gestures are enabled
- Use PiP or background play if your settings allow it

### 4. Use Subtitles

- Open subtitle track selection inside the player for embedded tracks
- Use the subtitle picker to load an external subtitle file
- ASS subtitles support effect rendering, not only plain text display
- Fine-tune subtitle appearance and playback behavior from Settings > Subtitle

### 5. Back Up and Restore

- Open Settings > General
- Export current app and player settings to a backup file
- Import the backup later on the same or another device
- Use reset if you want a clean configuration again

### 6. Troubleshooting

- If the library looks incomplete, check folder exclusions and `.nomedia` behavior
- If playback fails, retry after granting media permissions again
- If subtitle appearance looks wrong, verify text encoding and ASS track selection
- If a signed release fails in CI, check the signing validation output first

</details>

---

## 🏗️ Project Structure

```text
app/                  Application entry points, manifest, release variants
core/common/          Logging, dispatchers, common helpers
core/data/            Repository implementations and mappers
core/database/        Room database, DAO, schema
core/datastore/       DataStore sources and serializers
core/domain/          Use cases
core/media/           Media scanning and playback services
core/model/           Shared models
core/ui/              Shared Compose UI, strings, theme
feature/player/       Player UI and playback flow
feature/settings/     Settings screens and preference logic
feature/videopicker/  Media library browsing, search, quick settings
.github/workflows/    CI, version, and release workflows
```

---

<details>
<summary>🔧 <strong>Build and Validation</strong></summary>

### Prerequisites

- Android Studio or Android SDK command-line tools
- JDK `25`

### Common Commands

```bash
./gradlew assembleDebug
./gradlew test
./gradlew ktlintCheck
./gradlew connectedAndroidTest
./gradlew ktlintCheck test assembleDebug --warning-mode=fail
```

</details>

<details>
<summary>🧑‍💻 <strong>Development Tutorial</strong></summary>

### Step 1: Clone and Open

```bash
git clone https://github.com/Kindness-Kismet/One-Player.git
cd One-Player
```

Open the project in Android Studio, let Gradle sync, and confirm JDK 25 is selected.

### Step 2: Build a Debug APK

```bash
./gradlew assembleDebug
```

Install one of these outputs:

- `app/build/outputs/apk/debug/app-universal-debug.apk`
- `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`
- `app/build/outputs/apk/debug/app-x86_64-debug.apk`

### Step 3: Navigate the Codebase

Recommended reading order:

1. `README.md`
2. `app/src/main/java/one/next/player/MainActivity.kt`
3. `feature/videopicker/`
4. `feature/player/`
5. `feature/settings/`
6. `core/` modules for data, media, and shared UI

### Step 4: Add or Change a Feature

A typical flow is:

1. Add or adjust models in `core/model`
2. Update repositories or persistence in `core/data`, `core/database`, or `core/datastore`
3. Add use-case logic in `core/domain` when needed
4. Wire UI in the relevant `feature/*` module
5. Verify strings and localization in `core/ui/src/main/res`

### Step 5: Validate Before Commit

```bash
./gradlew ktlintCheck test assembleDebug --warning-mode=fail
```

If the change touches player behavior, also run the app on a device or emulator and verify actual playback.

### Step 6: Prepare a Release

1. Update `app/build.gradle.kts` with the target version
2. Push a `release/vx.y.z` branch or run the release workflow manually
3. Let CI validate signing and build artifacts
4. Review generated GitHub Release notes

</details>
