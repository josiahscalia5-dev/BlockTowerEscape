# Block Tower Escape

Native Android (Kotlin) game: a boy climbs floating block towers, collects blue blocks and escapes through the
portal at the top before time runs out. The app runs **Splash → Home → Select Level → Level**. Built so far:

- **Splash screen** from the approved Splash artwork (`design/menu_reference.png`): the logo over the floating-island
  world, castle and portal, the same boy running from the Guardian, and a loading bar
- **Home screen** from the approved Home artwork (`design/home_reference.png`): the title, the boy running from the
  Block Tower Guardian toward the portal, DAILY REWARDS and MISSIONS on the left, PRIZE VAULT and SETTINGS on the
  right, the PLAY button and RUN • JUMP • COLLECT • ESCAPE, with a live top bar (player level and XP, coins,
  gems, + buttons, settings)
- **Level map** (Select Level) from the approved Select Level artwork: a path of big coloured blocks climbing toward
  the castle and the portal, one per level, with numbers, stars, padlocks, short level names and SOON, and the same
  boy (seen from behind) standing on the level you are up to
- **Six levels**: three teaching levels in the sky world, **Level 4** (the sky tower, built to match the Screen 4
  design `design/screen4_reference.png`), **Level 5** (the volcanic sky fortress, `design/level5_volcano_reference.png`)
  and **Level 6** (the sky temple with its rainbow slides, `design/level6_reference.png`)
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
One boy, one Guardian and one world on every screen: the splash, Home and the level map use the same cut-outs of
the same cartoon boy (red hoodie, blue jeans, red-and-white sneakers, backpack), the same Guardian, the same
floating islands, castle and portal and the same block colours as the levels, with the HUD's buttons and fonts.

- **Splash**: shown while the menus and block textures load (the bar follows the real loading), then it fades to
  Home by itself. No buttons.
- **Home**: the approved Home screen's pieces (top bar, the four menu buttons, logo, boy and Guardian, PLAY, the
  RUN • JUMP • COLLECT • ESCAPE plank) laid out for each phone over the world, which fills the screen edge to edge.
  PLAY opens the level map; DAILY REWARDS, MISSIONS, PRIZE
  VAULT, SETTINGS and the gear open their screens; the + buttons open GET MORE (where coins and gems come from).
  A red "!" badge shows only when something is waiting (a daily reward, a mission to claim, a chest to open).
  PLAY breathes, the portal pulses, coins twinkle and the monster's eyes glow. Back on the Home screen closes the app.
- **Player level**: finishing a level gives XP (30 + 20 per star, replays too); every 100 XP is a new player level.
- **Level map**: the whole path always fits between the SELECT LEVEL sign and the bottom of the screen. Tap a level
  for its card (objective, what the level introduces, the three star goals, best score)
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
- **Music**: a bright Home theme on the menus, an adventure theme in the sky levels and a darker D-minor variant with
  toms in the volcano fortress. A level starts calm; the drums build with the action, and a fast "rush" layer (arpeggios,
  open hats, fills) joins on the final approach to the portal and runs flat out in chases, rising lava and the final
  escape. It drops while paused and stops when the app goes to the background. Like the sound effects, it is
  synthesised on the phone at launch (no audio files).
- In a level, **pause**, **game over** and the **results** have a LEVEL MAP button; the results also have
  **NEXT LEVEL** and REPLAY. After the last level, NEXT LEVEL says more levels are on the way.

## Fits every Android phone
- **Edge to edge**: the game draws behind the hidden status and navigation bars and into the camera cut-out; the
  artwork covers the whole screen on every shape (no black or grey bars, no blurred filler, never stretched).
- **Safe area**: every button and every piece of text keeps inside the safe area: the status and navigation bars
  (as if shown, since they slide in on a swipe; gesture and 3-button navigation), the camera cut-out, the
  gesture strip and room for rounded display corners (`GameView.safeInsets`).
- **Responsive, not one resolution**: the menu layouts are solved for each display (`MenuKit.kt`, `HomeScreen`,
  `SplashScreen`, `LevelMap`). A shorter phone shrinks the decoration first (logo, the boy and Guardian, the
  title sign); buttons stay at least 48dp (the touch areas are padded to 48dp where a button is drawn smaller). A
  taller phone shows more world instead of empty space. On wider screens (16:9, tablets, foldables) the level map
  zooms out into extra scenery at its sides so the whole path still fits.
