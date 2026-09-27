# Working on Block Tower Escape

## Always save to GitHub
- After finishing (or completing a step of) every task: commit and push to the working branch right away,
  then tell the user it was saved to GitHub. Don't wait to be asked.
- Every push to `main` or a `claude/...` branch builds the APK on GitHub Actions (Releases → latest `preview-N`).

## Ground rules from the owner
- Don't start over or redesign. Keep the approved artwork (Home, Level 4 sky tower, Level 5 volcanic sky fortress
  from design/level5_volcano_reference.png),
  the cartoon boy, the HUD and the Block Tower Escape identity; polish the implementation underneath.
- Splash, Home and the level map use the same boy, Guardian and world (from design/menu_reference.png and
  design/home_reference.png): never swap in a different character.
- Every screen must fit any Android display (short/tall phones, tablets, foldables) and respect the camera
  cut-out, system bars, gesture-navigation areas and rounded corners; check with `sim devices`.

## Testing without Android
- The desktop harness in `sim/` runs the same game code (see README "Headless playtest"):
  `play level=N` (autopilot), `flow` (whole app from a fresh install), `devices` (every screen on many display shapes).
