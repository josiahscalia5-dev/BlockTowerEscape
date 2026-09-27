# Level 5 art pipeline

Scripts that cut the Level 5 art out of `../level5_reference.png` (the approved design).
Run them from this folder with Python 3, NumPy, Pillow and OpenCV (contrib, for GrabCut):

- `guardian.py`, `checkpoint.py`, `log.py`, `gate.py`, `bridges.py`, `island.py`: cut the Temple Guardian,
  the CHECKPOINT arch, the spiked log, the portal temple, the mossy log bridges, the rope island and a
  floating island (traced outlines refined with GrabCut). Each writes `*_try.png` plus a preview.
- `plate_mask.py`: marks the HUD, the path and every gameplay object to take out of the background.
- `plate2.py`: builds the background plate from the design (jungle cliffs, waterfalls, mist, sky).

The results are copied into `app/src/main/assets/l5/`.
