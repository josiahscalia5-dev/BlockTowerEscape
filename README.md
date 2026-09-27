# Block Tower Escape — Screen 4 (Main Gameplay)

Native Android (Kotlin) build of **Screen 4: Main Gameplay**. Two levels are built, each to match its approved
design: **Level 5** (the jungle temple, `design/level5_reference.png`; the app opens this level) and **Level 23**
(the sky tower, `design/screen4_reference.png`). Home, World Map, Level Select and the other screens are
intentionally not started yet.

## Get the APK
Every push to `main` is built automatically by GitHub Actions (`.github/workflows/build-apk.yml`):
- **Releases** page → latest `preview-N` → `BlockTowerEscape-debug.apk` (download on your phone, allow "install unknown apps")
- or the `apk-build` branch, which also holds the build log

## Open in Android Studio
1. File → Open → select this folder, let Gradle sync (AGP 8.7.3, Kotlin 2.0.21, compileSdk 35, minSdk 24).
2. Run the `app` configuration on a phone or emulator (portrait).

No third-party libraries: rendering uses `android.graphics` on a hardware-accelerated view; sound effects are synthesized at first launch.

## Level 5 — the jungle temple
**Mission:** collect **10 blue blocks** and reach the **golden portal** at the top of the temple (01:15 on the clock,
three hearts, tools 3 / 2 / 3 / 2). The portal stays sealed until you have 10/10.

The start of the level reproduces the Level 5 design: the blue star block under the boy, the spike platform and the
floating ? block on the left, the ? block and the red spring button on the right, the magnet, the spiked log
swinging on its ropes, the stone steps up to the CHECKPOINT arch, the Temple Guardian watching from its ruins with
the warning sign, the shield, the lightning block and the coin trail winding up to the portal. The picture pieces
(portal temple, Guardian, checkpoint arch, spiked log, mossy log bridges, rope island) are cut from the design and
stand in the 3D world where the start camera sees them in the design; the camera was fitted to the design's blocks.

1. **Temple entrance** — the design's opening, first swinging log, first checkpoint; the Guardian leaps away
2. **Colourful block climb** — rising steps, gaps, an optional blue block on a raised pillar, a magnet box
3. **Moving stones and hazards** — sliding platforms, cracked and crumbling stones, a swinging log, timed spikes, vanishing stepping stones
4. **Tool trials** — blue blocks only the magnet reaches, a gap too wide to jump (BLOCK, LIGHTNING, or the vanishing stones), a spike run for the shield, a high ledge for the spring
5. **Temple Guardian chase** — *DANGER!* It bounds along the ruins ahead on the left, hurls boulders onto the path and closes in whenever you slow down; if it reaches the path it grabs you (game over — continue from the checkpoint just before the chase). The temple crumbles behind you.
6. **Checkpoint** — reach the arch and the Guardian's ledge gives way; a heart and a shield
7. **Temple climb** — spring buttons, vanishing stairs, a moving stone, crumbling blocks, spikes and two more swinging logs
8. **Final approach** — the temple collapses behind you on the way to the grand stairs
9. **Escape through the portal** — the portal blazes, the boy runs in, the camera pulls back, LEVEL COMPLETE

Swinging logs knock you back and cost a heart (the shield blocks them): watch the swing and pass when it is clear.
`design/level5/` holds the scripts that cut the Level 5 art out of its design.

## How to play Level 23
**Mission:** collect **12 blue blocks** and reach the **Ancient Gate**, the glowing gate at the top of the screen.
The gate is visible from the first second, grows as you climb toward it, and only opens once you have 12/12.

| Control | Action |
|---|---|
| Swipe up (lower screen) | Run forward. Short swipe = jog, medium = run, long = sprint. A quick flick keeps him running after you lift your thumb; a held swipe runs while you hold and eases to a stop when you let go |
| Swipe left / right | Steer toward that side of the path: a short swipe is a small correction, a medium one about one block, a long one up to 2½ blocks. Slide back to steer back |
| Swipe down | Slow down (short swipe) or stop (longer swipe); keep holding to step back carefully |
| Flick up at an edge | With a gap, step or spikes just ahead, an upward flick also jumps, timed to the edge (on open ground it only runs) |
| Big arrow (bottom-right) | Jump (hold for a higher jump) |
| Magnet | Pulls in nearby coins, tool bubbles and blue blocks, even ones out of reach (8 s) |
| Shield | Bubble that blocks one hit: spikes, debris, traps, the Tower Guard (10 s) |
| Lightning | Faster running and a much stronger jump (6 s) |
| Block tool (4th) | Builds a temporary platform across the gap ahead, up to 3 blocks (8 s) |
| Pause (top-right) | Pause / resume / restart |

Each tool has a use count (the badge), a duration ring while active and a short cooldown. More uses come from
floating tool bubbles and from tool / lightning blocks.

Swipe anywhere in the lower part of the screen that isn't a button. The pad at the bottom-left
shows what your thumb is doing (its knob follows the swipe, and the arrow for each recognised swipe lights up briefly).
Tiny accidental touches are ignored. Tools activate when you lift your finger on the button, so a swipe that starts on
a tool or slides across one never uses it. Steering keeps the boy on the blocks: he glides toward the side you swipe to,
stops at the edge of the path instead of walking off, and settles onto the middle of a block after a lane change.
To switch to a path across a gap, jump and steer in the air. The camera looks slightly into turns and rises a little with jumps.

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
with simulated touches (swipes in the movement area, taps on the jump and tool buttons), sent through the game's own
touch handlers: it falls once on purpose, lets the Tower Guard catch it once, continues from the checkpoint, uses every
tool and finishes at the gate. On the way it runs the movement tests: swipe forward, left and right, small corrections,
jumping while running (button and context flick), choosing a path at the fork, steering around obstacles,
falling and recovery, tools while running, and reaching the gate. It also checks that stray touches and swipes over tool
buttons do nothing, that a flick on open ground doesn't jump, that the boy never ends up inside a block, and that
steering stays smooth. It prints a timeline and a pass/fail checklist (`scenario=hearts` tests losing every heart and continuing).

```
./gradlew :sim:run --args="play level=5 out=sim-out"                          # Level 5 (level=23 for the sky tower)
./gradlew :sim:run --args="play out=sim-out"                                  # report only (Level 23)
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
