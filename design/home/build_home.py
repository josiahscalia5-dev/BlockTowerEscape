"""Builds every Home screen asset from the approved Home reference (../home_reference.png). Run from this folder.

The Home screen draws the reference as one picture (the plate) and puts the parts that change or respond on top:
  - home_plate.jpg   the reference with the top bar and the two "!" badges taken out, sky added above (tall
                     phones show it between the top bar and the title), the scene carried on for a short way below
                     the tagline (the gesture-bar strip) and softly mirrored scenery at the sides (tablets, foldables)
  - hud_profile.png, hud_wallet.png, hud_gear.png
                     the top bar, pinned to the top of the screen, with its numbers painted out (drawn live)
  - play.png, btn_daily.png, btn_missions.png, btn_vault.png, btn_settings.png
                     the buttons, cut out exactly where they are in the picture (pressed / released animation)
  - badge.png        the red "!" notification badge (shown only when something is waiting)
  - icon_daily.png, icon_missions.png, icon_vault.png, icon_settings.png
                     the buttons' pictures on transparent backgrounds (popup headers, vault chests)
"""
import cv2, numpy as np
from PIL import Image

OUT = '../../app/src/main/assets/home/'
ref = cv2.cvtColor(cv2.imread('../home_reference.png'), cv2.COLOR_BGR2RGB).astype(np.float32)
H, W = ref.shape[:2]
T, B, S = 640, 220, 200          # sky above, ground below, mirrored sides (plate pixels = reference pixels)
rng = np.random.default_rng(7)

def rrect_poly(x0, y0, x1, y1, r, n=8):
    pts = []
    for cx, cy, a0 in [(x1 - r, y0 + r, -90), (x1 - r, y1 - r, 0), (x0 + r, y1 - r, 90), (x0 + r, y0 + r, 180)]:
        for i in range(n + 1):
            a = np.radians(a0 + 90 * i / n); pts.append((cx + r * np.cos(a), cy + r * np.sin(a)))
    return np.array(pts, np.int32)

def circle_poly(cx, cy, r, n=32):
    return np.array([(cx + r * np.cos(a), cy + r * np.sin(a)) for a in np.linspace(0, 2 * np.pi, n, endpoint=False)], np.int32)

# ------------------------------------------------------------------ 1. the "!" badges come out
# (each badge sits on the top-right corner of a nearly symmetric button: fill it with that button's mirror image)
BADGES = [(148, 195, 31, 93.0), (143, 395, 31, 88.5)]      # centre, radius, the button's mirror axis
clean = ref.copy()
xs = np.arange(W)
for cx, cy, r, axis in BADGES:
    m = np.zeros((H, W), np.float32); cv2.circle(m, (cx, cy), r, 1.0, -1)
    m = np.clip(cv2.GaussianBlur(m, (0, 0), 2.0) * 1.4, 0, 1)[..., None]
    clean = clean * (1 - m) + ref[:, np.clip((2 * axis - xs).round().astype(int), 0, W - 1)] * m
# the badge itself, as a sprite
bx, by, br = 148, 195, 30
crop = ref[by - br:by + br, bx - br:bx + br]
am = np.zeros((2 * br, 2 * br), np.float32); cv2.circle(am, (br, br), br - 1, 255.0, -1)
am = cv2.GaussianBlur(am, (0, 0), 0.8)
Image.fromarray(np.dstack([crop, am]).clip(0, 255).astype(np.uint8), 'RGBA').save(OUT + 'badge.png')

# ------------------------------------------------------------------ 2. sprites (grabCut around a shape hint)
def cut(img, polys, box, name, band=4):
    img8 = img.clip(0, 255).astype(np.uint8)
    inside = np.zeros((H, W), np.uint8)
    for p in polys: cv2.fillPoly(inside, [p], 255)
    k = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * band + 1, 2 * band + 1))
    outer = cv2.dilate(inside, k); inner = cv2.erode(inside, k)
    mask = np.full((H, W), cv2.GC_BGD, np.uint8)
    mask[outer > 0] = cv2.GC_PR_BGD; mask[inside > 0] = cv2.GC_PR_FGD; mask[inner > 0] = cv2.GC_FGD
    bg = np.zeros((1, 65), np.float64); fg = np.zeros((1, 65), np.float64)
    cv2.grabCut(cv2.cvtColor(img8, cv2.COLOR_RGB2BGR), mask, None, bg, fg, 6, cv2.GC_INIT_WITH_MASK)
    m = np.where((mask == 1) | (mask == 3), 255, 0).astype(np.float32)
    m = cv2.GaussianBlur(m, (0, 0), 0.7)
    x0, y0, x1, y1 = box
    Image.fromarray(np.dstack([img8[y0:y1, x0:x1], m[y0:y1, x0:x1].clip(0, 255).astype(np.uint8)]), 'RGBA').save(OUT + name)
    return m

