"""Real-ESRGAN x4 (anime 6B) upscaler, CPU, tiled. Usage: upscale.py in.png out.png"""
import sys
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from PIL import Image


class RDB(nn.Module):
    def __init__(self, nf=64, gc=32):
        super().__init__()
        self.conv1 = nn.Conv2d(nf, gc, 3, 1, 1)
        self.conv2 = nn.Conv2d(nf + gc, gc, 3, 1, 1)
        self.conv3 = nn.Conv2d(nf + 2 * gc, gc, 3, 1, 1)
        self.conv4 = nn.Conv2d(nf + 3 * gc, gc, 3, 1, 1)
        self.conv5 = nn.Conv2d(nf + 4 * gc, nf, 3, 1, 1)
        self.lrelu = nn.LeakyReLU(0.2, True)

    def forward(self, x):
        x1 = self.lrelu(self.conv1(x))
        x2 = self.lrelu(self.conv2(torch.cat((x, x1), 1)))
        x3 = self.lrelu(self.conv3(torch.cat((x, x1, x2), 1)))
        x4 = self.lrelu(self.conv4(torch.cat((x, x1, x2, x3), 1)))
        x5 = self.conv5(torch.cat((x, x1, x2, x3, x4), 1))
        return x5 * 0.2 + x


class RRDB(nn.Module):
    def __init__(self, nf, gc=32):
        super().__init__()
        self.rdb1 = RDB(nf, gc); self.rdb2 = RDB(nf, gc); self.rdb3 = RDB(nf, gc)

    def forward(self, x):
        return self.rdb3(self.rdb2(self.rdb1(x))) * 0.2 + x


class RRDBNet(nn.Module):
    def __init__(self, nb=6, nf=64, gc=32):
        super().__init__()
        self.conv_first = nn.Conv2d(3, nf, 3, 1, 1)
        self.body = nn.Sequential(*[RRDB(nf, gc) for _ in range(nb)])
        self.conv_body = nn.Conv2d(nf, nf, 3, 1, 1)
        self.conv_up1 = nn.Conv2d(nf, nf, 3, 1, 1)
        self.conv_up2 = nn.Conv2d(nf, nf, 3, 1, 1)
        self.conv_hr = nn.Conv2d(nf, nf, 3, 1, 1)
        self.conv_last = nn.Conv2d(nf, 3, 3, 1, 1)
        self.lrelu = nn.LeakyReLU(0.2, True)

    def forward(self, x):
        feat = self.conv_first(x)
        feat = feat + self.conv_body(self.body(feat))
        feat = self.lrelu(self.conv_up1(F.interpolate(feat, scale_factor=2, mode='nearest')))
        feat = self.lrelu(self.conv_up2(F.interpolate(feat, scale_factor=2, mode='nearest')))
        return self.conv_last(self.lrelu(self.conv_hr(feat)))


def main():
    src, dst = sys.argv[1], sys.argv[2]
    torch.set_num_threads(4)
    net = RRDBNet()
    sd = torch.load(sys.argv[3] if len(sys.argv) > 3 else 'models/RealESRGAN_x4plus_anime_6B.pth', map_location='cpu')
    sd = sd.get('params_ema', sd.get('params', sd))
    net.load_state_dict(sd, strict=True)
    net.eval()
    img = np.asarray(Image.open(src).convert('RGB')).astype(np.float32) / 255.0
    h, w, _ = img.shape
    x = torch.from_numpy(img.transpose(2, 0, 1)).unsqueeze(0)
    tile, pad = 256, 16
    out = torch.zeros(1, 3, h * 4, w * 4)
    with torch.no_grad():
        for ty in range(0, h, tile):
            for tx in range(0, w, tile):
                y0, x0 = max(ty - pad, 0), max(tx - pad, 0)
                y1, x1 = min(ty + tile + pad, h), min(tx + tile + pad, w)
                o = net(x[:, :, y0:y1, x0:x1])
                oy, ox = (ty - y0) * 4, (tx - x0) * 4
                th, tw = (min(ty + tile, h) - ty) * 4, (min(tx + tile, w) - tx) * 4
                out[:, :, ty * 4:ty * 4 + th, tx * 4:tx * 4 + tw] = o[:, :, oy:oy + th, ox:ox + tw]
                print('.', end='', flush=True)
    res = (out[0].clamp(0, 1).numpy().transpose(1, 2, 0) * 255.0 + 0.5).astype(np.uint8)
    Image.fromarray(res).save(dst)
    print('\nsaved', dst, res.shape)


if __name__ == '__main__':
    main()
