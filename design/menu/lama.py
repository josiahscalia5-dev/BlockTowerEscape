"""LaMa (big-lama TorchScript) inpainting helper."""
import os
import numpy as np
import torch
from PIL import Image

_m = None


def inpaint(img: Image.Image, mask: np.ndarray) -> Image.Image:
    """img RGB, mask HxW float/bool (1 = fill). Works at the image's own resolution (padded to /8)."""
    global _m
    if _m is None:
        torch.set_num_threads(4)
        _m = torch.jit.load(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'models/big-lama.pt'), map_location='cpu').eval()
    a = np.asarray(img.convert('RGB')).astype(np.float32) / 255.0
    h, w = a.shape[:2]
    ph, pw = (8 - h % 8) % 8, (8 - w % 8) % 8
    a = np.pad(a, ((0, ph), (0, pw), (0, 0)), mode='reflect')
    m = (np.asarray(mask) > 0.5).astype(np.float32)
    m = np.pad(m, ((0, ph), (0, pw)), mode='constant')
    x = torch.from_numpy(a.transpose(2, 0, 1))[None]
    mm = torch.from_numpy(m)[None, None]
    with torch.no_grad():
        out = _m(x, mm)
    o = out[0].permute(1, 2, 0).numpy()
    if o.max() > 2:
        o = o / 255.0
    o = np.clip(o[:h, :w], 0, 1)
    orig = np.asarray(img.convert('RGB')).astype(np.float32) / 255.0
    mk = (np.asarray(mask) > 0.5)[..., None]
    res = np.where(mk, o, orig)
    return Image.fromarray((res * 255 + 0.5).astype(np.uint8))
