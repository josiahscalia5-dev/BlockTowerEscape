"""Builds the Ancient Gate sprite and the gate-free background plate for Screen 4.

The gate at the top of the approved Screen 4 design (screen4_reference.png) is the level's
final destination. It is cut out of the reference as its own sprite (img/gate.png) so the game can
draw it as a landmark that grows as the player approaches and finally stands at the end of the path.
The older, smaller gate painted into the background plate is replaced by sky (img/bg_plate.jpg),
so only one gate is ever on screen. At the start of the level the sprite sits exactly where the
gate is in the reference.

Run from the repository root:  python3 design/make_gate_assets.py
"""
from PIL import Image, ImageFilter, ImageChops, ImageDraw
import numpy as np

REF = "design/screen4_reference.png"
PLATE_SRC = "design/bg_plate_original.jpg"
ASSETS = "app/src/main/assets/img/"

# gate crop in reference pixels (852 x 1846): towers, arch, fire, top of the staircase, island edges
GATE = (190, 76, 665, 345)

def gate_sprite():
    ref = Image.open(REF).convert("RGB")
    crop = ref.crop(GATE)
    w, h = crop.size
    a = np.ones((h, w), np.float32)
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    def ramp(d, width):
        return np.clip(d / width, 0.0, 1.0)
    # soft edges so the painting blends into the sky of the background plate
    a *= ramp(xs, 46) * ramp(w - 1 - xs, 46) * ramp(ys, 8) * ramp(h - 1 - ys, 60)
    # round off the lower corners (open sky under the floating island)
    cx, cy = w / 2, h * 0.36
    rx, ry = w * 0.62, h * 0.78
    d = ((xs - cx) / rx) ** 2 + ((ys - cy) / ry) ** 2
    a *= np.clip((1.12 - d) / 0.25, 0.0, 1.0)
    rgba = np.dstack([np.asarray(crop, np.float32), a * 255.0]).astype(np.uint8)
    Image.fromarray(rgba, "RGBA").save(ASSETS + "gate.png", optimize=True)
    print("gate.png", w, h)

def patch(arr, x0, y0, x1, y1, below=6):
    """Replaces a rectangle with a soft vertical blend of the sky rows just above and below it."""
    top = arr[y0 - 6:y0].mean(axis=0)
    bot = arr[y1 + below:y1 + below + 6].mean(axis=0)
    fill = arr.copy()
    for i in range(y1 - y0):
        u = i / (y1 - y0 - 1)
        fill[y0 + i] = top * (1 - u) + bot * u
    fill = np.asarray(Image.fromarray(fill.clip(0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(10)), np.float32)
    m = Image.new("L", (arr.shape[1], arr.shape[0]), 0)
    ImageDraw.Draw(m).rounded_rectangle((x0 + 8, y0 + 4, x1 - 8, y1 - 8), radius=30, fill=255)
    m = np.asarray(m.filter(ImageFilter.GaussianBlur(12)), np.float32)[..., None] / 255.0
    return arr * (1 - m) + fill * m

def plate_without_gate():
    plate = Image.open(PLATE_SRC).convert("RGB")
    arr = np.asarray(plate, np.float32).copy()
    arr = patch(arr, 455, 535, 655, 712, below=24)   # the old gate: fire, arch and its island
    arr = patch(arr, 412, 462, 470, 600)             # the thin spire beside it
    Image.fromarray(arr.clip(0, 255).astype(np.uint8)).save(ASSETS + "bg_plate.jpg", quality=92)
    print("bg_plate.jpg", plate.size)

if __name__ == "__main__":
    gate_sprite()
    plate_without_gate()
