# Block Tower Escape — Screen 4 (Main Gameplay)

Native Android (Kotlin) prototype of **Screen 4: Main Gameplay**, built to match the supplied Screen 4 artwork.
Only this screen is being built; Home, World Map, Level Select and the other screens are intentionally not started.

**Status: work in progress.** The gameplay code is being written; this README will list how to play once the screen is complete.

## Open in Android Studio
1. File → Open → select this folder.
2. Let Gradle sync (Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compileSdk 35, minSdk 24).
3. Run the `app` configuration on a phone or emulator (portrait).

No third-party libraries are used — rendering is done with `android.graphics` on a hardware-accelerated view.

## Project layout
- `app/src/main/java/com/blocktower/escape/core/` — platform-independent game code (world, level, physics, rendering, HUD)
- `app/src/main/java/com/blocktower/escape/` — Android host (Activity, view, canvas renderer, sound)
- `app/src/main/assets/` — artwork extracted from the Screen 4 design (background, boy, coins, HUD icons), block textures and fonts

Fonts: Fira Sans Condensed and Lilita One (SIL Open Font License, see `assets/fonts`).
