"""isnet-anime segmentation helper (same pre/post-processing as rembg's DisSession)."""
import numpy as np
import onnxruntime as ort
from PIL import Image

_sess = None


def mask(img: Image.Image, size=1024) -> np.ndarray:
    """Returns a float32 [0,1] mask with the image's size."""
    global _sess
    if _sess is None:
        import os
        here = os.path.dirname(os.path.abspath(__file__))
        _sess = ort.InferenceSession(os.path.join(here, 'models/isnet-anime.onnx'), providers=['CPUExecutionProvider'])
    im = img.convert('RGB').resize((size, size), Image.LANCZOS)
    a = np.asarray(im).astype(np.float32)
    a = a / max(a.max(), 1e-6)
    mean = (0.485, 0.456, 0.406)
    for c in range(3):
        a[:, :, c] = (a[:, :, c] - mean[c]) / 1.0
    x = a.transpose(2, 0, 1)[None].astype(np.float32)
    out = _sess.run(None, {_sess.get_inputs()[0].name: x})[0][0, 0]
    out = (out - out.min()) / max(out.max() - out.min(), 1e-6)
    m = Image.fromarray((out * 255).astype(np.uint8)).resize(img.size, Image.LANCZOS)
    return np.asarray(m).astype(np.float32) / 255.0


_gen = None


def mask_general(img: Image.Image, size=1024) -> np.ndarray:
    global _gen
    if _gen is None:
        import os
        here = os.path.dirname(os.path.abspath(__file__))
        _gen = ort.InferenceSession(os.path.join(here, 'models/isnet-general-use.onnx'), providers=['CPUExecutionProvider'])
    im = img.convert('RGB').resize((size, size), Image.LANCZOS)
    a = np.asarray(im).astype(np.float32)
    a = a / max(a.max(), 1e-6)
    mean = (0.485, 0.456, 0.406)
    for c in range(3):
        a[:, :, c] = (a[:, :, c] - mean[c]) / 1.0
    x = a.transpose(2, 0, 1)[None].astype(np.float32)
    out = _gen.run(None, {_gen.get_inputs()[0].name: x})[0][0, 0]
    out = (out - out.min()) / max(out.max() - out.min(), 1e-6)
    m = Image.fromarray((out * 255).astype(np.uint8)).resize(img.size, Image.LANCZOS)
    return np.asarray(m).astype(np.float32) / 255.0
