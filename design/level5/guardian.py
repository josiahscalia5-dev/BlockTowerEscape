from cut import *
poly=eval(open('gpoly.txt').read())
m=cut_poly(poly,band=4)
ys=np.arange(img.shape[0])[:,None]
m=(m*np.clip((566-ys)/26.0,0,1)).astype(np.uint8)
save_cut(m,(140,360,406,566),'guardian_try.png',feather=0.7)