BUTTONS = {   # name: (frame x0, y0, x1, y1, corner radius, extra shape poking out of the frame, sprite box)
    'play.png':         ((275, 1180, 750, 1345, 44), [], (266, 1172, 760, 1356)),
    'btn_daily.png':    ((26, 183, 160, 345, 22), [rrect_poly(40, 165, 150, 200, 14)], (18, 158, 168, 353)),
    'btn_missions.png': ((22, 380, 155, 520, 22), [rrect_poly(75, 372, 105, 392, 6)], (14, 364, 163, 528)),
    'btn_vault.png':    ((868, 187, 1002, 347, 22), [rrect_poly(880, 168, 990, 205, 14)], (860, 160, 1010, 355)),
    'btn_settings.png': ((872, 380, 1002, 520, 22), [], (864, 372, 1010, 528)),
}
for name, (f, extra, box) in BUTTONS.items():
    cut(clean, [rrect_poly(*f)] + extra, box, name)

# the top bar, with its numbers painted out (the game draws the live ones)
hud = clean.copy()
TEXT = [(134, 27, 248, 63), (188, 67, 270, 90), (526, 31, 618, 69), (778, 31, 832, 69)]
tm = np.zeros((H, W), np.uint8)
for x0, y0, x1, y1 in TEXT: cv2.rectangle(tm, (x0, y0), (x1, y1), 255, -1)
hud = cv2.inpaint(hud.clip(0, 255).astype(np.uint8), tm, 5, cv2.INPAINT_NS).astype(np.float32)
cut(hud, [circle_poly(66, 60, 52), rrect_poly(96, 19, 338, 104, 18)], (8, 2, 346, 118), 'hud_profile.png')
cut(hud, [circle_poly(489, 51, 31), rrect_poly(470, 24, 912, 79, 10), circle_poly(716, 50, 34),
          rrect_poly(622, 23, 672, 78, 10), rrect_poly(856, 23, 908, 78, 10)], (452, 12, 918, 90), 'hud_wallet.png')
cut(hud, [rrect_poly(928, 16, 1006, 94, 16)], (920, 8, 1014, 102), 'hud_gear.png')

# the pictures inside the menu buttons (everything not the button's blue, joined to the picture's middle)
def icon(frame, seed, name, ylim):
    x0, y0, x1, y1 = frame
    sub = clean[y0:ylim, x0:x1].clip(0, 255).astype(np.uint8)
    hsv = cv2.cvtColor(sub, cv2.COLOR_RGB2HSV).astype(np.float32)
    h, s, v = hsv[..., 0] * 2, hsv[..., 1] / 255, hsv[..., 2] / 255
    blue = (h > 188) & (h < 240) & (s > 0.35)
    pic = ~blue
    pic = cv2.morphologyEx(pic.astype(np.uint8), cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    n, lab = cv2.connectedComponents(pic)
    keep = (lab == lab[seed[1] - y0, seed[0] - x0]).astype(np.uint8)
    keep = cv2.morphologyEx(keep, cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8))
    ff = keep.copy(); cv2.floodFill(ff, None, (0, 0), 1); keep = keep | (1 - ff)       # fill holes
    a = cv2.GaussianBlur(keep.astype(np.float32) * 255, (0, 0), 0.8)
    ys, xs_ = np.nonzero(keep)
    bb = (xs_.min(), ys.min(), xs_.max() + 1, ys.max() + 1)
    out = np.dstack([sub, a.clip(0, 255).astype(np.uint8)])[bb[1]:bb[3], bb[0]:bb[2]]
    Image.fromarray(out, 'RGBA').save(OUT + name)

icon((18, 150, 168, 353), (95, 225), 'icon_daily.png', 272)
icon((14, 364, 163, 528), (88, 430), 'icon_missions.png', 478)
icon((860, 160, 1010, 355), (935, 235), 'icon_vault.png', 272)
icon((864, 372, 1010, 528), (905, 430), 'icon_settings.png', 478)

