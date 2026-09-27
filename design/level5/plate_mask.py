import cv2, numpy as np
from PIL import Image
REF='../level5_reference.png'
img=cv2.imread(REF)
H,W=img.shape[:2]
mask=np.zeros((H,W),np.uint8)
def rr(x0,y0,x1,y1,r=14):
    cv2.rectangle(mask,(x0,y0),(x1,y1),255,-1)
def poly(p): cv2.fillPoly(mask,[np.array(p,np.int32)],255)
# HUD
rr(8,8,100,110); rr(98,10,276,86); rr(320,8,530,90); rr(540,10,790,86); rr(776,6,850,88)
rr(668,92,842,336)
rr(704,356,836,860)
rr(14,1510,278,1770); rr(606,1512,834,1740)
# the portal temple (drawn as its own sprite)
gp=eval(open('gate_poly.txt').read())
poly([(x,y) for x,y in gp])
# the path, the boy and every gameplay object near it
poly([(236,296),(664,296),(690,330),(700,440),(560,430),(555,560),(700,560),(712,700),(640,720),(650,830),(852,830),(852,1010),(800,1030),(815,1140),(780,1180),(730,1290),(852,1330),(852,1846),(0,1846),(0,1500),(40,1360),(110,1180),(150,1040),(170,900),(250,870),(270,780),(150,775),(140,660),(250,650),(300,600),(230,590),(130,575),(135,470),(160,400),(230,360)])
rr(182,792,280,892)            # the green ? block floating on the left
poly([(596,430),(706,430),(712,870),(596,870)])   # the island and the rope holding the log
cv2.imwrite('plate_mask.png',mask)
prev=img.copy(); prev[mask>0]=(prev[mask>0]*0.3+np.array([255,0,255])*0.7).astype(np.uint8)
cv2.imwrite('plate_mask_prev.png',cv2.resize(prev,(W//2,H//2)))

kept=img.copy(); kept[mask>0]=(255,0,255)
cv2.imwrite('plate_kept.png',cv2.resize(kept,(W//2,H//2)))
