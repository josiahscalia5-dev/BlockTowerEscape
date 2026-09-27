# Block Tower Escape — Screen 4 (Main Gameplay)

Native Android (Kotlin) build of **Screen 4: Main Gameplay**, built to match the approved Screen 4 design
(`design/screen4_reference.png`). Only this screen is built. Home, World Map, Level Select and the other
screens are intentionally not started yet.

## Get the APK
Every push to `main` is built automatically by GitHub Actions (`.github/workflows/build-apk.yml`):
- **Releases** page → latest `preview-N` → `BlockTowerEscape-debug.apk` (download on your phone, allow "install unknown apps")
- or the `apk-build` branch, which also holds the build log

## Open in Android Studio
1. File → Open → select this folder, let Gradle sync (AGP 8.7.3, Kotlin 2.0.21, compileSdk 35, minSdk 24).
2. Run the `app` configuration on a phone or emulator (portrait).

No third-party libraries: rendering uses `android.graphics` on a hardware-accelerated view; sound effects are synthesized at first launch.

## How to play Level 23
**Mission:** collect **12 blue blocks** and reach the **Ancient Gate**, the glowing gate at the top of the screen.
The gate is visible from the first second, grows as you climb toward it, and only opens once you have 12/12.

| Control | Action |
|---|---|
| Joystick (bottom-left) | Move: up = forward, down = back, left / right |
| Big arrow (bottom-right) | Jump (hold for a higher jump) |
| Magnet | Pulls in nearby coins, tool bubbles and blue blocks, even ones out of reach (8 s) |
| Shield | Bubble that blocks one hit: spikes, debris, traps, the Tower Guard (10 s) |
| Lightning | Faster running and a much stronger jump (6 s) |
| Block tool (4th) | Builds a temporary platform across the gap ahead, up to 3 blocks (8 s) |
| Pause (top-right) | Pause / resume / restart |

Each tool has a use count (the badge), a duration ring while active and a short cooldown. More uses come from
floating tool bubbles and from tool / lightning blocks.

On an emulator you can use the keyboard: WASD or the arrow keys, Space to jump, 1–4 for the tools, P to pause.

## The level
The start of the level reproduces the Screen 4 design. After **3 · 2 · 1 · GO!** a small mission card appears, then the climb begins:

1. **Easy opening** — the winding path from the design: spring pads, ? blocks, spikes, a lava-cracked block, magnet and shield bubbles
2. **Sky platforms** — the first jumps and blue blocks, a hidden gem ledge
3. **Moving and crumbling blocks** — sliding platforms (safe middle route, risky blue-pillar route, a hidden bridge), cracked bridges, lava-cracked blocks, a secret shortcut
4. **Traps and difficult jumps** — spike rows to time or jump, a fork (left: safe + hidden heart, right: treasure), a trapped ? box
5. **Tower Guard chase** — *DANGER! TOWER GUARD APPROACHING!* The camera turns to show it rise behind you, then the tower collapses behind it while it closes in. Reach the checkpoint and it falls; get caught and it's game over.
6. **Checkpoint haven** — breathing room, a heart and a shield
7. **Tool trials** — blue blocks only the magnet can reach, a gap too wide to jump (block tool, speed, or vanishing stones), a spike run for the shield
8. **Final climb** — lava floods the tower from below
9. **Final escape** — the tower crumbles behind you on the run to the grand staircase and the Ancient Gate

**Blocks:** normal, blue target (glow; crack → shatter; counter updates at once; combo score), moving, cracked, lava-cracked (crumbling),
vanishing, hidden (appear on approach), spring pads, star boost, golden save blocks, spike blocks (armed / timed),
mystery ? blocks (coins, gems, tools, heart, shortcut, or a trap), tool bubbles, checkpoints.

**Falling:** a safety net catches you and flies you back to the last solid ground for **-1 heart**; golden save blocks
bounce you back for -2 s. **Time running out** costs a heart and adds 20 s. Only when all hearts are gone is it
**GAME OVER** — then *Continue* restarts from the last checkpoint with full hearts.

**Level complete:** slow motion, the camera pulls back, the gate blazes, the boy steps in, coins and gems fly to the
HUD, then the results: objectives, stars, score count-up, rewards and **NEXT LEVEL**.

## Headless playtest (`sim/`)
`sim` runs the same game code on the desktop JVM with a Java2D renderer and an autopilot that plays the whole level
with the joystick, jump and tool buttons: it falls once on purpose, lets the Tower Guard catch it once, continues
from the checkpoint, uses every tool and finishes at the gate. It prints a timeline and a pass/fail checklist.

```
./gradlew :sim:run --args="play out=sim-out"                                  # report only
./gradlew :sim:run --args="play out=sim-out video=sim-out/run.mp4 ffmpeg=ffmpeg"  # also record a video
./gradlew :sim:run --args="shot out=sim-out frames=240"                       # one frame
./gradlew :sim:run --args="poses out=sim-out"                                 # the character animation poses
```

## Project layout
- `app/src/main/java/com/blocktower/escape/core/`: platform-independent game code (level, physics, events, renderer, HUD, character rig)
- `app/src/main/java/com/blocktower/escape/`: Android host (Activity, game view and loop, Canvas renderer, sound)
- `app/src/main/assets/`: artwork taken from the Screen 4 design (background, gate, boy, coins, HUD icons), block textures and fonts
- `design/`: the approved Screen 4 reference and the script that cuts the gate out of it (`make_gate_assets.py`)
- `sim/`: desktop playtest harness (not part of the APK)

The boy's animations (run, jump, fall, land, turn, collect, hurt, celebrate, rescue, capture, portal) come from the
original sprite cut into legs, body and arms at load time; nothing is repainted.

Fonts: Fira Sans Condensed and Lilita One (SIL Open Font License, see `assets/fonts`).
