"""Level 6 (the sky temple) artwork, cut from the approved reference design/level6_reference.png (941 x 1672).

Nothing is painted by hand: every piece comes from the reference's own pixels.

  gate.png      the sky temple and its portal (the level's destination): traced outline refined with GrabCut
  guardian.png  the stone Guardian with the purple eyes (IS-Net segmentation, the palm in front of it removed)
  heli.png      the yellow helicopter (IS-Net segmentation; the game spins its rotor)
  falls.png     a strip of the reference's waterfall, made to tile vertically (the game scrolls it)
  bg_plate.jpg  the sky: blue sky, clouds, the floating islands with their waterfalls and ruins, and the far path
                of blocks leading up to the temple. The HUD, the temple, the Guardian, the helicopter and the near
                course (blocks, slides, hazards, the boy, the relic, coins) are taken out and the holes inpainted
                (LaMa), since the game draws all of those live.

Cut-outs are upscaled with Real-ESRGAN (colour and alpha separately) so they stay sharp on phones.

Tools and models: see design/menu/README.md (lama.py, seg.py, sprite.py, upscale.py; models in design/menu/models).
  python3 design/level6/build_l6.py            writes app/src/main/assets/l6/ and previews to sim-out/l6art/
"""
import os
import subprocess
import sys

import cv2
import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
MENU = os.path.join(ROOT, 'design', 'menu')
sys.path.insert(0, MENU)
import lama  # noqa: E402
import seg  # noqa: E402
import sprite  # noqa: E402

REF = os.path.join(ROOT, 'design', 'level6_reference.png')
OUT = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'l6')
PREV = os.path.join(ROOT, 'sim-out', 'l6art')
TMP = os.path.join(HERE, 'hs')
for d in (OUT, PREV, TMP):
    os.makedirs(d, exist_ok=True)

ref = Image.open(REF).convert('RGB')
W, H = ref.size
A = np.asarray(ref)

# ------------------------------------------------------------------ outlines (reference pixels)
TEMPLE = [(556, 250), (556, 200), (558, 160), (563, 156), (568, 147), (574, 156), (580, 158), (583, 150), (585, 120),
          (592, 116), (600, 106), (608, 116), (613, 120), (613, 146), (620, 144), (626, 140), (632, 144), (637, 142),
          (640, 126), (646, 122), (652, 112), (660, 116), (668, 122), (671, 142), (678, 140), (684, 138), (690, 142),
          (698, 140), (700, 118), (705, 114), (711, 104), (718, 114), (726, 118), (727, 150), (732, 156), (740, 144),
          (746, 155), (751, 158), (752, 200), (752, 250)]

# the stone Guardian: left fist, head with the jaw, right fist (its body fades into the clouds below)
GOLEM = [(36, 465), (38, 446), (46, 428), (58, 416), (74, 409), (92, 411), (106, 418), (116, 428), (124, 422), (134, 412),
         (150, 407), (166, 411), (180, 409), (196, 415), (210, 427), (223, 440), (233, 459), (239, 480), (237, 500),
         (240, 511), (256, 507), (273, 511), (286, 521), (293, 536), (291, 552), (281, 562), (263, 566), (246, 561),
         (234, 551), (212, 556), (190, 561), (168, 560), (148, 556), (128, 550), (112, 538), (98, 523), (80, 516),
         (60, 511), (46, 501), (39, 484)]

# the near course: blocks, slides, hazards, coins, the boy and the relic (the game draws all of these)
COURSE = [(250, 830), (300, 700), (380, 590), (470, 530), (560, 508), (720, 505), (775, 545), (792, 700), (815, 790),
          (815, 1000), (812, 1140), (740, 1140), (740, 1340), (0, 1340), (0, 1090), (140, 1068), (152, 950), (132, 880),
          (200, 840)]
# the helicopter's blurred rotor blades (the game spins its own)
ROTOR = [(226, 186), (262, 178), (300, 181), (346, 203), (432, 229), (436, 247), (398, 246), (338, 224), (298, 206), (258, 196), (226, 204)]
# near hazards outside that outline (spiked balls and the laser arm)
SPOTS = [(300, 755, 366, 822), (380, 652, 420, 698), (634, 702, 692, 762), (738, 1140, 915, 1272)]
# the reference's HUD
HUD = [(0, 0, W, 103), (0, 0, 132, 128), (820, 0, W, 118), (126, 98, 322, 170), (8, 162, 214, 272), (8, 274, 234, 402), (740, 162, 934, 270),
       (764, 266, 934, 492), (810, 498, W, 902), (0, 1330, W, H)]


