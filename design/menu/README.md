# Splash / Home / Select Level artwork

`design/menu_reference.png` is the approved Splash, Home and Select Level reference (three phone screens side by
side); `design/home_reference.png` is the approved Home screen. The menu screens are built from their pixels. Nothing
is repainted by hand, so the boy, the Guardian and the world are the same on every screen.

| Asset | From | How |
|---|---|---|
| `assets/menu/world.jpg` | Splash panel | boy, Guardian (and its purple haze), logo and loading bar removed by inpainting; sky extended upward for tall phones; 4x upscale |
| `assets/menu/boy_run.png` | Splash panel | the boy cut out (anime segmentation), 4x upscale |
| `assets/menu/guardian.png` | Splash panel (boy removed) | segmentation mask refined with GrabCut, 4x upscale |
| `assets/menu/map.jpg` | Select Level panel | HUD, title, boy, level numbers, locks, stars and labels removed so they can be drawn live; top, bottom and side scenery extended; 4x upscale |
| `assets/menu/boy_back.png` | Select Level panel | the boy seen from behind, cut out |
| `assets/menu/sign.png` | Select Level panel | the wooden title sign with its text removed and the wood grain repainted |
| `assets/home/logo.png`, `tagline.png` | `home_reference.png` | cut out, 4x upscale |
| `assets/home/btn_*.png`, `play.png`, `hud_*.png`, `badge.png` | the earlier cut-outs (`home/build_home.py`) | 2x upscale (colour and alpha separately, `up_rgba.py`) |

Tools (Python 3 with Pillow, NumPy, OpenCV, SciPy, PyTorch, onnxruntime), all CPU:
- `upscale.py`: Real-ESRGAN x4 anime model (`RealESRGAN_x4plus_anime_6B.pth`, github.com/xinntao/Real-ESRGAN releases)
- `seg.py`: IS-Net segmentation (`isnet-anime.onnx`, `isnet-general-use.onnx`, github.com/danielgatis/rembg releases)
- `lama.py`: LaMa inpainting (`big-lama.pt`, github.com/Sanster/models releases)
- `sprite.py`: mask to clean RGBA sprite (despeckle, fill holes, feather, defringe, premultiplied resize)
- `grid.py`: draws a labelled coordinate grid over a crop (used to read off element positions)

The node positions on the level map (numbers, stars, padlocks, where the boy stands, labels) are in `MapPlate`
(`LevelMap.kt`), in the map's own units, measured with `grid.py`.

`home/build_home.py` still makes the button and top-bar cut-outs at the artwork's size; after running it, upscale
them with `up_rgba.py` (its `home_plate.jpg` is no longer used: Home is laid out from the pieces).
