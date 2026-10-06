#!/usr/bin/env python3
"""Writes exif_1.jpg .. exif_8.jpg: the SAME upright picture (red / green / blue / yellow quadrants, 120x80),
stored the way a phone would for each EXIF orientation. A correct reader shows all eight identically.
Verified here against Pillow's own exif_transpose before being trusted."""
import os
from PIL import Image, ImageOps

HERE = os.path.dirname(os.path.abspath(__file__))
W, H = 120, 80
up = Image.new("RGB", (W, H))
for x in range(W):
    for y in range(H):
        left, top = x < W // 2, y < H // 2
        up.putpixel((x, y), (230, 30, 30) if left and top else (30, 200, 60) if (not left) and top else (30, 60, 230) if left else (240, 220, 30))

# orientation -> op that turns STORED pixels into upright (Pillow's table); we store the INVERSE
UNDO = {1: None, 2: Image.FLIP_LEFT_RIGHT, 3: Image.ROTATE_180, 4: Image.FLIP_TOP_BOTTOM, 5: Image.TRANSPOSE,
        6: Image.ROTATE_270, 7: Image.TRANSVERSE, 8: Image.ROTATE_90}
INVERSE = {None: None, Image.FLIP_LEFT_RIGHT: Image.FLIP_LEFT_RIGHT, Image.ROTATE_180: Image.ROTATE_180,
           Image.FLIP_TOP_BOTTOM: Image.FLIP_TOP_BOTTOM, Image.TRANSPOSE: Image.TRANSPOSE,
           Image.ROTATE_270: Image.ROTATE_90, Image.TRANSVERSE: Image.TRANSVERSE, Image.ROTATE_90: Image.ROTATE_270}

def close(a, b): return all(abs(p - q) < 40 for p, q in zip(a, b))
for o in range(1, 9):
    inv = INVERSE[UNDO[o]]
    stored = up if inv is None else up.transpose(inv)
    exif = Image.Exif(); exif[0x0112] = o
    path = os.path.join(HERE, f"exif_{o}.jpg")
    stored.save(path, "JPEG", quality=95, subsampling=0, exif=exif.tobytes())
    # self-check with Pillow
    back = ImageOps.exif_transpose(Image.open(path)).convert("RGB")
    assert back.size == (W, H), (o, back.size)
    for (x, y), want in [((15, 10), (230, 30, 30)), ((105, 10), (30, 200, 60)), ((15, 70), (30, 60, 230)), ((105, 70), (240, 220, 30))]:
        assert close(back.getpixel((x, y)), want), (o, (x, y), back.getpixel((x, y)), want)
    print(f"exif_{o}.jpg stored {stored.size} orientation={o}  [verified with Pillow]")
