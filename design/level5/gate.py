from cut import *
from showpoly import show
z=[(95,62),(130,40),(162,44),(200,62),(210,110),(232,118),(236,72),(262,62),(420,62),(520,62),(600,62),(622,40),(660,44),(690,62),(700,108),(718,118),(742,88),(772,84),(802,100),(812,140),(800,200),(795,262),(790,330),(786,420),(800,480),(792,540),(700,552),(600,548),(560,508),(470,505),(380,505),(350,548),(250,552),(120,552),(62,540),(60,480),(100,440),(106,330),(95,270),(110,200),(95,150),(90,100)]
poly=[(220+x/2,50+y/2) for x,y in z]
show(poly,(220,50,680,360),'gate_poly.png',k=2)
open('gate_poly.txt','w').write(repr(poly))
m=cut_poly(poly,band=4)
# soft top edge where the HUD timer hides the top of the central tower
ys=np.arange(img.shape[0])[:,None]
m=(m*np.clip((ys-82)/10.0,0,1)).astype(np.uint8)
save_cut(m,(262,68,628,330),'gate_try.png',feather=0.8)
