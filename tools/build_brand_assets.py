#!/usr/bin/env python3
"""Regenerates every brand image from the four original files in docs/brand/source/.

  python3 tools/build_brand_assets.py

Inputs  (docs/brand/source): Dark_Mode_Logo.jpeg, Light_mode_Logo.jpeg, Favicon.jpeg, Icon.jpeg
Outputs src/main/resources/brand/   logo-dark.png, logo-light.png, mark-dark.png, mark-light.png,
                                    icon-{16..512}.png, favicon-{16,32,48}.png, favicon.ico, icon.ico
        installer/icons/            icon_*.png, kaziflow_256.png, kaziflow.ico, kaziflow.iconset/*  (replaces the old "K" icon)
        docs/brand/preview.png      contact sheet for a quick visual check

The sources are JPEGs with opaque backgrounds, so each is converted to a transparent PNG ("colour to alpha"):
the near-black / light-grey background is removed and the edge pixels are un-mixed from it, so the logos sit
cleanly on any surface. The app icon is REBUILT crisply from the 1254 px master mark on a rounded square that
matches the supplied Icon.jpeg (colours, corner radius, mark proportions), because the supplied icon is only
~168 px and would look soft in installers. If you supply vector or higher-resolution originals, drop them in
docs/brand/source and adjust the paths here.
"""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SRC = os.path.join(ROOT, "docs", "brand", "source")
RES = os.path.join(ROOT, "src", "main", "resources", "brand")
INST = os.path.join(ROOT, "installer", "icons")
os.makedirs(RES, exist_ok=True)
os.makedirs(os.path.join(INST, "kaziflow.iconset"), exist_ok=True)


def load(name):
    return Image.open(os.path.join(SRC, name)).convert("RGB")


def alpha_from_black(img, lo=14, hi=60):
    """Remove a near-black background. alpha ramps from 0 (<=lo) to 1 (>=hi) on the brightest channel."""
    a = np.asarray(img).astype(float)
    m = a.max(axis=2)
    alpha = np.clip((m - lo) / (hi - lo), 0, 1)
    out = np.dstack([a, alpha * 255]).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def alpha_from_light(img, bg=None, thr=0.03):
    """Remove a light background by un-mixing each pixel from it (true 'colour to alpha')."""
    a = np.asarray(img).astype(float)
    if bg is None:  # estimate from the border
        border = np.concatenate([a[0], a[-1], a[:, 0], a[:, -1]])
        bg = np.median(border, axis=0)
    bg = np.asarray(bg, float)
    alpha = np.max((bg - a) / bg, axis=2)
    alpha = np.clip(alpha, 0, 1)
    alpha[alpha < thr] = 0
    safe = np.where(alpha > 0, alpha, 1)[..., None]
    fg = np.clip((a - (1 - alpha[..., None]) * bg) / safe, 0, 255)
    out = np.dstack([fg, alpha * 255]).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def crop_to_content(rgba, pad=0):
    bbox = rgba.getchannel("A").point(lambda v: 255 if v > 8 else 0).getbbox()
    l, t, r, b = bbox
    return rgba.crop((max(0, l - pad), max(0, t - pad), min(rgba.width, r + pad), min(rgba.height, b + pad)))