def poly_mask(poly, grow=0):
    m = Image.new('L', (W, H), 0)
    ImageDraw.Draw(m).polygon(poly, fill=255)
    a = np.asarray(m) > 127
    if grow > 0:
        a = cv2.dilate(a.astype(np.uint8), np.ones((grow * 2 + 1, grow * 2 + 1), np.uint8)) > 0
    return a


def rect_mask(rects):
    m = np.zeros((H, W), bool)
    for x0, y0, x1, y1 in rects:
        m[max(0, y0):min(H, y1), max(0, x0):min(W, x1)] = True
    return m


def save_prev(name, im):
    im.save(os.path.join(PREV, name))


def upscale_rgba(im, factor):
    """Real-ESRGAN x4 on colour and alpha separately, then resize to [factor] (premultiplied, no halos)."""
    from scipy import ndimage
    a = np.asarray(im).astype(np.float32) / 255
    solid = a[..., 3] > 0.5
    if solid.any():
        idx = ndimage.distance_transform_edt(~solid, return_distances=False, return_indices=True)
        rgb = a[..., :3][idx[0], idx[1]]
    else:
        rgb = a[..., :3]
    Image.fromarray((rgb * 255 + .5).astype(np.uint8)).save(os.path.join(TMP, '_rgb.png'))
    Image.fromarray((np.repeat(a[..., 3:4], 3, 2) * 255 + .5).astype(np.uint8)).save(os.path.join(TMP, '_a.png'))
    for n in ('_rgb', '_a'):
        subprocess.run([sys.executable, os.path.join(MENU, 'upscale.py'), os.path.join(TMP, n + '.png'), os.path.join(TMP, n + '4.png'),
                        os.path.join(MENU, 'models', 'RealESRGAN_x4plus_anime_6B.pth')], check=True, capture_output=True)
    w, h = im.size
    tw, th = round(w * factor), round(h * factor)
    R = Image.open(os.path.join(TMP, '_rgb4.png')).resize((tw, th), Image.LANCZOS)
    Al = Image.open(os.path.join(TMP, '_a4.png')).convert('L').resize((tw, th), Image.LANCZOS)
    out = R.convert('RGBA')
    out.putalpha(Al)
    return out


def upscale_rgb(im, factor):
    im.save(os.path.join(TMP, '_p.png'))
    subprocess.run([sys.executable, os.path.join(MENU, 'upscale.py'), os.path.join(TMP, '_p.png'), os.path.join(TMP, '_p4.png'),
                    os.path.join(MENU, 'models', 'RealESRGAN_x4plus_anime_6B.pth')], check=True, capture_output=True)
    w, h = im.size
    return Image.open(os.path.join(TMP, '_p4.png')).resize((round(w * factor), round(h * factor)), Image.LANCZOS)


def grabcut_poly(poly, box, band=6):
    """Matte for a traced outline: sure inside / outside away from it, GrabCut decides the band by colour."""
    x0, y0, x1, y1 = box
    inside = poly_mask(poly)
    k = np.ones((band * 2 + 1, band * 2 + 1), np.uint8)
    sure_fg = cv2.erode(inside.astype(np.uint8), k) > 0
    maybe = cv2.dilate(inside.astype(np.uint8), k) > 0
    gc = np.full((H, W), cv2.GC_BGD, np.uint8)
    gc[maybe] = cv2.GC_PR_BGD
    gc[inside] = cv2.GC_PR_FGD
    gc[sure_fg] = cv2.GC_FGD
    sub = np.ascontiguousarray(A[y0:y1, x0:x1][..., ::-1])
    m = np.ascontiguousarray(gc[y0:y1, x0:x1])
    bgd = np.zeros((1, 65), np.float64); fgd = np.zeros((1, 65), np.float64)
    cv2.grabCut(sub, m, None, bgd, fgd, 6, cv2.GC_INIT_WITH_MASK)
    full = np.zeros((H, W), bool)
    full[y0:y1, x0:x1] = (m == cv2.GC_FGD) | (m == cv2.GC_PR_FGD)
    return full


def build_gate():
    box = (544, 96, 764, 258)
    fg = grabcut_poly(TEMPLE, box, 5)
    x0, y0, x1, y1 = box
    a = fg[y0:y1, x0:x1].astype(np.float32)
    a = cv2.GaussianBlur(a, (0, 0), 0.8)
    a = np.clip((a - 0.5) * 1.8 + 0.5, 0, 1)
    # the plaza at the foot of the temple fades out (the course's own plaza and stairs are in front of it)
    ys = np.arange(y0, y1, dtype=np.float32)[:, None]
    a *= np.clip((254 - ys) / 16, 0, 1)
    rgb = A[y0:y1, x0:x1].astype(np.float32)
    col = sprite.defringe(rgb, a)
    rgba = np.dstack([np.clip(col, 0, 255).astype(np.uint8), (a * 255 + .5).astype(np.uint8)])
    im = Image.fromarray(rgba, 'RGBA')
    big = upscale_rgba(im, 2.0)
    big.save(os.path.join(OUT, 'gate.png'), optimize=True)
    save_prev('gate_prev.png', sprite.preview(big, scale=1.0))
    print('gate.png', big.size, 'portal centre in the picture:', ((653 - x0) / (x1 - x0), (190 - y0) / (y1 - y0)))
    return fg


