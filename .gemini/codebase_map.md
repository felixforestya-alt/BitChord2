# BitChord2 Codebase Map

## Architecture Overview
BitChord² is a multiplatform music player (Compose Multiplatform / Kotlin Multiplatform) with an emphasis on a Windows desktop client.

- **Root modules**:
  - `:shared` - Common data models, repositories (YouTube Music, Listen Together, lyrics, scrobbling, cache, audio metadata).
  - `:sharedUi` - Shared Compose UI components, themes, icons, player controls.
  - `:desktopUi` - Desktop-specific UI layouts and components.
  - `:desktopApp` - Desktop application entry point (`Main.kt`, `DesktopApp.kt`), desktop audio engine, tray, update checker, settings, packaging.
  - `:app`, `:tv`, `:wearos` - Android / Android TV / Wear OS targets (optional if Android SDK is absent).

## Key Files & Entrypoints
- `desktopApp/src/main/kotlin/com/music/bitchord/desktop/Main.kt`: Desktop application main method.
- `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopApp.kt`: Root Composable `BitChordDesktopApp`, manages navigation, pages, audio engine, overlays, dialogs, and settings screen.
- `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopUpdateChecker.kt`: GitHub release checking logic (`DesktopUpdateChecker`).
- `desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopSettingsScreen` (in `DesktopApp.kt`): Settings UI displaying audio, appearance, integrations, and `DesktopSettingsFooter` displaying version and links.
- `build.gradle.kts` & `desktopApp/build.gradle.kts`: Version configuration (`bitchord.version`, `desktopVersionCode`, packaging configs).

## Update Mechanism
- **Remote Repository**: `https://github.com/felixforestya-alt/BitChord2`
- **Releases API**: `https://api.github.com/repos/felixforestya-alt/BitChord2/releases`
- **Release Assets**: Portable Windows ZIP archive (e.g. `BitChord2-v*.zip` or `BitChord-windows-x64*.zip`) containing the bundled executable and runtime.
- **In-App Updater Requirements**:
  - Display current app version in Settings.
  - "Check for updates" / "Update" button in Settings.
  - Check latest release on GitHub API.
  - If a newer release is found, download the update archive or installer, unpack / apply the update, and restart or guide the user seamlessly.
