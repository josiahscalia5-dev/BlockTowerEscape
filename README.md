# Block Tower Escape

Native Android (Kotlin) game: a boy climbs floating block towers, collects blue blocks and escapes through the
portal at the top before time runs out. Built so far:

- **Home screen** from the approved Home artwork (`design/home_reference.png`): the title, the boy running from the
  Block Tower Guardian toward the portal, DAILY REWARDS and MISSIONS on the left, PRIZE VAULT and SETTINGS on the
  right, the PLAY button and RUN • JUMP • COLLECT • ESCAPE, with a live top bar (player level and XP, coins,
  gems, + buttons, settings)
- **Level map** (Select Level): a winding trail of blocks, one per level, locked levels with padlocks, stars on the
  finished ones, the boy standing on the level you are up to
- **Five levels**: three teaching levels in the sky world, **Level 4** (the sky tower, built to match the Screen 4
  design `design/screen4_reference.png`) and **Level 5** (the jungle temple, `design/level5_reference.png`)
- **Saved progress** on the phone: unlocked levels, best stars and scores, the coin and gem wallet, relics,
  missions, the daily streak and settings

## Get the APK
Every push to `main` or to a `claude/...` working branch is built automatically by GitHub Actions (`.github/workflows/build-apk.yml`):
- **Releases** page → latest `preview-N` → `BlockTowerEscape-debug.apk` (download on your phone, allow "install unknown apps")
- or the `apk-build` branch, which also holds the build log

## Open in Android Studio
1. File → Open → select this folder, let Gradle sync (AGP 8.7.3, Kotlin 2.0.21, compileSdk 35, minSdk 24).
2. Run the `app` configuration on a phone or emulator (portrait).

No third-party libraries: rendering uses `android.graphics` on a hardware-accelerated view; sound effects and music are synthesized at first launch.

## The app
- **Home**: the artwork is shown whole and undistorted on every display: it spans the full width (nothing at the
  sides is ever cut off) and fits between the camera cut-out and the gesture bar. The top bar is pinned to the top
  of the safe area; a taller phone gets its spare height as sky between the top bar and the title, a wider screen
  (tablet, foldable) gets soft scenery at the sides. PLAY opens the level map; DAILY REWARDS, MISSIONS, PRIZE
  VAULT, SETTINGS and the gear open their screens; the + buttons open GET MORE (where coins and gems come from).
  A red "!" badge shows only when something is waiting (a daily reward, a mission to claim, a chest to open).
  PLAY breathes, the portal pulses, coins twinkle and the monster's eyes glow. Back on the Home screen closes the app.
- **Player level**: finishing a level gives XP (30 + 20 per star, replays too); every 100 XP is a new player level.
- **Level map**: tap a level for its card (objective, what the level introduces, the three star goals, best score)
  and PLAY. Finished levels show their stars, the next locked level says which level opens it, and tapping a locked
  level explains the same. A level opens when the one before it is finished: back on the map, its padlock shakes and
  bursts off, the block colours in with a ring of light and **NEW!**, and the boy hops across to it.
- **Stars**: 1 for finishing (the portal only opens once the blue-block objective is done), 2 for also collecting the
  level's coin goal, 3 for also finishing with the level's time goal left and no game over. The results screen shows
  which goals were met. Replaying never lowers your best stars.
- **Wallet**: coins and gems picked up in a level plus the level reward (more for more stars) are added to the wallet,
  which the HUD shows during play.
- **Tools unlock as you go**: the Magnet in Level 2, the Shield in Level 3, Lightning and the Block tool in Level 4.
  A locked tool shows a padlock on its button.
- **DAILY REWARDS**: a 7-day calendar, one claim per day; missing a day starts the streak over.
- **MISSIONS**: goals across all levels (finish levels, blue blocks, coins, stars, tools, mystery blocks, a flawless
  level, escaping a chase); CLAIM pays the reward once.
- **PRIZE VAULT**: a relic for each level you finish, and three treasure chests that open at 5, 10 and 15 stars.
- **SETTINGS**: sound, music, vibration, controls (swipe / joystick), sensitivity (low / normal / high) and
  RESET PROGRESS (asks first).
- **Music**: a bright Home theme on the menus, an adventure theme in the sky levels and a marimba-and-toms variant in
  the jungle temple. A level starts calm; the drums build with the action, and a fast "rush" layer (arpeggios,
  open hats, fills) joins on the final approach to the portal and runs flat out in chases, rising lava and the final
  escape. It drops while paused and stops when the app goes to the background. Like the sound effects, it is
  synthesised on the phone at launch (no audio files).
- In a level, **pause**, **game over** and the **results** have a LEVEL MAP button; the results also have
  **NEXT LEVEL** and REPLAY. After the last level, NEXT LEVEL says more levels are on the way.

## Levels 1–3 — the sky world
Each ends at a block portal that opens once the blue blocks are collected.

1. **Tutorial Adventure** — tips on screen teach running, steering and jumping on a wide, gentle path with bends,
   half steps and a checkpoint. Falls are free here: the safety net catches you without costing a heart.
2. **First Challenge** — wider gaps, slow moving blocks, mystery blocks, the first spikes, and the **Magnet** for blue
   blocks on islands out of reach.
3. **Mechanics Begin** — a moving-block ferry, cracked bridges that break if you stop, vanishing stepping stones,
   spike rows (jump them or use the new **Shield**), timed spikes, a fork with an optional treasure route, *MAGICAL
   WIND* over the cracked bridge (streaks of wind warn of each gust, which pushes you toward the spikes at the sides:
   steer against it), and fast moving blocks before the portal.