# ------------------------------------------------------------------ 3. clouds
def fbm(h, w, cell, octaves=5, seed=0):
    """Smooth fractal noise (0..1): random grids at several scales, upsampled and summed."""
    r = np.random.default_rng(seed)
    out = np.zeros((h, w), np.float32); amp = 1.0; tot = 0.0
    for o in range(octaves):
        c = max(2, int(cell / (2 ** o)))
        g = r.random((h // c + 3, w // c + 3)).astype(np.float32)
        up = cv2.resize(g, ((w // c + 3) * c, (h // c + 3) * c), interpolation=cv2.INTER_CUBIC)[:h, :w]
        out += up * amp; tot += amp; amp *= 0.5
    return out / tot

def paint_clouds(base, density, white=(250, 252, 255), shadow=(178, 198, 234), light=14):
    """Cumulus shading on a density field: lit tops, soft bluish undersides."""
    d = np.clip(density, 0, 1)
    ds = cv2.GaussianBlur(d, (0, 0), 3)
    below = np.roll(ds, -light, axis=0)
    shade = np.clip(0.62 + 1.8 * (ds - below), 0, 1)          # top edges bright, lower parts in shadow
    col = np.array(shadow, np.float32) + (np.array(white, np.float32) - np.array(shadow, np.float32)) * shade[..., None]
    return base * (1 - d[..., None]) + col * d[..., None]

def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t)

# ------------------------------------------------------------------ 4. the plate
PW, PH = W + 2 * S, H + T + B
can = np.zeros((PH, PW, 3), np.float32)
art = clean.copy()

# --- sky: the top band of the picture (where the top bar was) and everything above it is one generated sky
SKY_TOP = np.array([2, 88, 196], np.float32)            # deepest blue, far above
# the picture's own sky along its top edge (above the top bar), smoothed: the generated sky ends in exactly it
SKY_ART = cv2.GaussianBlur(ref[0:4].mean(axis=0, keepdims=True), (0, 0), sigmaX=60, sigmaY=0.1)[0][None, :, :]
band = 120                                                # rows of the picture the top bar reaches (title from ~96)
skyh = T + band
ys = np.arange(skyh, dtype=np.float32)[:, None, None]
u = np.clip(ys / T, 0, 1) ** 1.3                          # 0 at the very top, 1 from the picture's top edge down
sky = SKY_TOP * (1 - u) + SKY_ART * u
# a lighter glow low on the horizon of the band, like the picture's sky
glow = np.exp(-((np.arange(W) - W * 0.5) / (W * 0.6)) ** 2)[None, :, None] * np.clip((ys - skyh * 0.5) / (skyh * 0.5), 0, 1)
sky += glow * np.array([30, 30, 20], np.float32)
n = fbm(skyh, W, 170, seed=11)
yy = np.arange(skyh, dtype=np.float32)[:, None]
thresh = 0.56 + 0.06 * (1 - yy / skyh)                   # sparser higher up
dens = smooth(thresh, thresh + 0.12, n) * (0.55 + 0.45 * yy / skyh)
sky = paint_clouds(sky, dens)
# blend: the top bar's footprint is first filled in from around it, then the generated sky fades in over the
# band's top rows across the whole width, handing over to the picture just above the title
hm = np.zeros((H, W), np.uint8)
cv2.circle(hm, (66, 61), 64, 255, -1); cv2.rectangle(hm, (92, 12), (345, 111), 255, -1)
cv2.rectangle(hm, (452, 12), (917, 90), 255, -1); cv2.rectangle(hm, (921, 8), (1013, 101), 255, -1)
filled = cv2.inpaint(art.clip(0, 255).astype(np.uint8), hm, 9, cv2.INPAINT_NS).astype(np.float32)
soft = cv2.GaussianBlur(filled, (0, 0), 4)
m = cv2.GaussianBlur(hm.astype(np.float32) / 255, (0, 0), 3)[..., None]
art = art * (1 - m) + soft * m                            # the fill, softened, only inside the footprint
fb = np.zeros((band, 1, 1), np.float32)
for y in range(band):
    fb[y] = 1.0 if y < 58 else (0.5 + 0.5 * np.cos(np.pi * (y - 58) / 34) if y < 92 else 0.0)
can[0:T, S:S + W] = sky[0:T]
can[T:T + H, S:S + W] = art
can[T:T + band, S:S + W] = sky[T:] * fb + art[0:band] * (1 - fb)

# --- below: the scene carries on under the tagline (the rows beneath the banner and its vines, reflected back
# and forth), softening into a light sky haze with distance; phones show only a short strip of it
strip = art[H - 34:H]
rows = []
while len(rows) < B:
    rows.extend(list(strip[::-1])); rows.extend(list(strip))
ext = np.array(rows[:B], np.float32)
HAZE = np.array([196, 214, 244], np.float32)
for i in range(B):
    u = i / B
    ext[i] = cv2.GaussianBlur(ext[i:i + 1], (0, 0), sigmaX=0.6 + 9 * u, sigmaY=0.1)[0] * (1 - 0.6 * u) + HAZE * 0.6 * u
can[T + H:, S:S + W] = ext

# --- sides: the picture mirrored, softened and a touch darker; where the menu buttons would be mirrored, sky
# and clouds instead (so no copy of a button ever shows beside the real one)
def side(strip, seed):
    out = cv2.GaussianBlur(strip, (0, 0), 5) * 0.9
    y0, y1 = T + 120, T + 580
    base = cv2.GaussianBlur(strip[y0 - 80:y1 + 80], (0, 0), 50)[80:-80]
    n = fbm(y1 - y0, S, 120, seed=seed)
    sky_part = paint_clouds(base, smooth(0.5, 0.62, n) * 0.9) * 0.9
    ys_ = np.arange(y1 - y0, dtype=np.float32)[:, None, None]
    fm = np.minimum(smooth(0, 50, ys_), smooth(0, 50, (y1 - y0) - ys_))
    out[y0:y1] = out[y0:y1] * (1 - fm) + sky_part * fm
    return out
can[:, 0:S] = side(can[:, S:2 * S][:, ::-1].copy(), 31)
can[:, S + W:] = side(can[:, W:W + S][:, ::-1].copy(), 37)

Image.fromarray(np.clip(can, 0, 255).astype(np.uint8)).save(OUT + 'home_plate.jpg', quality=88)
print('plate', PW, PH, 'art at', S, T)
