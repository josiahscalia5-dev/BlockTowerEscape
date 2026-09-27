import cv2, numpy as np
from PIL import Image
rng=np.random.default_rng(5)
ref=np.asarray(Image.open('../level5_reference.png').convert('RGB'),np.float32)
H,W=ref.shape[:2]
mask=cv2.imread('plate_mask.png',0).astype(np.float32)/255
mask=cv2.dilate(mask,np.ones((5,5),np.uint8))
mask[88:880,826:]=1.0                     # slivers between the tool buttons
mask[1180:,0:120]=np.maximum(mask[1180:,0:120],0)
ML,MT,MB=74,500,110
PW,PH=W+2*ML,H+MT+MB
def grad(stops):
    out=np.zeros((PH,3),np.float32); y=np.arange(PH)
    for c in range(3): out[:,c]=np.interp(y,[s[0]+MT for s in stops],[s[1][c] for s in stops])
    return np.repeat(out[:,None,:],PW,axis=1)
canvas=grad([(-MT,(20,78,190)),(-200,(40,120,215)),(60,(95,170,235)),(250,(150,205,245)),(520,(188,224,246)),(850,(200,230,246)),(1050,(170,208,222)),(1250,(95,140,110)),(1500,(55,95,45)),(H+MB,(30,60,28))])
def put(dst, src, alpha, x0, y0):
    h,w=src.shape[:2]
    xa,ya=max(0,x0),max(0,y0); xb,yb=min(dst.shape[1],x0+w),min(dst.shape[0],y0+h)
    if xa>=xb or ya>=yb: return
    a=alpha[ya-y0:yb-y0,xa-x0:xb-x0][...,None]
    dst[ya:yb,xa:xb]=dst[ya:yb,xa:xb]*(1-a)+src[ya-y0:yb-y0,xa-x0:xb-x0]*a
def haze(rgb, k, col=(196,226,246)): return rgb*(1-k)+np.array(col,np.float32)*k
# soft clouds high in the sky
def clouds(img, n, y0, y1, smin, smax, a):
    layer=np.zeros((PH,PW),np.float32)
    for i in range(n):
        cx=rng.uniform(0,PW); cy=rng.uniform(y0,y1)+MT; s=rng.uniform(smin,smax)
        for k in range(8):
            cv2.circle(layer,(int(cx+rng.normal(0,s*0.9)),int(cy+rng.normal(0,s*0.22))),int(s*rng.uniform(0.35,0.75)),1.0,-1)
    layer=cv2.GaussianBlur(layer,(0,0),8)
    sh=cv2.GaussianBlur(np.roll(layer,7,axis=0),(0,0),5)
    m=np.clip(layer*a,0,1)[...,None]
    c=np.array([252,253,255],np.float32)*(1-0.25*np.clip(sh-layer*0.6,0,1)[...,None])
    return img*(1-m)+c*m
canvas=clouds(canvas,30,-MT+30,300,26,62,0.9)
canvas=clouds(canvas,12,380,900,30,70,0.35)
# the valley's far wall on the right: the design's left cliffs, mirrored and hazed
kept=1-mask
strip=ref[330:1330,0:250][:, ::-1]; ka=kept[330:1330,0:250][:, ::-1]
ka=cv2.GaussianBlur(ka,(0,0),2)
put(canvas, haze(strip*0.95,0.28), ka*0.97, ML+W-250, MT+330)
# distant floating islands (the island from the top left, hazed)
isl=np.asarray(Image.open('float_island_try.png'),np.float32)
def keysky(rgba):
    rgb=rgba[...,:3]; a=rgba[...,3]/255
    r,g,b=rgb[...,0],rgb[...,1],rgb[...,2]
    s=np.clip(((b-r)-40)/60,0,1)*np.clip((b-g+10)/40,0,1)*np.clip((b-150)/60,0,1)
    return a*(1-s)
