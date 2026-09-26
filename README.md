# Block Tower Escape — Screen 4 (Main Gameplay)

Native Android (Kotlin) build of **Screen 4: Main Gameplay**, built to match the supplied Screen 4 artwork.
Only this screen is built. Home, World Map, Level Select and the other screens are intentionally not started yet.

## Get the APK
Every push to `main` is built automatically by GitHub Actions (`.github/workflows/build-apk.yml`):
- **Releases** page → latest `preview-N` → `BlockTowerEscape-debug.apk` (download on your phone, allow "install unknown apps")
- or the `apk-build` branch, which also holds the build log

## Open in Android Studio
1. File → Open → select this folder, let Gradle sync (AGP 8.7.3, Kotlin 2.0.21, compileSdk 35, minSdk 24).
2. Run the `app` configuration on a phone or emulator (portrait).

No third-party libraries: rendering uses `android.graphics` on a hardware-accelerated view; sound effects are synthesized at first launch.

## How to play Level 23
| Control | Action |
|---|---|
| Joystick (bottom-left) | Move: up = forward, down = back, left / right |
| Big arrow (bottom-right) | Jump (hold for a higher jump) |
| Magnet | Pulls in nearby coins and blue blocks (8 s) |
| Shield | Bubble that blocks one hit: spikes, debris, traps, the Tower Guard (10 s) |
| Lightning | Faster running and a stronger jump (6 s) |
| Block tool (4th) | Creates a safe block in the gap ahead (lasts 8 s) |
| Pause (top-right) | Pause / resume / restart |

On an emulator you can use the keyboard: WASD or the arrow keys, Space to jump, 1–4 for the tools, P to pause.

**Goal:** reach the fire portal at the top of the tower before the 01:42 timer runs out. Step on **blue blocks** to collect them (the target is 12). Colour-shifting blocks only count while they are blue.

## What's in the level
- **Blocks:** normal, blue target, moving, disappearing, bouncing, falling, cracked, colour-shifting, mystery (?), star boost, golden save blocks, spike traps, and blocks that appear as you approach.
- **Mystery blocks:** coins, gems, a tool, a heart, a trap (-5 s) or a shortcut bridge.
- **Falling:** an emergency platform catches you and carries you back to your last checkpoint for **-1 heart**. The level only ends when all hearts are gone. Golden save blocks below some gaps bounce you back up without costing a heart.
- **Checkpoints:** 5 glowing beacons. Activating one makes it your recovery point.
- **Adventure events**, in order:
  - a portal to another tower section
  - a STORM whose wind gusts push you sideways
  - a TOWER GUARD chase with a warning, a reveal, a chase meter and falling debris; a safe checkpoint drops the guard
  - a DRAGON ATTACK that burns the bridge ahead
  - a TOWER COLLAPSE behind you
  - a LAVA RUSH to the end portal
- **Level end:** the camera pulls back, the portal activates and coins fly in. You then get stars, coins, gems and bonus rewards (time, hearts, target).

## Project layout
- `app/src/main/java/com/blocktower/escape/core/`: platform-independent game code (level, physics, events, renderer, HUD)
- `app/src/main/java/com/blocktower/escape/`: Android host (Activity, game view and loop, Canvas renderer, sound)
- `app/src/main/assets/`: artwork taken from the Screen 4 design (background, boy, coins, HUD icons), block textures and fonts

Fonts: Fira Sans Condensed and Lilita One (SIL Open Font License, see `assets/fonts`).