def fit_square(img, size, fill=0.9):
    """Place img centred on a transparent square canvas, scaled so its longest side is `fill` of the canvas."""
    scale = size * fill / max(img.size)
    w, h = max(1, round(img.width * scale)), max(1, round(img.height * scale))
    r = img.resize((w, h), Image.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.paste(r, ((size - w) // 2, (size - h) // 2), r)
    return canvas


# ── logos (transparent) ───────────────────────────────────────────────────────
dark = load("Dark_Mode_Logo.jpeg")
light = load("Light_mode_Logo.jpeg")
fav = load("Favicon.jpeg")

logo_dark = crop_to_content(alpha_from_black(dark), pad=6)
logo_light = crop_to_content(alpha_from_light(light), pad=4)
# the symbol alone (no wordmark): rows measured from the sources
mark_dark = crop_to_content(alpha_from_black(dark.crop((0, 262, dark.width, 726))), pad=6)
mark_light = crop_to_content(alpha_from_light(light.crop((0, 26, light.width, 208)), bg=(247, 247, 247)), pad=3)
mark_fav = crop_to_content(alpha_from_light(fav, bg=(247, 247, 247)), pad=2)

def scaled(img, width):
    return img.resize((width, round(img.height * width / img.width)), Image.LANCZOS) if img.width > width else img

scaled(logo_dark, 900).save(os.path.join(RES, "logo-dark.png"), optimize=True)
logo_light.save(os.path.join(RES, "logo-light.png"), optimize=True)
scaled(mark_dark, 512).save(os.path.join(RES, "mark-dark.png"), optimize=True)
mark_light.save(os.path.join(RES, "mark-light.png"), optimize=True)

# ── app icon: crisp rounded square built from the master mark ─────────────────
def build_icon(size, mark_fill):
    S = size * 4 if size < 256 else size            # supersample small sizes for clean antialiasing
    top, bottom = np.array([14, 13, 27]), np.array([2, 2, 10])   # sampled from the supplied Icon.jpeg
    t = np.linspace(0, 1, S)[:, None, None]
    grad = (top * (1 - t) + bottom * t).astype(np.uint8)
    base = Image.fromarray(np.repeat(grad, S, axis=1), "RGB").convert("RGBA")
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, S - 1, S - 1), radius=int(S * 0.225), fill=255)
    m = fit_square(mark_dark, S, fill=mark_fill)
    base.alpha_composite(m)
    base.putalpha(mask)
    return base.resize((size, size), Image.LANCZOS) if S != size else base

SIZES = [16, 24, 32, 48, 64, 128, 256, 512, 1024]
icons = {}
for s in SIZES:
    icons[s] = build_icon(s, mark_fill=0.86 if s <= 32 else 0.80)
for s in [16, 24, 32, 48, 64, 128, 256, 512]:
    icons[s].save(os.path.join(RES, f"icon-{s}.png"), optimize=True)

def save_ico(path, sizes):
    base = icons[max(sizes)]
    base.save(path, format="ICO", sizes=[(s, s) for s in sizes], append_images=[icons[s] for s in sizes if s != max(sizes)])

save_ico(os.path.join(RES, "icon.ico"), [16, 24, 32, 48, 64, 128, 256])

# favicons from the supplied favicon file (light-theme mark on transparent)
fav_sq = {s: fit_square(mark_fav, s, fill=0.94) for s in (16, 32, 48, 64)}
for s in (16, 32, 48):
    fav_sq[s].save(os.path.join(RES, f"favicon-{s}.png"), optimize=True)
fav_sq[64].save(os.path.join(RES, "favicon.ico"), format="ICO", sizes=[(16, 16), (32, 32), (48, 48), (64, 64)],
                append_images=[fav_sq[s] for s in (16, 32, 48)])

# ── installer icons: same file names as before, so the installer scripts need no change ──
for s in [16, 32, 48, 64, 128, 256, 512, 1024]:
    icons[s].save(os.path.join(INST, f"icon_{s}.png"), optimize=True)
icons[256].save(os.path.join(INST, "kaziflow_256.png"), optimize=True)
save_ico(os.path.join(INST, "kaziflow.ico"), [16, 24, 32, 48, 64, 128, 256])
for base_px in (16, 32, 128, 256, 512):
    icons[base_px].save(os.path.join(INST, "kaziflow.iconset", f"icon_{base_px}x{base_px}.png"), optimize=True)
    icons[base_px * 2].save(os.path.join(INST, "kaziflow.iconset", f"icon_{base_px}x{base_px}@2x.png"), optimize=True)

# ── contact sheet for review: every asset on a light and a dark ground ───────
sheet = Image.new("RGB", (1500, 720), (255, 255, 255))
dark_half = Image.new("RGB", (1500, 360), (13, 27, 42))
sheet.paste(dark_half, (0, 360))
d = ImageDraw.Draw(sheet)
def put(img, x, y, h):
    r = img.resize((round(img.width * h / img.height), h), Image.LANCZOS)
    sheet.paste(r, (x, y), r)
    return x + r.width + 30
x = 20
x = put(Image.open(os.path.join(RES, "logo-light.png")), x, 30, 200)
x = put(Image.open(os.path.join(RES, "mark-light.png")), x, 40, 150)
for s in (256, 128, 64, 32, 16):
    x = put(icons[s], x, 40, s)
x = 20
x = put(Image.open(os.path.join(RES, "logo-dark.png")), x, 390, 260)
x = put(Image.open(os.path.join(RES, "mark-dark.png")), x, 420, 150)
for s in (256, 128, 64, 32, 16):
    x = put(icons[s], x, 420, s)
d.text((20, 6), "light surface", fill=(100, 100, 100)); d.text((20, 366), "dark surface", fill=(180, 190, 200))
sheet.save(os.path.join(ROOT, "docs", "brand", "preview.png"))
print("brand assets built:", sorted(os.listdir(RES)))
