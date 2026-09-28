import cv2, numpy as np, sys
from PIL import Image
REF='../level5_reference.png'
img=cv2.imread(REF)
def grabcut(rect, fg_polys=(), bg_polys=(), pr_fg_polys=(), iters=8):
    x0,y0,x1,y1=rect
    mask=np.full(img.shape[:2], cv2.GC_BGD, np.uint8)
    mask[y0:y1,x0:x1]=cv2.GC_PR_BGD
    for p in pr_fg_polys: cv2.fillPoly(mask,[np.array(p,np.int32)],cv2.GC_PR_FGD)
    for p in fg_polys: cv2.fillPoly(mask,[np.array(p,np.int32)],cv2.GC_FGD)
    for p in bg_polys: cv2.fillPoly(mask,[np.array(p,np.int32)],cv2.GC_BGD)
    bgd=np.zeros((1,65),np.float64); fgd=np.zeros((1,65),np.float64)
    cv2.grabCut(img,mask,None,bgd,fgd,iters,cv2.GC_INIT_WITH_MASK)
    m=np.where((mask==cv2.GC_FGD)|(mask==cv2.GC_PR_FGD),255,0).astype(np.uint8)
    return m
def save_cut(m, rect, name, feather=1.2):
    x0,y0,x1,y1=rect
    a=m[y0:y1,x0:x1].astype(np.float32)
    if feather>0: a=cv2.GaussianBlur(a,(0,0),feather)
    rgb=cv2.cvtColor(img[y0:y1,x0:x1],cv2.COLOR_BGR2RGB)
    out=np.dstack([rgb,a.clip(0,255).astype(np.uint8)])
    Image.fromarray(out,'RGBA').save(name)
    # preview on magenta
    bg=np.zeros_like(rgb); bg[:]=(255,0,255)
    af=a[...,None]/255.
    prev=(rgb*af+bg*(1-af)).astype(np.uint8)
    Image.fromarray(prev).resize((prev.shape[1]*2,prev.shape[0]*2)).save(name.replace('.png','_prev.png'))

def cut_poly(poly, band=6, holes=(), fg_extra=(), iters=6):
    """GrabCut restricted to a band around a traced outline: outside = background, deep inside = foreground."""
    h,w=img.shape[:2]
    inside=np.zeros((h,w),np.uint8); cv2.fillPoly(inside,[np.array(poly,np.int32)],255)
    for hp in holes: cv2.fillPoly(inside,[np.array(hp,np.int32)],0)
    k=cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(2*band+1,2*band+1))
    outer=cv2.dilate(inside,k); inner=cv2.erode(inside,k)
    mask=np.full((h,w),cv2.GC_BGD,np.uint8)
    mask[outer>0]=cv2.GC_PR_BGD
    mask[inside>0]=cv2.GC_PR_FGD
    mask[inner>0]=cv2.GC_FGD
    for p in fg_extra: cv2.fillPoly(mask,[np.array(p,np.int32)],cv2.GC_FGD)
    bgd=np.zeros((1,65),np.float64); fgd=np.zeros((1,65),np.float64)
    cv2.grabCut(img,mask,None,bgd,fgd,iters,cv2.GC_INIT_WITH_MASK)
    m=np.where((mask==cv2.GC_FGD)|(mask==cv2.GC_PR_FGD),255,0).astype(np.uint8)
    return m
