"""Turn an RGB crop + mask into a clean RGBA sprite: despeckle, feather, defringe, trim, resize."""
import numpy as np
import cv2
from PIL import Image


def largest_components(binary: np.ndarray, min_area: int) -> np.ndarray:
    n, lab, stats, _ = cv2.connectedComponentsWithStats(binary.astype(np.uint8), 8)
    keep = np.zeros_like(binary, dtype=bool)
    for i in range(1, n):
        if stats[i, cv2.CC_STAT_AREA] >= min_area:
            keep |= lab == i
    return keep


def fill_holes(binary: np.ndarray, max_hole: int) -> np.ndarray:
    inv = (~binary).astype(np.uint8)
    n, lab, stats, _ = cv2.connectedComponentsWithStats(inv, 4)
    out = binary.copy()
    h, w = binary.shape
    for i in range(1, n):
        x, y, bw, bh, area = stats[i]
        touches = x == 0 or y == 0 or x + bw >= w or y + bh >= h
        if not touches and area <= max_hole:
            out |= lab == i
    return out


def defringe(rgb: np.ndarray, alpha: np.ndarray, band: int = 6) -> np.ndarray:
    """Replace colours of semi-transparent edge pixels with colours pulled from the solid interior."""
    solid = (alpha > 0.97).astype(np.float32)
    k = band * 2 + 1
    num = cv2.GaussianBlur(rgb.astype(np.float32) * solid[..., None], (k, k), band / 2)
    den = cv2.GaussianBlur(solid, (k, k), band / 2)[..., None]
    interior = np.where(den > 1e-3, num / np.maximum(den, 1e-3), rgb)
    w = np.clip((0.97 - alpha) / 0.5, 0, 1)[..., None]
    return (rgb * (1 - w) + interior * w).astype(np.float32)


def make(rgb: np.ndarray, soft: np.ndarray, thr=0.5, min_area=400, max_hole=2000, feather=1.2,
         erode=0, pad=4, keep_edges=False) -> Image.Image:
    """rgb HxWx3 uint8, soft HxW float [0,1]. Returns trimmed RGBA PIL image."""
    b = soft > thr
    b = largest_components(b, min_area)
    b = fill_holes(b, max_hole)
    if erode > 0:
        b = cv2.erode(b.astype(np.uint8), np.ones((erode * 2 + 1, erode * 2 + 1), np.uint8)) > 0
    a = b.astype(np.float32)
    if feather > 0:
        a = cv2.GaussianBlur(a, (0, 0), feather)
        a = np.clip((a - 0.5) * 1.6 + 0.5, 0, 1)  # keep edges crisp but anti-aliased
    col = defringe(rgb.astype(np.float32), a)
    out = np.dstack([np.clip(col, 0, 255).astype(np.uint8), (a * 255 + 0.5).astype(np.uint8)])
    ys, xs = np.where(a > 0.01)
    y0, y1 = max(ys.min() - pad, 0), min(ys.max() + pad + 1, a.shape[0])
    x0, x1 = max(xs.min() - pad, 0), min(xs.max() + pad + 1, a.shape[1])
    if keep_edges:
        pass
    return Image.fromarray(out[y0:y1, x0:x1], 'RGBA'), (x0, y0)


def resize_rgba(im: Image.Image, width: int) -> Image.Image:
    """Premultiplied-alpha resize (no dark halos)."""
    a = np.asarray(im).astype(np.float32) / 255.0
    pm = a.copy()
    pm[..., :3] *= pm[..., 3:4]
    h = round(im.size[1] * width / im.size[0])
    r = cv2.resize(pm, (width, h), interpolation=cv2.INTER_AREA)
    al = r[..., 3:4]
    rgb = np.where(al > 1e-4, r[..., :3] / np.maximum(al, 1e-4), 0)
    out = np.concatenate([np.clip(rgb, 0, 1), np.clip(al, 0, 1)], axis=2)
    return Image.fromarray((out * 255 + 0.5).astype(np.uint8), 'RGBA')


def preview(im: Image.Image, color=(255, 0, 255), scale=0.5) -> Image.Image:
    bg = Image.new('RGB', im.size, color)
    bg.paste(im, (0, 0), im)
    return bg.resize((int(im.size[0] * scale), int(im.size[1] * scale)), Image.LANCZOS)
