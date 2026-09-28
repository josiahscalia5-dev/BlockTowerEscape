import sys, subprocess, numpy as np
from PIL import Image
src, dst = sys.argv[1], sys.argv[2]
im = Image.open(src).convert('RGBA'); a = np.asarray(im).astype(np.float32) / 255
w, h = im.size
# colour: fill transparent areas with nearby colour so the upscaler has no dark fringe
from scipy import ndimage
solid = a[..., 3] > 0.5
idx = ndimage.distance_transform_edt(~solid, return_distances=False, return_indices=True)
rgb = a[..., :3][idx[0], idx[1]]
Image.fromarray((rgb * 255 + .5).astype(np.uint8)).save('hs/_rgb.png')
Image.fromarray((np.repeat(a[..., 3:4], 3, 2) * 255 + .5).astype(np.uint8)).save('hs/_a.png')
for n in ('_rgb', '_a'):
    subprocess.run(['python3', 'upscale.py', f'hs/{n}.png', f'hs/{n}4.png'], check=True, capture_output=True)
R = Image.open('hs/_rgb4.png').resize((w * 2, h * 2), Image.LANCZOS)
A = Image.open('hs/_a4.png').convert('L').resize((w * 2, h * 2), Image.LANCZOS)
out = R.convert('RGBA'); out.putalpha(A); out.save(dst, optimize=True); print(dst, out.size)
