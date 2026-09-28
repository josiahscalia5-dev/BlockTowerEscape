"""Level 7 (the enchanted sky realm) artwork, cut from the approved reference design/level7_reference.png (941 x 1672).

Nothing is painted by hand: every piece comes from the reference's own pixels.

  gate.png      the Celestial Gate: the golden crystal castle with its star portal (the level's destination). Its right
                wing is hidden under the design's Time and Target panels, so it is rebuilt as the mirror of the left
                wing (the castle is symmetric about its portal); traced outline refined with GrabCut
  sorcerer.png  the hooded Sorcerer with the glowing purple eyes and the clawed hand (he commands the minions; his
                cloak dissolves into magic mist where the design's CHASE & COLLECT panel covered it)
  gem.png       the purple gem that floats over the path (the gems to pick up)
  m_*.png       the Sorcerer's minions: little witch-hat imps, the round cat-eared orb imp and the lantern-ship rider
                (IS-Net segmentation)
  balloon.png   the purple and gold hot-air balloon
  falls.png     a strip of the reference's waterfall, made to tile vertically (the game scrolls it)
  bg_plate.jpg  (and bg_plate_hd.jpg, twice the design's size, for big screens) the sky: violet sky, pink clouds, the
                floating islands with their towers and waterfalls, and the far path of blocks up to the castle. The
                HUD, the castle, the Sorcerer, the minions, the balloon and the near course (blocks, spiked balls,
                chains, the boy, the relic, coins, gems) are taken out and the holes inpainted (LaMa), since the game
                draws all of those live.

Cut-outs are upscaled with Real-ESRGAN (colour and alpha separately) so they stay sharp on phones.

Tools and models: see design/menu/README.md (lama.py, seg.py, sprite.py, upscale.py; models in design/menu/models).
  python3 design/level7/build_l7.py [gate sorcerer minions balloon falls plate]
      writes app/src/main/assets/l7/ and previews to sim-out/l7art/
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

REF = os.path.join(ROOT, 'design', 'level7_reference.png')
OUT = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'l7')
PREV = os.path.join(ROOT, 'sim-out', 'l7art')
TMP = os.path.join(HERE, 'hs')
for d in (OUT, PREV, TMP):
    os.makedirs(d, exist_ok=True)

ref = Image.open(REF).convert('RGB')
W, H = ref.size
A = np.asarray(ref)

# ------------------------------------------------------------------ outlines (reference pixels)
# the castle is symmetric about its portal (x = 640): the left half is traced, the right half is its mirror
AXIS = 640
CASTLE_LEFT = [(506, 428), (500, 398), (470, 388), (452, 380), (452, 300), (460, 286), (463, 250), (470, 232), (478, 210), (486, 230), (492, 246), (504, 240),
               (511, 198), (520, 162), (529, 196), (537, 190), (541, 150), (547, 110), (553, 150), (557, 166), (561, 140),
               (565, 124), (570, 140), (577, 158), (583, 132), (590, 92), (597, 132), (602, 158), (614, 158), (627, 148),
               (636, 138)]
CASTLE = CASTLE_LEFT + [(2 * AXIS - x, y) for (x, y) in reversed(CASTLE_LEFT)]
# the reference's HUD panels over the castle's right wing: mirrored from the left wing from here on
MIRROR_X = 742

# the hooded Sorcerer: hood, shoulders, the clawed hand reaching toward the path (his robe fades into mist below)
SORCERER = [(226, 330), (238, 300), (262, 280), (290, 272), (312, 280), (336, 300), (352, 324), (360, 352), (382, 356),
            (404, 372), (420, 364), (440, 376), (470, 396), (500, 410), (514, 432), (518, 462), (510, 492), (492, 520),
            (470, 530), (440, 520), (420, 500), (396, 486), (372, 500), (346, 530), (320, 560), (290, 580), (250, 590),
            (220, 575), (200, 540), (192, 500), (196, 450), (206, 400), (214, 360)]

# the Sorcerer's minions (traced outlines, refined by GrabCut): too small and too glowing for IS-Net to isolate
MINIONS = {
    # witch-hat imp with the golden hat band and orange eyes, a glowing torch in each hand
    'm_hat.png': [(377, 511), (382, 525), (383, 538), (387, 548), (392, 553), (393, 560), (405, 571), (404, 577), (393, 578),
                  (385, 583), (380, 593), (368, 598), (365, 603), (361, 601), (353, 595), (340, 588), (337, 578), (327, 575),
                  (320, 561), (330, 555), (335, 542), (340, 538), (347, 543), (358, 537), (368, 523), (373, 513)],
    # the round, cat-eared orb imp with the golden staff
    'm_orb.png': [(340, 630), (351, 638), (363, 648), (366, 658), (364, 670), (369, 685), (366, 692), (358, 700), (346, 713),
                  (338, 720), (331, 713), (319, 700), (306, 690), (296, 683), (299, 678), (313, 677), (309, 663), (309, 652),
                  (306, 637), (313, 633), (323, 640), (329, 633)],
    # the orb imp riding its lantern ship with the gold cannon
    'm_ship.png': [(96, 404), (100, 418), (118, 412), (132, 422), (144, 438), (148, 456), (146, 472), (140, 484), (154, 496),
                   (160, 512), (144, 520), (126, 530), (106, 536), (90, 528), (80, 512), (76, 500), (74, 478), (72, 464),
                   (74, 446), (68, 430), (72, 422), (82, 410)],
    # (not a minion) the purple gem floating over the path, for the gems to pick up along the course
    'gem.png': [(555, 625), (560, 620), (577, 620), (582, 625), (591, 637), (571, 660), (550, 636)],
    # witch-hat imp with glowing purple eyes
    'm_hat2.png': [(30, 410), (40, 433), (50, 447), (57, 455), (55, 467), (67, 470), (67, 477), (53, 478), (48, 490), (42, 500),
                   (23, 500), (18, 487), (10, 478), (3, 470), (7, 463), (7, 448), (20, 442), (25, 425), (28, 412)],
}
BALLOON = [(207, 562), (232, 570), (242, 587), (245, 607), (238, 627), (225, 642), (218, 652), (216, 660), (200, 660),
           (196, 652), (195, 642), (175, 625), (165, 605), (165, 587), (175, 570), (190, 564)]

# the near course: blocks, spiked balls, coins, gems, the boy and the relic (the game draws all of these)
COURSE = [(0, 1250), (0, 1180), (120, 1150), (240, 1090), (300, 1000), (320, 860), (330, 760), (340, 690), (400, 640),
          (470, 612), (540, 602), (620, 612), (700, 640), (800, 690), (870, 740), (941, 760), (941, 1672), (0, 1672)]
# the reference's HUD panels over the castle's right wing: mirrored from the left wing from here on
MIRROR_X = 742

# the hooded Sorcerer: hood, shoulders, the clawed hand reaching toward the path (his robe fades into mist below)
SORCERER = [(226, 330), (238, 300), (262, 280), (290, 272), (312, 280), (336, 300), (352, 324), (360, 352), (382, 356),
            (404, 372), (420, 364), (440, 376), (470, 396), (500, 410), (514, 432), (518, 462), (510, 492), (492, 520),
            (470, 530), (440, 520), (420, 500), (396, 486), (372, 500), (346, 530), (320, 560), (290, 580), (250, 590),
            (220, 575), (200, 540), (192, 500), (196, 450), (206, 400), (214, 360)]

# the Sorcerer's minions (traced outlines, refined by GrabCut): too small and too glowing for IS-Net to isolate
MINIONS = {
    # witch-hat imp with the golden hat band and orange eyes, a glowing torch in each hand
    'm_hat.png': [(377, 511), (382, 525), (383, 538), (387, 548), (392, 553), (393, 560), (405, 571), (404, 577), (393, 578),
                  (385, 583), (380, 593), (368, 598), (365, 603), (361, 601), (353, 595), (340, 588), (337, 578), (327, 575),
                  (320, 561), (330, 555), (335, 542), (340, 538), (347, 543), (358, 537), (368, 523), (373, 513)],
    # the round, cat-eared orb imp with the golden staff
    'm_orb.png': [(340, 630), (351, 638), (363, 648), (366, 658), (364, 670), (369, 685), (366, 692), (358, 700), (346, 713),
                  (338, 720), (331, 713), (319, 700), (306, 690), (296, 683), (299, 678), (313, 677), (309, 663), (309, 652),
                  (306, 637), (313, 633), (323, 640), (329, 633)],
    # the orb imp riding its lantern ship with the gold cannon
    'm_ship.png': [(96, 404), (100, 418), (118, 412), (132, 422), (144, 438), (148, 456), (146, 472), (140, 484), (154, 496),
                   (160, 512), (144, 520), (126, 530), (106, 536), (90, 528), (80, 512), (76, 500), (74, 478), (72, 464),
                   (74, 446), (68, 430), (72, 422), (82, 410)],
    # (not a minion) the purple gem floating over the path, for the gems to pick up along the course
    'gem.png': [(555, 625), (560, 620), (577, 620), (582, 625), (591, 637), (571, 660), (550, 636)],
    # witch-hat imp with glowing purple eyes
    'm_hat2.png': [(30, 410), (40, 433), (50, 447), (57, 455), (55, 467), (67, 470), (67, 477), (53, 478), (48, 490), (42, 500),
                   (23, 500), (18, 487), (10, 478), (3, 470), (7, 463), (7, 448), (20, 442), (25, 425), (28, 412)],
}
BALLOON = [(207, 562), (232, 570), (242, 587), (245, 607), (238, 627), (225, 642), (218, 652), (216, 660), (200, 660),
           (196, 652), (195, 642), (175, 625), (165, 605), (165, 587), (175, 570), (190, 564)]

# the near course: blocks, spiked balls, coins, gems, the boy and the relic (the game draws all of these)
COURSE = [(0, 1250), (0, 1180), (120, 1150), (240, 1090), (300, 1000), (330, 900), (360, 820), (420, 740), (480, 700),
          (560, 660), (640, 640), (720, 650), (800, 700), (870, 760), (941, 800), (941, 1672), (0, 1672)]
# the reference's HUD
HUD = [(0, 0, W, 106), (0, 0, 132, 134), (124, 98, 324, 172), (6, 164, 214, 276), (6, 272, 238, 406), (742, 164, 934, 272),
       (762, 266, 934, 498), (810, 496, W, 924), (0, 1310, 240, H), (720, 1310, W, H), (80, 1490, 860, H)]
# live minions, spiked balls and chains in the sky around the Sorcerer and along the far course
SPOTS = [(170, 490, 228, 556), (14, 516, 150, 590), (72, 578, 140, 646), (396, 632, 444, 684), (436, 524, 500, 584),
         (730, 536, 836, 616), (604, 586, 700, 668), (160, 680, 310, 704), (0, 596, 70, 626), (690, 686, 820, 724),
         (580, 556, 670, 596), (214, 244, 262, 292), (340, 292, 408, 370), (700, 590, 775, 650), (0, 480, 90, 520),
         (360, 640, 430, 700)]


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


def grabcut_poly(img, poly, box, band=6):
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
    sub = np.ascontiguousarray(img[y0:y1, x0:x1][..., ::-1])
    m = np.ascontiguousarray(gc[y0:y1, x0:x1])
    bgd = np.zeros((1, 65), np.float64); fgd = np.zeros((1, 65), np.float64)
    cv2.grabCut(sub, m, None, bgd, fgd, 6, cv2.GC_INIT_WITH_MASK)
    full = np.zeros((H, W), bool)
    full[y0:y1, x0:x1] = (m == cv2.GC_FGD) | (m == cv2.GC_PR_FGD)
    return full


def mirrored_castle():
    """The reference with the castle's right wing (under the design's Time / Target panels) rebuilt as the mirror of
    its left wing, blended over a few pixels at the seam."""
    img = A.astype(np.float32).copy()
    xs = np.arange(W)
    for x in range(MIRROR_X - 6, min(W, 2 * AXIS - 440)):
        k = np.clip((x - (MIRROR_X - 6)) / 8, 0, 1)
        sx = 2 * AXIS - x
        if 0 <= sx < W:
            img[:, x] = img[:, x] * (1 - k) + A[:, sx].astype(np.float32) * k
    return np.clip(img, 0, 255).astype(np.uint8)


GATE_BOX = (446, 84, 834, 430)


def build_gate():
    img = mirrored_castle()
    x0, y0, x1, y1 = GATE_BOX
    fg = grabcut_poly(img, CASTLE, GATE_BOX, 5)
    # the traced outline wins far inside it: GrabCut may only trim the edge band (the glowing portal is bright like the sky)
    core = cv2.erode(poly_mask(CASTLE).astype(np.uint8), np.ones((15, 15), np.uint8)) > 0
    fg |= core
    # symmetric matte: the right half is the mirror of the left (the castle is symmetric)
    left = fg[:, :AXIS + 1].copy()
    for x in range(AXIS + 1, W):
        sx = 2 * AXIS - x
        if sx >= 0:
            fg[:, x] = left[:, sx]
    a = fg[y0:y1, x0:x1].astype(np.float32)
    a = cv2.GaussianBlur(a, (0, 0), 0.8)
    a = np.clip((a - 0.5) * 1.8 + 0.5, 0, 1)
    # the base of the castle fades out (the course's own plaza and stairs are in front of it)
    ys = np.arange(y0, y1, dtype=np.float32)[:, None]
    a *= np.clip((428 - ys) / 22, 0, 1)
    rgb = img[y0:y1, x0:x1].astype(np.float32)
    col = sprite.defringe(rgb, a)
    rgba = np.dstack([np.clip(col, 0, 255).astype(np.uint8), (a * 255 + .5).astype(np.uint8)])
    im = Image.fromarray(rgba, 'RGBA')
    save_prev('gate_small.png', sprite.preview(im, scale=2.0))
    # full Real-ESRGAN resolution: the castle fills much of the screen over the last stretch and at the finale
    big = upscale_rgba(im, 4.0)
    big.save(os.path.join(OUT, 'gate.png'), optimize=True)
    save_prev('gate_prev.png', sprite.preview(big, scale=0.5))
    print('gate.png', big.size, 'portal centre in the picture:', ((AXIS - x0) / (x1 - x0), (320 - y0) / (y1 - y0)))
    return fg


def seg_cut(box, name, factor, fix=None, thr=0.5, min_area=200, feather=0.9, anime=False):
    x0, y0, x1, y1 = box
    crop = ref.crop(box)
    m = seg.mask(crop) if anime else seg.mask_general(crop)
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


def build_sorcerer():
    box = (186, 262, 530, 600)
    x0, y0, x1, y1 = box
    # IS-Net finds the hood, the arm and the claw against the sky; the traced outline keeps it to him (the design's
    # HUD panel and the castle behind are not him) and fills the dark hood and robe inside it
    m = seg.mask_general(ref.crop(box))
    save_prev('sorcerer_mask.png', Image.fromarray((m * 255).astype(np.uint8)))
    inside = poly_mask(SORCERER, 4)[y0:y1, x0:x1].astype(np.float32)
    inside = cv2.GaussianBlur(inside, (0, 0), 1.5)
    core = (cv2.erode(poly_mask(SORCERER).astype(np.uint8), np.ones((31, 31), np.uint8)) > 0)[y0:y1, x0:x1].astype(np.float32)
    core = cv2.GaussianBlur(core, (0, 0), 6.0)
    a = np.maximum(np.clip((m - 0.22) / 0.4, 0, 1), core) * inside
    a = sprite.fill_holes(a > 0.5, 1500).astype(np.float32) * 0.5 + a * 0.5
    a = np.clip(cv2.GaussianBlur(a, (0, 0), 0.7), 0, 1)
    ys, xs = np.mgrid[y0:y1, x0:x1].astype(np.float32)
    # his robe dissolves into magic mist below the chest, and where the design's CHASE & COLLECT panel covered him
    a *= np.clip((592 - ys) / 70, 0, 1)
    a *= np.clip((xs - 232) / 26 + np.clip((ys - 396) / 30, 0, 1), 0, 1)
    rgb = A[y0:y1, x0:x1].astype(np.float32)
    col = sprite.defringe(rgb, a)
    im = Image.fromarray(np.dstack([np.clip(col, 0, 255).astype(np.uint8), (a * 255 + .5).astype(np.uint8)]), 'RGBA')
    big = upscale_rgba(im, 2.0)
    big.save(os.path.join(OUT, 'sorcerer.png'), optimize=True)
    save_prev('sorcerer_prev.png', sprite.preview(big, scale=1.0))
    print('sorcerer.png', big.size, 'box', box)
    full = np.zeros((H, W), bool)
    full[y0:y1, x0:x1] = a > 0.05
    return full


def build_minions():
    masks = []
    for name, poly in MINIONS.items():
        xs = [p[0] for p in poly]; ys = [p[1] for p in poly]
        box = (max(0, min(xs) - 10), max(0, min(ys) - 10), min(W, max(xs) + 10), min(H, max(ys) + 10))
        x0, y0, x1, y1 = box
        fg = grabcut_poly(A, poly, box, 3)
        core = cv2.erode(poly_mask(poly).astype(np.uint8), np.ones((7, 7), np.uint8)) > 0
        fg = sprite.fill_holes(fg | core, 400)
        a = cv2.GaussianBlur(fg.astype(np.float32), (0, 0), 0.7)
        a = np.clip((a - 0.5) * 1.8 + 0.5, 0, 1)[y0:y1, x0:x1]
        rgb = A[y0:y1, x0:x1].astype(np.float32)
        col = sprite.defringe(rgb, a, 3)
        im = Image.fromarray(np.dstack([np.clip(col, 0, 255).astype(np.uint8), (a * 255 + .5).astype(np.uint8)]), 'RGBA')
        big = upscale_rgba(im, 4.0)
        big.save(os.path.join(OUT, name), optimize=True)
        save_prev(name.replace('.png', '_prev.png'), sprite.preview(big, scale=1.0))
        print(name, big.size, 'box', box)
        full = np.zeros((H, W), bool)
        full[y0:y1, x0:x1] = a > 0.05
        masks.append(full)
    return masks


def build_balloon():
    xs = [p[0] for p in BALLOON]; ys = [p[1] for p in BALLOON]
    box = (min(xs) - 8, min(ys) - 8, max(xs) + 8, max(ys) + 8)
    x0, y0, x1, y1 = box
    fg = grabcut_poly(A, BALLOON, box, 3)
    fg = sprite.fill_holes(fg | (cv2.erode(poly_mask(BALLOON).astype(np.uint8), np.ones((7, 7), np.uint8)) > 0), 800)
    a = cv2.GaussianBlur(fg.astype(np.float32), (0, 0), 0.7)
    a = np.clip((a - 0.5) * 1.8 + 0.5, 0, 1)[y0:y1, x0:x1]
    col = sprite.defringe(A[y0:y1, x0:x1].astype(np.float32), a, 3)
    im = Image.fromarray(np.dstack([np.clip(col, 0, 255).astype(np.uint8), (a * 255 + .5).astype(np.uint8)]), 'RGBA')
    big = upscale_rgba(im, 3.0)
    big.save(os.path.join(OUT, 'balloon.png'), optimize=True)
    save_prev('balloon_prev.png', sprite.preview(big, scale=1.0))
    print('balloon.png', big.size, 'box', box)
    full = np.zeros((H, W), bool)
    full[y0:y1, x0:x1] = a > 0.05
    return full


def build_falls():
    # the waterfall pouring off the left island: a vertical strip, cross-faded onto itself so it tiles
    x0, x1, y0, y1 = FALLS
    strip = A[y0:y1, x0:x1].astype(np.float32)
    h = strip.shape[0]
    ov = 20
    body = strip[:h - ov].copy()
    for i in range(ov):
        k = i / ov
        body[i] = strip[h - ov + i] * (1 - k) + strip[i] * k
    im = Image.fromarray(np.clip(body, 0, 255).astype(np.uint8))
    big = upscale_rgb(im, 4.0)
    big.save(os.path.join(OUT, 'falls.png'), optimize=True)
    save_prev('falls_prev.png', big)
    print('falls.png', big.size)


FALLS = (33, 67, 1046, 1140)
HORIZON = 760  # below this row the plate is sky and clouds (the game's 3D cloud sea covers it in play)


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
    """The sky and clouds under the islands, in the reference's own colours: violet high up, warm pink and lavender
    cloud banks lower down, lit from above."""
    ys = np.arange(H, dtype=np.float32)[:, None]
    sky_hi = np.array([168, 132, 214], np.float32); sky_lo = np.array([236, 196, 232], np.float32)
    k = np.clip((ys - 500) / 420, 0, 1)[..., None]
    base = sky_hi * (1 - k) + sky_lo * k
    base = np.broadcast_to(base, (H, W, 3)).copy()
    n = fbm(W, H, 90, 34, 7)
    under = fbm(W, H, 90, 34, 7)[np.clip(np.arange(H) - 9, 0, H - 1)]
    bank = np.clip((n - 0.42) / 0.26, 0, 1)
    shade = np.clip((under - n) * 3, 0, 1)
    cloud = np.array([255, 238, 246], np.float32); lav = np.array([222, 188, 236], np.float32)
    dens = np.clip((ys - 600) / 240, 0.25, 1)[..., None]
    c = cloud * (1 - shade[..., None] * 0.6) + lav * (shade[..., None] * 0.6)
    out = base * (1 - bank[..., None] * dens) + c * (bank[..., None] * dens)
    return out


def build_plate(gate_fg, sorc, minions, balloon):
    hole = (cv2.dilate(rect_mask(HUD).astype(np.uint8), np.ones((13, 13), np.uint8)) > 0) | poly_mask(COURSE) | rect_mask(SPOTS)
    hole |= cv2.dilate(gate_fg.astype(np.uint8), np.ones((9, 9), np.uint8)) > 0
    hole |= rect_mask([(626, 0, 656, 150)])  # the portal's beam of light (drawn by the game)
    hole |= cv2.dilate(sorc.astype(np.uint8), np.ones((15, 15), np.uint8)) > 0
    for m in minions:
        hole |= cv2.dilate(m.astype(np.uint8), np.ones((11, 11), np.uint8)) > 0
    hole |= cv2.dilate(balloon.astype(np.uint8), np.ones((11, 11), np.uint8)) > 0
    sky = cloud_sky()
    # below the horizon the plate is the cloud sky: LaMa then only has to join the islands to it
    src = A.astype(np.float32).copy()
    src[HORIZON:] = sky[HORIZON:]
    hole[HORIZON:] = False
    top = HORIZON + 60
    m = hole[:top]
    base = np.clip(src[:top], 0, 255).astype(np.uint8)
    save_prev('plate_hole.png', Image.fromarray((hole * 255).astype(np.uint8)))
    # two scales: the big holes (the design's HUD panels, the Sorcerer and his minions) are first filled at half size,
    # where they are small enough for LaMa to continue the sky, clouds and islands coherently; that fill is then the
    # start for the full-size pass, which puts back the fine detail
    hh, ww = base.shape[:2]
    small = cv2.resize(base, (ww // 2, hh // 2), interpolation=cv2.INTER_AREA)
    ms = cv2.resize(m.astype(np.uint8), (ww // 2, hh // 2), interpolation=cv2.INTER_NEAREST) > 0
    ms = cv2.dilate(ms.astype(np.uint8), np.ones((3, 3), np.uint8)) > 0
    small = cv2.inpaint(small, ms.astype(np.uint8) * 255, 5, cv2.INPAINT_TELEA)
    low = np.asarray(lama.inpaint(Image.fromarray(small), ms.astype(np.float32))).astype(np.float32)
    save_prev('plate_lama_half.png', Image.fromarray(np.clip(low, 0, 255).astype(np.uint8)))
    up = cv2.resize(low, (ww, hh), interpolation=cv2.INTER_CUBIC)
    img = np.where(m[..., None], up, base.astype(np.float32))
    part = np.asarray(lama.inpaint(Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)), m.astype(np.float32))).astype(np.float32)
    # inside the biggest holes keep the coherent half-size fill (sharpened a little) under the full-size one's detail
    far = cv2.distanceTransform(m.astype(np.uint8), cv2.DIST_L2, 5)
    k = np.clip((far - 12) / 30, 0, 1)[..., None] * 0.6
    sharp = np.clip(up * 1.5 - cv2.GaussianBlur(up, (0, 0), 2.0) * 0.5, 0, 255)
    part = part * (1 - k) + sharp * k
    plate = sky.copy()
    plate[:top] = part
    for y in range(HORIZON - 40, top):
        k = np.clip((y - (HORIZON - 40)) / 90, 0, 1)
        plate[y] = part[y] * (1 - k) + sky[y] * k
    filled = Image.fromarray(np.clip(plate, 0, 255).astype(np.uint8))
    save_prev('plate_lama.png', filled)
    big = upscale_rgb(filled, 1.5)
    big.save(os.path.join(OUT, 'bg_plate.jpg'), quality=90)
    w, h = filled.size
    Image.open(os.path.join(TMP, '_p4.png')).resize((w * 2, h * 2), Image.LANCZOS).save(os.path.join(OUT, 'bg_plate_hd.jpg'), quality=92, optimize=True)
    print('bg_plate.jpg', big.size)


if __name__ == '__main__':
    what = sys.argv[1:] or ['gate', 'sorcerer', 'minions', 'balloon', 'falls', 'plate']
    plate = 'plate' in what
    gate_fg = build_gate() if ('gate' in what or plate) else None
    sorc = build_sorcerer() if ('sorcerer' in what or plate) else None
    mins = build_minions() if ('minions' in what or plate) else None
    bal = build_balloon() if ('balloon' in what or plate) else None
    if 'falls' in what:
        build_falls()
    if plate:
        build_plate(gate_fg, sorc, mins, bal)
