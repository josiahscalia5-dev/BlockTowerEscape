# Block Tower Escape

Native Android (Kotlin) portrait game. Screen flow:

**Splash → Home → Select Level → Gameplay**

| Screen | What's on it |
|---|---|
| Splash | Logo, the floating-island world with castle and portal, the boy running from the Guardian, loading bar (tracks the real loading of the gameplay) |
| Home | Level / XP profile, coins, gems, Daily Rewards, Missions, Prize Vault, Settings (sound / vibration), logo, boy and Guardian, **PLAY**, RUN • JUMP • COLLECT • ESCAPE |
| Select Level | Block path up to the castle and portal. Level 1 (Tutorial Adventure) is active, levels 2–5 unlock one by one, with stars, locks, labels and SOON. The same boy (back view) stands on the current level |
| Gameplay | The Screen 4 course (unchanged). Pause and the results / fail panels have a **LEVELS** button back to the map |

Progress (stars, unlocked levels, coins, gems, XP, settings) is saved on the phone.
Levels 2–5 are on the map but their courses are not built yet ("coming soon").

## Get the APK
Every push to `main` or to a `claude/...` branch is built automatically by GitHub Actions (`.github/workflows/build-apk.yml`):
- **Releases** page → latest `preview-N` → `BlockTowerEscape-debug.apk` (download on your phone, allow "install unknown apps")
- or the `apk-build` branch, which also holds the build log

## Open in Android Studio
1. File → Open → select this folder, let Gradle sync (AGP 8.7.3, Kotlin 2.0.21, compileSdk 35, minSdk 24).
2. Run the `app` configuration on a phone or emulator (portrait).

No third-party libraries: rendering uses `android.graphics` on a hardware-accelerated view; sound effects are synthesized at first launch.

## Android phone fit
- Edge to edge: the artwork fills the whole screen (no black or grey bars, nothing stretched). Buttons and text stay inside the **safe area**: status bar, navigation bar (gesture or 3-button), display cutout / notch and rounded display corners.
- Layouts are responsive, not fixed to one resolution. Short phones shrink the decoration (logo, hero art, sign) first. Buttons never shrink below Android's 48dp touch size. Tall phones show more of the world.
- The level map zooms out on wider screens (16:9, foldables, tablets) to show more scenery, so the whole path always fits.
- Checked on 11 screen profiles: 20:9, 19.5:9, 21:9, 22:9, 16:9, 720p, 480×854, Pixel 8 Pro, tablet and foldable, with both gesture and 3-button navigation.

## How to play (Level 1)
| Control | Action |
|---|---|
| Joystick (bottom-left) | Move: up = forward, down = back, left / right |
| Big arrow (bottom-right) | Jump (hold for a higher jump) |
| Magnet | Pulls in nearby coins and blue blocks (8 s) |
| Shield | Bubble that blocks one hit: spikes, debris, traps, the Tower Guard (10 s) |
| Lightning | Faster running and a stronger jump (6 s) |
| Block tool (4th) | Creates a safe block in the gap ahead (lasts 8 s) |
| Pause (top-right) or Back | Pause / resume / restart / back to the level map |

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
- `app/src/main/java/com/blocktower/escape/core/`: platform-independent game code
  - `App.kt` (screen flow, loading), `SplashScreen.kt`, `HomeScreen.kt`, `LevelSelectScreen.kt`, `Ui.kt` (safe area, buttons, drawing helpers), `Progress.kt`, `MenuArt.kt`
  - gameplay: level, physics, events, renderer, HUD
- `app/src/main/java/com/blocktower/escape/`: Android host (Activity, game view and loop, Canvas renderer, sound)
- `app/src/main/assets/`: artwork taken from the Screen 4 design (background, boy, coins, HUD icons), block textures and fonts
- `app/src/main/assets/menu/`: Splash / Home / Select Level artwork taken from the approved reference screens (world, level map, logo, the boy from the front and back, the Guardian, the title sign, button icons)

Fonts: Fira Sans Condensed and Lilita One (SIL Open Font License, see `assets/fonts`).
