import sys, json
from PIL import Image, ImageDraw
im=Image.open('../level5_reference.png').convert('RGB')
def show(poly, box, name, k=3, extra=()):
    x0,y0,x1,y1=box
    cr=im.crop(box).resize(((x1-x0)*k,(y1-y0)*k))
    d=ImageDraw.Draw(cr)
    for x in range((x0//10+1)*10,x1,10): d.line([((x-x0)*k,0),((x-x0)*k,cr.height)],fill=(90,0,90),width=1)
    for y in range((y0//10+1)*10,y1,10): d.line([(0,(y-y0)*k),(cr.width,(y-y0)*k)],fill=(90,0,90),width=1)
    for x in range((x0//50+1)*50,x1,50): d.text(((x-x0)*k+2,2),str(x),fill=(255,255,0))
    for y in range((y0//50+1)*50,y1,50): d.text((2,(y-y0)*k+2),str(y),fill=(255,255,0))
    for p in [poly]+list(extra):
        pts=[((x-x0)*k,(y-y0)*k) for x,y in p]
        d.line(pts+[pts[0]],fill=(0,255,255),width=2)
        for i,(x,y) in enumerate(pts): d.ellipse((x-3,y-3,x+3,y+3),fill=(255,0,0))
    cr.save(name)