def seg_cut(box, name, factor, fix=None, thr=0.5, min_area=400, feather=1.0):
    x0, y0, x1, y1 = box
    crop = ref.crop(box)
    m = seg.mask_general(crop)
    if fix is not None:
        m = fix(m)
    save_prev(name.replace('.png', '_mask.png'), Image.fromarray((m * 255).astype(np.uint8)))
    im, off = sprite.make(np.asarray(crop), m, thr=thr, min_area=min_area, max_hole=3000, feather=feather)
    big = upscale_rgba(im, factor)
    big.save(os.path.join(OUT, name), optimize=True)
    save_prev(name.replace('.png', '_prev.png'), sprite.preview(big, scale=1.0))
    print(name, big.size, 'at', (x0 + off[0], y0 + off[1]), 'size in the reference', im.size)
    full = np.zeros((H, W), bool)
    full[y0:y1, x0:x1] = m > thr
    return full


def cut_poly(poly, box, name, factor, band=5, fade=None):
    """A traced outline refined by GrabCut, feathered, defringed and upscaled."""
    x0, y0, x1, y1 = box
    fg = grabcut_poly(poly, box, band)
    a = cv2.GaussianBlur(fg.astype(np.float32), (0, 0), 0.9)
    a = np.clip((a - 0.5) * 1.8 + 0.5, 0, 1)[y0:y1, x0:x1]
    if fade is not None:
        a *= fade(a.shape)
    rgb = A[y0:y1, x0:x1].astype(np.float32)
    col = sprite.defringe(rgb, a)
    im = Image.fromarray(np.dstack([np.clip(col, 0, 255).astype(np.uint8), (a * 255 + .5).astype(np.uint8)]), 'RGBA')
    big = upscale_rgba(im, factor)
    big.save(os.path.join(OUT, name), optimize=True)
    save_prev(name.replace('.png', '_prev.png'), sprite.preview(big, scale=1.0))
    print(name, big.size, 'box', box)
    return fg


def build_guardian():
    def fade(shape):
        # its body fades into the clouds below the jaw
        ys = np.arange(shape[0], dtype=np.float32)[:, None] + 400
        return np.clip((572 - ys) / 18, 0, 1)
    def no_palm(shape):
        # the palm fronds in front of its left fist: yellow-green foliage there is not the Guardian
        k = fade(shape)
        sub = A[400:400 + shape[0], 30:30 + shape[1]].astype(np.float32)
        r, g, b = sub[..., 0], sub[..., 1], sub[..., 2]
        leaf = (g > b + 18) & (g > 90) & (r > b)
        ys, xs = np.mgrid[0:shape[0], 0:shape[1]]
        near = (xs + 30 < 78) & (ys + 400 > 436) & (ys + 400 < 540)
        m = (leaf & near).astype(np.uint8)
        m = cv2.dilate(m, np.ones((5, 5), np.uint8)).astype(np.float32)
        return k * (1 - cv2.GaussianBlur(m, (0, 0), 1.0))
    return cut_poly(GOLEM, (30, 400, 300, 572), 'guardian.png', 2.0, 5, no_palm)


def build_heli():
    return seg_cut((226, 162, 436, 322), 'heli.png', 2.0, thr=0.45, min_area=200, feather=0.8)


def build_falls():
    # the big waterfall on the left island: a vertical strip, cross-faded onto itself so it tiles
    x0, x1, y0, y1 = 127, 165, 792, 902
    strip = A[y0:y1, x0:x1].astype(np.float32)
    h = strip.shape[0]
    ov = 24
    body = strip[:h - ov].copy()
    for i in range(ov):
        k = i / ov
        body[i] = strip[h - ov + i] * (1 - k) + strip[i] * k
    im = Image.fromarray(np.clip(body, 0, 255).astype(np.uint8))
    big = upscale_rgb(im, 2.0)
    big.save(os.path.join(OUT, 'falls.png'), optimize=True)
    print('falls.png', big.size)


HORIZON = 700  # below this row the plate is sky and clouds (the game's 3D cloud sea covers it in play)