- **Checked** by `sim devices` on 12 display shapes (16:9 with and without 3-button bar, 18:9, 19.5:9 notch, 20:9
  gesture and 3-button, 21:9, 22:9, 480x854, 16:10 and 4:3 tablets, a foldable): it prints any element outside the
  safe area or overlapping another.

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

## Level 5 — the volcanic sky fortress (the approved Level 5 design)
**Mission:** collect **12 blue blocks** and reach the **Fortress Portal** (02:30 on the clock, three hearts, tools
3 / 2 / 3 / 2); bonus: catch the **Runaway Relic**. The portal stays sealed until you have 12/12.

The fortress with its stone face and glowing portal stands far away at the top of the screen from the first second,
exactly where the design shows it, over a sunset sky of floating castle islands and lava falls; it only slides into
its real place at the end of one long, dangerous route (about 300 rows) over a sea of lava. The coloured blocks stand
on fortress-stone pillars rising out of the lava, wooden bridges with chain railings cross the wider gaps, and towers
with fire bowls and red crown banners line the way. The fortress, the lava golem, the golden relic, the spiked mace,
the banner and the sky are cut from the design by `sim l5art` (`sim/.../L5Art.kt`, into `app/src/main/assets/l5v/`);
the design's HUD, loop, track and characters are taken out of the sky and the wing of the fortress hidden behind the
design's Time panel is rebuilt as the mirror of the other wing.

1. **The volcanic approach** — first lava gaps, the fortress courtyard, a wide bridge, spikes to steer round, the
   first **speed pads** (blue chevron blocks: a burst of speed), a long jump. **Checkpoint 1**
2. **Choose your path** — left: a safe bridge with coins; right: blue block and gems on moving blocks
3. **The great loop** — speed pads for the run-up, then a real loop: run in at its foot and the boy goes all the way
   round (he keeps his momentum; the climb slows him, the drop speeds him up, pushing forward adds pace), steering
   across its three lanes for the coins and round the spike plates (or JUMP to hop one). The camera swings out to
   show the whole ring. **Checkpoint 2** where it lets you out
4. **Runaway Relic** — *CHASE & COLLECT!* (HUD: 0/1). The glowing golden relic pops up ahead and runs off along the
   course on a fixed, readable route, hopping the gaps and steps, a little faster than a run: only a clean run that
   uses the speed pads on its route (or Lightning) catches it, near the end. **RELIC CAPTURED!** pays +250 coins,
   +10 gems and +2,500 score (sparkles, a coin burst, a flash and a short slow-motion) and the relic goes to the
   Prize Vault. Miss it and it simply gets away: no heart, no restart, no checkpoint lost. **Checkpoint 3**
5. **The Guardian chase** — *DANGER!* then *GUARDIAN APPROACHING!*: the lava golem climbs out of the lava beside
   the path and chases on the left, closing in when you slow down and hurling lava rocks onto the path, through
   spikes, a moving block, crumbling rows, a one-lane bridge and a swinging mace. It is as fast as in Level 4 and
   never blocks the way; if it catches you it is game over (continue from checkpoint 3). At **checkpoint 4** the
   bridge gives way under it and it sinks back into the lava
6. **The fire bridges** — vanishing stepping stones, swinging maces, moving blocks, spikes, lava-cracked and
   crumbling blocks, narrow bridges. **Checkpoint 5**
7. **The final ascent** — *FINAL ESCAPE!* the bridge collapses behind you as you climb: rising steps, speed pads,
   moving platforms, narrow steps; the portal grows as you get closer
8. **The fortress stairs and the portal** — the portal blazes, the boy runs in, the camera pulls back, LEVEL COMPLETE

Maces knock you back and cost a heart (the shield blocks them): watch the swing and pass when it is clear.
The HUD adds the route to the portal (a progress strip under the hearts), the score, and the relic's CHASE & COLLECT
panel, in the left column clear of the timer, wallet, target panel and tools.

## Level 6 — the sky temple (the approved Level 6 design)
**Mission:** collect **15 blue blocks** and reach the **Temple Portal** (02:55 on the clock, three hearts, tools
3 / 3 / 2 / 3); bonus: catch the **Runaway Relic**. The longest and hardest level so far (about 475 rows).

