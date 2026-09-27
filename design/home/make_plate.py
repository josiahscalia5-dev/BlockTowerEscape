"""Builds the Home screen plate: the approved artwork with sky added above, a shadowed foreground below
and soft mirrored sides, so the artwork itself is never cropped or stretched on any phone shape.
Also crops the menu icons used as headers in the popups. Run from this folder."""
import cv2, numpy as np
from PIL import Image
OUT='../../app/src/main/assets/home/'
ref=np.asarray(Image.open('../home_reference.png').convert('RGB'),np.float32)
H,W=ref.shape[:2]
T,B,S=560,360,200
PW,PH=W+2*S,H+T+B
can=np.zeros((PH,PW,3),np.float32)
can[T:T+H,S:S+W]=ref
# ---- top: the artwork's own sky colours carried upward, deepening, with soft rays from the crown
edge=cv2.GaussianBlur(ref[0:10].mean(axis=0,keepdims=True),(0,0),sigmaX=40,sigmaY=0.1)[0]   # W x 3
ys=np.arange(T,dtype=np.float32)[:,None]
k=((T-ys)/T)[...,None]                     # 0 at the artwork edge -> 1 at the very top
deep=np.array([20,62,176],np.float32)
topx=edge[None,:,:]*(1-0.85*k**0.9)+deep*0.85*k**0.9
cx,cy=W/2,T+40
yy,xx=np.mgrid[0:T,0:W].astype(np.float32)
ang=np.arctan2(yy-cy,xx-cx)
rays=(0.5+0.5*np.sin(ang*16))**5*np.clip(1-np.hypot(xx-cx,yy-cy)/950,0,1)**1.5
topx+=rays[...,None]*np.array([80,110,130],np.float32)*0.45
# soft clouds: gaussian puffs near both edges
cl=np.zeros((T,W),np.float32)
rng=np.random.default_rng(3)
for (x,y,r) in [(40,470,70),(110,500,55),(0,420,60),(960,480,65),(1010,420,55),(900,510,45),(170,300,40),(860,250,45)]:
    for i in range(6):
        cv2.circle(cl,(int(x+rng.normal(0,r*0.6)),int(y+rng.normal(0,r*0.25))),int(r*rng.uniform(0.5,0.9)),1.0,-1)
cl=cv2.GaussianBlur(cl,(0,0),16)[...,None]*0.8
topx=topx*(1-cl)+np.array([240,246,255],np.float32)*cl
can[0:T,S:S+W]=topx
fe=40
for i in range(fe):
    u=(i/fe)**1.2; can[T+i,S:S+W]=topx[-1]*(1-u)+ref[i]*u
# ---- bottom: the ground's colours carried downward into shadow
bedge=cv2.GaussianBlur(ref[H-12:H].mean(axis=0,keepdims=True),(0,0),sigmaX=18,sigmaY=0.1)[0]
yb_=np.arange(B,dtype=np.float32)[:,None]
kk=(yb_/B)[...,None]
navy=np.array([8,14,40],np.float32)
botx=bedge[None,:,:]*(1-0.9*kk**0.6)+navy*0.9*kk**0.6
noise=cv2.GaussianBlur(rng.random((B,W)).astype(np.float32),(0,0),6)[...,None]
botx*=0.9+0.2*noise
can[T+H:,S:S+W]=botx
fe=20
for i in range(fe):
    u=(i/fe); can[T+H-fe+i,S:S+W]=ref[H-fe+i]*(1-u)+botx[0]*u
# ---- sides: mirrored, blurred, darkened
can[:,0:S]=cv2.GaussianBlur(can[:,S:2*S][:,::-1],(0,0),14)*0.72
can[:,S+W:]=cv2.GaussianBlur(can[:,W:W+S][:,::-1],(0,0),14)*0.72
Image.fromarray(np.clip(can,0,255).astype(np.uint8)).save(OUT+'home_plate.jpg',quality=88)
print('plate',PW,PH)
# ---- icons for popup headers (cropped from the menu buttons)
refimg=Image.open('../home_reference.png').convert('RGBA')
def icon(box,name):
    Image.open(OUT+name[0]).convert('RGBA').crop(box).save(OUT+name[1])