def fbm(w, h, sx, sy, seed, octaves=5):
    """Fractal value noise (smooth, deterministic)."""
    rng = np.random.default_rng(seed)
    out = np.zeros((h, w), np.float32)
    amp = 0.5
    for o in range(octaves):
        fx, fy = sx * (2 ** o), sy * (2 ** o)
        gw, gh = int(w / fx) + 3, int(h / fy) + 3
        g = rng.random((gh, gw)).astype(np.float32)
        up = cv2.resize(g, (int(gw * fx), int(gh * fy)), interpolation=cv2.INTER_CUBIC)[:h, :w]
        out += up * amp
        amp *= 0.5
    return out


def cloud_sky():
    """The sky and clouds under the islands, in the reference's own colours: sky blue high up, pale lavender
    cloud banks lower down, lit from above."""
    ys = np.arange(H, dtype=np.float32)[:, None]
    sky_hi = np.array([118, 170, 238], np.float32); sky_lo = np.array([196, 214, 246], np.float32)
    k = np.clip((ys - 420) / 420, 0, 1)[..., None]
    base = sky_hi * (1 - k) + sky_lo * k
    base = np.broadcast_to(base, (H, W, 3)).copy()
    n = fbm(W, H, 90, 34, 6)
    under = fbm(W, H, 90, 34, 6)[np.clip(np.arange(H) - 9, 0, H - 1)]
    bank = np.clip((n - 0.42) / 0.26, 0, 1)
    shade = np.clip((under - n) * 3, 0, 1)
    cloud = np.array([246, 242, 252], np.float32); lav = np.array([214, 204, 238], np.float32)
    dens = np.clip((ys - 520) / 240, 0.25, 1)[..., None]
    c = cloud * (1 - shade[..., None] * 0.6) + lav * (shade[..., None] * 0.6)
    out = base * (1 - bank[..., None] * dens) + c * (bank[..., None] * dens)
    return out


def build_plate(gate_fg, guard, heli):
    hole = (cv2.dilate(rect_mask(HUD).astype(np.uint8), np.ones((13, 13), np.uint8)) > 0) | poly_mask(COURSE) | rect_mask(SPOTS)
    hole |= cv2.dilate(gate_fg.astype(np.uint8), np.ones((9, 9), np.uint8)) > 0
    hole |= rect_mask([(644, 60, 666, 132)])  # the portal's beam of light (drawn by the game)
    hole |= cv2.dilate(guard.astype(np.uint8), np.ones((13, 13), np.uint8)) > 0
    hole |= cv2.dilate(heli.astype(np.uint8), np.ones((11, 11), np.uint8)) > 0
    hole |= poly_mask(ROTOR, 3)
    sky = cloud_sky()
    # below the horizon the plate is the cloud sky: LaMa then only has to join the islands to it
    src = A.astype(np.float32).copy()
    src[HORIZON:] = sky[HORIZON:]
    hole[HORIZON:] = False
    top = HORIZON + 60
    m = hole[:top]
    # the model reads what is under the mask: start it from a smooth diffusion fill of the surroundings
    img = cv2.inpaint(np.clip(src[:top], 0, 255).astype(np.uint8), m.astype(np.uint8) * 255, 9, cv2.INPAINT_TELEA).astype(np.float32)
    save_prev('plate_hole.png', Image.fromarray((hole * 255).astype(np.uint8)))
    part = np.asarray(lama.inpaint(Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)), m.astype(np.float32))).astype(np.float32)
    plate = sky.copy()
    plate[:top] = part
    # soften the seam into the clouds
    for y in range(HORIZON - 40, top):
        k = np.clip((y - (HORIZON - 40)) / 90, 0, 1)
        plate[y] = part[y] * (1 - k) + sky[y] * k if y < top else sky[y]
    filled = Image.fromarray(np.clip(plate, 0, 255).astype(np.uint8))
    save_prev('plate_lama.png', filled)
    big = upscale_rgb(filled, 1.5)
    big.save(os.path.join(OUT, 'bg_plate.jpg'), quality=90)
    print('bg_plate.jpg', big.size)


if __name__ == '__main__':
    what = sys.argv[1:] or ['gate', 'guardian', 'heli', 'falls', 'plate']
    gate_fg = guard = heli = None
    if 'gate' in what or 'plate' in what:
        gate_fg = build_gate() if 'gate' in what else grabcut_poly(TEMPLE, (544, 96, 764, 258), 5)
    if 'guardian' in what or 'plate' in what:
        guard = build_guardian()
    if 'heli' in what or 'plate' in what:
        heli = build_heli()
    if 'falls' in what:
        build_falls()
    if 'plate' in what:
        build_plate(gate_fg, guard, heli)