The temple with its glowing portal stands on the highest island at the top of the screen from the first second, as in
the design, over a blue sky of floating islands, waterfalls, ruins and a helicopter circling with a spinning rotor. The
course runs over a sea of clouds: coloured blocks on temple-stone islands with moss, palms and waterfalls pouring off
their edges, wooden bridges, braziers, and the design's **rainbow slides**. The temple, the stone golem, the
helicopter, the waterfalls and the sky are cut from the design by `design/level6/build_l6.py` (into
`app/src/main/assets/l6/`); the design's HUD, course, characters and hazards are taken out of the sky, since the game
draws them live.

**The rainbow slides.** Run into a slide's mouth and the boy sits down and rides it: he follows its curve, leans into
the bends and is carried up the walls by his speed (the channel banks under him), with his body turning with the
slide and his legs in place. The drop speeds him up, climbs slow him down, the glowing **speed rings** add a burst and
he keeps his momentum when he shoots out of the exit onto the next island. Swipe left / right to climb the walls
(coins run along the best line), JUMP to hop a spike plate. The camera follows him down, turning and tipping with the
channel so the way ahead stays in view. There are four to ride: the first rainbow slide, the fast slide at the fork,
the relic's slide and the Great Rainbow Run; the tubes curving round the islands at the start are scenery.

1. **The temple gardens** — speed pads, the first **laser gate** (jump the beam, or pass while it is off: it flickers
   before it fires), a moving block, a swinging mace, a **spiked block sliding across the path** on its rail, a gap
   for the block tool, vanishing stones. **Checkpoint 1**
2. **The Rainbow Slide** — the first long slide, swinging left, then right, down to an island below
3. **The waterfall islands** — moving blocks island to island, a laser gate between spiked blocks, vanishing stepping
   stones, then **the fork**: left, a short fast slide with a blue block; right, a block path with moving blocks, gems
   and a shield. **Checkpoint 2** where they meet
4. **Runaway Relic** — *CHASE & COLLECT!* (HUD: 0/1). The relic runs off along the course and rides the slide on its
   route too, but slower than you do: use the slide and the speed pads to catch it. **RELIC CAPTURED!** pays +250
   coins, +10 gems and +2,500 score and goes to the Prize Vault; miss it and it gets away, nothing lost.
   **Checkpoint 3**
5. **The Great Rainbow Run** — the longest slide: swooping bends, spike plates to steer round or hop, speed rings
6. **The hazard gardens** — a mace, a fast laser, two spiked blocks sliding out of step, vanishing stones, crumbling
   blocks, a narrow bridge under a mace, falling blocks, a spike row, a laser. **Checkpoint 4**
7. **The Guardian chase** — the stone golem with the glowing purple eyes rises beside the path and chases on the left,
   closing in when you slow down and hurling rocks: a moving block, crumbling rows, a laser, a one-lane bridge,
   spikes, falling blocks. At **checkpoint 5** the island gives way under it and it falls into the clouds
8. **The floating maze** — platforms sliding in turn over a long drop, a spring up to a high island, a laser,
   crumbling and vanishing stones, a sliding spiked block, a moving platform. **Checkpoint 6**
9. **The temple ascent** — *FINAL ESCAPE!* rising steps, lasers, moving platforms, a bridge under a mace, a sliding
   spiked block, while the way collapses behind you
10. **The temple stairs and the portal** — the portal blazes, the boy runs in, LEVEL COMPLETE

