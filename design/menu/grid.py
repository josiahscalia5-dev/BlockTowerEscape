import sys
from PIL import Image, ImageDraw, ImageFont
F = ImageFont.truetype('/home/user/BlockTowerEscape/app/src/main/assets/fonts/FiraSansCondensed-Bold.ttf', 15)
def grid(src, box, scale, name, step=10):
    s = Image.open(src).convert('RGB')
    c = s.crop(box).resize(((box[2]-box[0])*scale, (box[3]-box[1])*scale), Image.LANCZOS)
    d = ImageDraw.Draw(c)
    for x in range(box[0] - box[0] % step + step, box[2], step):
        X = (x - box[0]) * scale
        d.line([(X, 0), (X, c.size[1])], fill=(255, 255, 0) if x % 50 else (255, 60, 0), width=1)
        if x % 50 == 0: d.text((X + 2, 2), str(x), fill=(255, 255, 255), font=F, stroke_width=2, stroke_fill=(0, 0, 0))
    for y in range(box[1] - box[1] % step + step, box[3], step):
        Y = (y - box[1]) * scale
        d.line([(0, Y), (c.size[0], Y)], fill=(255, 255, 0) if y % 50 else (255, 60, 0), width=1)
        if y % 50 == 0: d.text((2, Y + 2), str(y), fill=(255, 255, 255), font=F, stroke_width=2, stroke_fill=(0, 0, 0))
    c.save(name)
if __name__ == '__main__':
    a = sys.argv
    grid(a[1], tuple(int(v) for v in a[2].split(',')), int(a[3]), a[4], int(a[5]) if len(a) > 5 else 10)
