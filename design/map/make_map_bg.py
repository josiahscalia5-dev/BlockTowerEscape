"""Builds the level map's backdrop from the sky tower plate: its artwork area, softened and hazed a little
toward the sky colour. The level blocks stand sharp in front of it, and the soft focus also hides the
patches where the design's own blocks were painted out of the plate. Run from this folder."""
from PIL import Image, ImageFilter
import numpy as np

plate = Image.open('../../app/src/main/assets/img/bg_plate.jpg').convert('RGB')
# the artwork area of the plate (see Art.bgArtX / bgArtY / artW / artH)
art = plate.crop((64, 480, 64 + 1024, 480 + 1536))
soft = art.filter(ImageFilter.GaussianBlur(4))
a = np.asarray(soft, np.float32)
# haze toward a light sky blue, a touch stronger near the top where the plate is busiest
ys = np.linspace(1.0, 0.0, a.shape[0], dtype=np.float32)[:, None, None]
haze = 0.16 + 0.10 * ys
sky = np.array([150, 200, 255], np.float32)
a = a * (1 - haze) + sky * haze
out = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)).resize((768, 1152), Image.LANCZOS)
out.save('../../app/src/main/assets/img/map_bg.jpg', quality=86)
print('wrote map_bg.jpg', out.size)
