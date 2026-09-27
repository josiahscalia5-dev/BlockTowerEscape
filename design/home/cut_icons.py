"""Cuts the pictures out of the Home screen's four menu buttons (the star badge, the scroll, the chest and the
gear) onto transparent backgrounds, for the popup headers and the Prize Vault chests. The buttons were cut from
the artwork as rectangles, so around each picture there is the button's navy plate and, in the corners, a bit
of the sky behind it: keep only the bright picture joined to the middle of the button. Run from this folder."""
import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage

SRC = '../../app/src/main/assets/home/'
for name, seed in [('daily', (0.5, 0.30)), ('missions', (0.40, 0.35)), ('vault', (0.5, 0.35)), ('settings', (0.32, 0.33))]:
    im = Image.open(SRC + 'btn_%s.png' % name).convert('RGBA')
    a = np.asarray(im, np.float32)
    h, w = a.shape[:2]
    rgb = a[..., :3]; alpha = a[..., 3]
    mx = rgb.max(axis=2); mn = rgb.min(axis=2)
    # the navy plate: dark and blue-ish
    navy = (mx < 95) | ((rgb[..., 2] > rgb[..., 0] + 25) & (mx < 125) & (mx - mn < 90))
    pic = (~navy) & (alpha > 128)
    pic[int(h * 0.64):] = False                     # the caption lives below the picture
    pic = ndimage.binary_opening(pic, iterations=1)
    lab, n = ndimage.label(pic)
    sy, sx = int(h * seed[1]), int(w * seed[0])
    keep = lab == lab[sy, sx]
    if lab[sy, sx] == 0:
        raise SystemExit('seed missed the picture in ' + name)
    keep = ndimage.binary_fill_holes(keep)
    keep = ndimage.binary_closing(keep, iterations=2)
    keep = ndimage.binary_fill_holes(keep)
    m = Image.fromarray((keep * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.8))
    out = im.copy(); out.putalpha(m)
    bb = m.getbbox()
    out = out.crop(bb)
    out.save(SRC + 'icon_%s.png' % name)
    print(name, 'bbox', bb, '->', out.size)