From the first second, a pillar of light marks where the portal is; up close the portal itself glows brighter and
more magic spills out of it the nearer you get.

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

## Controls
Swipes are the default. **SETTINGS → Controls → JOYSTICK** turns the movement pad into a thumb stick instead: push up
to run (a little is a jog, all the way a sprint), down to slow down and step back, left / right to steer (the further,
the quicker; he still settles onto the middle of a block). It drives the same eased movement as the swipes, and
jumping stays on the JUMP button. **Sensitivity** (low / normal / high) applies to both.

| Control (swipe mode) | Action |
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

## Level 4 — the sky tower (the Screen 4 design)
**Mission:** collect **12 blue blocks** and reach the **Ancient Gate**, the glowing gate at the top of the screen.
The gate is visible from the first second, grows as you climb toward it, and only opens once you have 12/12.

The start of the level reproduces the Screen 4 design. After **3 · 2 · 1 · GO!** a small mission card appears, then the climb begins:

1. **Easy opening** — the winding path from the design: spring pads, ? blocks, spikes, a lava-cracked block, magnet and shield bubbles
2. **Sky platforms** — the first jumps and blue blocks, a hidden gem ledge
3. **Moving and crumbling blocks** — sliding platforms (safe middle route, risky blue-pillar route, a hidden bridge), cracked bridges, lava-cracked blocks, a secret shortcut
4. **Traps and difficult jumps** — spike rows to time or jump, a fork (left: safe + hidden heart, right: treasure), a trapped ? box
5. **Tower Guard chase** — *DANGER!* then *TOWER GUARD APPROACHING!* The camera turns to show it rise behind you, then the tower collapses behind it while it closes in. Reach the checkpoint and it falls; get caught and it's game over.
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
`sim` runs the same game code on the desktop JVM with a Java2D renderer and an autopilot that plays a whole level
with simulated touches (swipes in the movement area, taps on the jump and tool buttons), sent through the game's own
touch handlers: it falls once on purpose, lets the chaser catch it once, continues from the checkpoint, uses every
tool the level has and finishes at the portal. On the way it runs the movement tests: swipe forward, left and right,
small corrections, jumping while running (button and context flick), choosing a path at the fork, steering around
obstacles, falling and recovery, tools while running, and reaching the portal. It also checks that stray touches and
swipes over tool buttons do nothing, that a flick on open ground doesn't jump, that the boy never ends up inside a
block, and that steering stays smooth. It prints a timeline and a pass/fail checklist of what that level contains
(`scenario=hearts` tests losing every heart and continuing; `scenario=clear` just plays through;
`controls=joystick` plays the level with the joystick instead of swipes).

`flow` plays the whole app from a fresh install by tapping the screen: Home, daily rewards (claim, the next day, a
missed day), the level map (a locked level stays shut), the level card, pause -> LEVEL MAP, Levels 1-5 in a row
through NEXT LEVEL (the autopilot plays each one), "more levels soon", missions, the vault, settings, the app closed
and opened again, and RESET. It checks unlocks, stars, the wallet (saved and on the HUD), relics, mission and chest
rewards, settings and the saved progress, and prints a pass/fail checklist.

```
./gradlew :sim:run --args="play level=5 out=sim-out"                          # one level (1-5; Level 4 by default)
./gradlew :sim:run --args="play out=sim-out video=sim-out/run.mp4 ffmpeg=ffmpeg"  # also record a video
./gradlew :sim:run --args="flow out=sim-out shots=1"                          # the whole app, a screenshot of each screen
./gradlew :sim:run --args="flow w=720 h=1560 out=sim-out video=sim-out/tour.mp4 ffmpeg=ffmpeg"  # the whole app as a video tour
./gradlew :sim:run --args="app out=sim-out"                                   # Home, map, popups and level cards
./gradlew :sim:run --args="devices out=sim-out"                               # every screen on 8 display shapes (layout audit)
./gradlew :sim:run --args="music out=sim-out"                                 # the music themes as WAV files (calm and flat out)
./gradlew :sim:run --args="shot level=4 out=sim-out frames=240"               # one frame
./gradlew :sim:run --args="poses out=sim-out"                                 # the character animation poses
```

## Project layout
- `app/src/main/java/com/blocktower/escape/core/`: platform-independent game code: the app and its screens (`App`,
  `HomeScreen`, `LevelMap`, `Menus`, `Progress`), the levels (`LevelSpec`, `Levels123`, `Level`, `Level5`), physics,
  events, renderer, HUD and the character rig
- `app/src/main/java/com/blocktower/escape/`: Android host (Activity, game view and loop, Canvas renderer, sound, saved progress)
- `app/src/main/assets/`: artwork taken from the designs (Home screen, backgrounds, gates, boy, coins, HUD icons), block textures and fonts
- `design/`: the approved references and the scripts that build the game's artwork from them (`home/build_home.py`, `level5/`, `map/`, `make_gate_assets.py`)
- `sim/`: desktop playtest harness (not part of the APK)

The boy's animations (run, jump, fall, land, turn, collect, hurt, celebrate, rescue, capture, portal) come from the
original sprite cut into legs, body and arms at load time; nothing is repainted.

Fonts: Fira Sans Condensed and Lilita One (SIL Open Font License, see `assets/fonts`).