ia=keysky(isl)
for (cx,cy,sc,hz) in [(460,380,0.6,0.5),(640,470,0.55,0.55),(330,560,0.45,0.62),(560,700,0.42,0.66),(420,860,0.4,0.7),(700,880,0.5,0.6),(-20,-160,0.8,0.3),(820,-260,0.7,0.35),(430,-330,0.55,0.45),(250,40,0.5,0.35)]:
    nw,nh=int(isl.shape[1]*sc),int(isl.shape[0]*sc)
    rgb=cv2.resize(isl[...,:3],(nw,nh),interpolation=cv2.INTER_AREA); a=cv2.resize(ia,(nw,nh),interpolation=cv2.INTER_AREA)
    put(canvas, haze(rgb,hz), a, int(cx-nw/2)+ML, int(cy-nh/2)+MT)
# waterfalls pouring out of the cliffs into the mist
wf=ref[1040:1210,150:232]
wfa=np.clip((wf.mean(axis=2)-150)/60,0,1)
wfa=cv2.GaussianBlur(wfa,(0,0),1.5)
for (x,y,sx,sy,hz) in [(250,330,0.9,3.2,0.25),(600,320,0.9,3.4,0.25),(420,560,0.6,2.4,0.45),(520,640,0.5,2.2,0.5),(330,760,0.7,2.0,0.4),(660,620,0.7,2.6,0.35),(200,900,0.9,1.8,0.3),(560,980,0.8,1.6,0.35)]:
    nw,nh=int(wf.shape[1]*sx),int(wf.shape[0]*sy)
    rgb=cv2.resize(wf,(nw,nh)); a=cv2.resize(wfa,(nw,nh))
    a=a*np.clip(np.linspace(0,1,nh)[:,None]*5,0,1)*np.clip((1-np.linspace(0,1,nh)[:,None])*3,0,1)
    put(canvas, haze(rgb,hz), a*0.9, int(x-nw/2)+ML, y+MT)
# the design's big waterfall beside the near path (left of the yellow block)
put(canvas, wf, np.clip(wfa*1.2,0,1), ML+150, MT+1040)
# mist where the waterfalls land
mist=np.zeros((PH,PW),np.float32)
for i in range(40):
    cv2.circle(mist,(int(rng.uniform(150,750))+ML,int(rng.uniform(820,1150))+MT),int(rng.uniform(30,70)),1.0,-1)
mist=cv2.GaussianBlur(mist,(0,0),22)
m=np.clip(mist*0.55,0,1)[...,None]
canvas=canvas*(1-m)+np.array([236,246,252],np.float32)*m
# the jungle wall behind the near path: the design's left jungle, mirrored and tiled
jungle=ref[880:1340,0:150]
jt=np.concatenate([jungle,jungle[:, ::-1]],axis=1)
jt=np.concatenate([jt]*4,axis=1)[:, :W]
jt=cv2.resize(jt,(W,int(jt.shape[0]*1.35)))
ja=np.ones(jt.shape[:2],np.float32)
ja*=np.clip(np.linspace(0,1,jt.shape[0])[:,None]*3.5,0,1)
jt=haze(jt,0.12,(120,150,110))
put(canvas, jt, ja, ML, MT+1180)
# the design itself, with the path, the HUD and the gameplay objects taken out
mm=np.maximum(cv2.GaussianBlur(mask,(0,0),6),mask)
top=np.clip(1-np.arange(H,dtype=np.float32)/70,0,1)[:,None]
mm=np.maximum(mm,top*0.85)                # the design's top edge melts into the sky above it
mm=cv2.GaussianBlur(mm,(0,0),2.5)[...,None]
canvas[MT:MT+H,ML:ML+W]=ref*(1-mm)+canvas[MT:MT+H,ML:ML+W]*mm
# side margins: mirrored edge columns
canvas[:,0:ML]=cv2.GaussianBlur(canvas[:,ML:2*ML][:, ::-1],(0,0),3)
canvas[:,W+ML:]=cv2.GaussianBlur(canvas[:,W:W+ML][:, ::-1],(0,0),3)
out=np.clip(canvas,0,255).astype(np.uint8)
Image.fromarray(out).save('plate_v2.jpg',quality=90)
Image.fromarray(out).resize((PW//2,PH//2)).save('plate_v2_small.png')