Lasers, maces and spiked blocks knock you back and cost a heart (the shield blocks them). The HUD is the same as in
Level 5 (route strip, score, the relic's CHASE & COLLECT panel).

## Controls
Swipes are the default. **SETTINGS → Controls → JOYSTICK** turns the movement pad into a thumb stick instead: push up
to run (a little is a jog, all the way a sprint), down to slow down and step back, left / right to steer (the further,
the quicker; he still settles onto the middle of a block). It drives the same eased movement as the swipes, and
jumping stays on the JUMP button. **Sensitivity** (low / normal / high) applies to both.

| Control (swipe mode) | Action |
|---|---|
| Swipe up (lower screen) | Run forward. Short swipe = jog, medium = run, long = sprint. A quick flick keeps him running after you lift your thumb; a held swipe runs while you hold and eases to a stop when you let go |
| Swipe left / right | Steer toward that side of the path: a short swipe is a small correction, a medium one about one block, a long one up to 2½ blocks. Slide back to steer back. On a rainbow slide it climbs that wall |
| Swipe down | Slow down (short swipe) or stop (longer swipe); keep holding to step back carefully |
| Flick up at an edge | With a gap, step or spikes just ahead, an upward flick also jumps, timed to the edge (on open ground it only runs) |
| Big arrow (bottom-right) | Jump (hold for a higher jump); on a slide, hop a spike plate |
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
missed day), the level map (a locked level stays shut), the level card, pause -> LEVEL MAP, Levels 1-6 in a row
through NEXT LEVEL (the autopilot plays each one), "more levels soon", missions, the vault, settings, the app closed
and opened again, and RESET. It checks unlocks, stars, the wallet (saved and on the HUD), relics, mission and chest
rewards, settings and the saved progress, and prints a pass/fail checklist.

```
./gradlew :sim:run --args="play level=6 out=sim-out"                          # one level (1-6; Level 4 by default)
./gradlew :sim:run --args="play out=sim-out video=sim-out/run.mp4 ffmpeg=ffmpeg"  # also record a video
./gradlew :sim:run --args="flow out=sim-out shots=1"                          # the whole app, a screenshot of each screen
./gradlew :sim:run --args="flow w=720 h=1560 out=sim-out video=sim-out/tour.mp4 ffmpeg=ffmpeg"  # the whole app as a video tour
./gradlew :sim:run --args="app out=sim-out"                                   # Home, map, popups and level cards
./gradlew :sim:run --args="devices out=sim-out"                               # every screen on 12 display shapes (layout audit)
./gradlew :sim:run --args="devices out=sim-out only=splash"                   # one screen (splash, home, map, daily, ... level6)
./gradlew :sim:run --args="music out=sim-out"                                 # the music themes as WAV files (calm and flat out)
./gradlew :sim:run --args="shot level=4 out=sim-out frames=240"               # one frame
./gradlew :sim:run --args="poses out=sim-out"                                 # the character animation poses (+ hips close up, run cycle)
./gradlew :sim:run --args="play level=5 relic=miss out=sim-out"               # Level 5, letting the Runaway Relic get away
./gradlew :sim:run --args="l5art out=sim-out"                                 # re-cut the Level 5 art from its design
./gradlew :sim:run --args="play level=6 relic=miss out=sim-out"               # Level 6, letting the Runaway Relic get away
./gradlew :sim:run --args="run level=6 z=45 frames=240 every=20 out=sim-out"  # start anywhere (here: the first slide), frames every 20
python3 design/level6/build_l6.py                                            # re-cut the Level 6 art from its design (LaMa, IS-Net, Real-ESRGAN)
```

## Project layout
- `app/src/main/java/com/blocktower/escape/core/`: platform-independent game code: the app and its screens (`App`,
  `SplashScreen`, `HomeScreen`, `LevelMap`, `Menus`, `MenuKit`, `Progress`), the levels (`LevelSpec`, `Levels123`, `Level`, `Level5`, `Level6`), physics,
  events, renderer, HUD and the character rig
- `app/src/main/java/com/blocktower/escape/`: Android host (Activity, game view and loop, Canvas renderer, sound, saved progress)
- `app/src/main/assets/`: artwork taken from the designs (Home screen, backgrounds, gates, boy, coins, HUD icons), block textures and fonts
- `design/`: the approved references and the scripts that build the game's artwork from them (`home/build_home.py`, `menu/` for the splash / Home / level map pieces, `level5/`, `level6/`, `make_gate_assets.py`)
- `sim/`: desktop playtest harness (not part of the APK)

The boy's animations (run, jump, fall, land, turn, collect, hurt, celebrate, rescue, capture, portal, the loop, the slides) come
from the original sprite cut into legs, body and arms at load time; nothing is repainted. The legs swap sides every
step by mirroring about the centre of the pants seat, and the cut between body and legs runs across the seat, which
is symmetric, so the hips have the same outline in either stride; the legs hang from the hip joint and move and lean
with the body, so they never separate from it (`sim poses` renders every pose, the hips close up and a run cycle at
gameplay size to check this).

Fonts: Fira Sans Condensed and Lilita One (SIL Open Font License, see `assets/fonts`).
