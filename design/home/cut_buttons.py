import cv2, numpy as np
from PIL import Image
OUT='../../app/src/main/assets/home/'
REF='../home_reference.png'
img=cv2.imread(REF)
H,W=img.shape[:2]
def rrect_poly(x0,y0,x1,y1,r,n=8):
    pts=[]
    for cx,cy,a0 in [(x1-r,y0+r,-90),(x1-r,y1-r,0),(x0+r,y1-r,90),(x0+r,y0+r,180)]:
        for i in range(n+1):
            a=np.radians(a0+90*i/n); pts.append((cx+r*np.cos(a),cy+r*np.sin(a)))
    return pts
def cut(poly, box, name, band=4, extra_fg=()):
    inside=np.zeros((H,W),np.uint8); cv2.fillPoly(inside,[np.array(poly,np.int32)],255)
    for p in extra_fg: cv2.fillPoly(inside,[np.array(p,np.int32)],255)
    k=cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(2*band+1,2*band+1))
    outer=cv2.dilate(inside,k); inner=cv2.erode(inside,k)
    mask=np.full((H,W),cv2.GC_BGD,np.uint8); mask[outer>0]=cv2.GC_PR_BGD; mask[inside>0]=cv2.GC_PR_FGD; mask[inner>0]=cv2.GC_FGD
    bg=np.zeros((1,65),np.float64); fg=np.zeros((1,65),np.float64)
    cv2.grabCut(img,mask,None,bg,fg,6,cv2.GC_INIT_WITH_MASK)
    m=np.where((mask==1)|(mask==3),255,0).astype(np.float32)
    m=cv2.GaussianBlur(m,(0,0),0.7)
    x0,y0,x1,y1=box
    rgb=cv2.cvtColor(img[y0:y1,x0:x1],cv2.COLOR_BGR2RGB)
    out=np.dstack([rgb,m[y0:y1,x0:x1].clip(0,255).astype(np.uint8)])
    Image.fromarray(out,'RGBA').save(name)
    prev=(rgb*(m[y0:y1,x0:x1,None]/255)+np.array([255,0,255])*(1-m[y0:y1,x0:x1,None]/255)).astype(np.uint8)
    return prev
prevs=[]
# PLAY
prevs.append(cut(rrect_poly(261,1263,757,1437,62),(250,1252,768,1448),OUT+'play.png'))
# menu buttons: dark rounded panels; the daily star pokes above its panel
star=[(58,540),(80,528),(103,516),(125,528),(150,540),(160,570),(140,600),(70,600),(48,570)]
prevs.append(cut(rrect_poly(35,528,172,689,20),(26,508,182,698),OUT+'btn_daily.png',extra_fg=[star]))
prevs.append(cut(rrect_poly(21,706,176,869,20),(12,696,186,878),OUT+'btn_missions.png'))
prevs.append(cut(rrect_poly(21,889,176,1048,20),(12,878,186,1058),OUT+'btn_vault.png'))
prevs.append(cut(rrect_poly(22,1067,173,1226,20),(12,1057,183,1236),OUT+'btn_settings.png'))
hh=max(p.shape[0] for p in prevs); ww=sum(p.shape[1] for p in prevs)+40
s=np.zeros((hh,ww,3),np.uint8); x=0
for p in prevs: s[:p.shape[0],x:x+p.shape[1]]=p; x+=p.shape[1]+10
pass
