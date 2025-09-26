#!/usr/bin/env python3
"""
icon_updater.py

One-click app icon updater:

1. Calls svg_to_vd.py to convert the source SVG into a VectorDrawable,
   scaled to a 108x108dp viewport (Android adaptive icon spec).
2. Writes the VectorDrawable to res/drawable/ic_launcher_foreground.xml.
3. Writes a solid-color background resource to res/drawable/ic_launcher_background.xml.
4. Generates per-density ic_launcher.webp and ic_launcher_round.webp
   into each mipmap-* folder.

Configure SVG_FILE, RES_FOLDER, and BACKGROUND_COLOR below, then run.
"""

import io
import shutil
import subprocess
import sys
from pathlib import Path
from PIL import Image, ImageDraw
import cairosvg

# ----------------- CONFIGURATION -----------------
SVG_FILE = Path("./ic_fisheye_foreground.svg")
RES_FOLDER = Path("./app/src/main/res")
SVG_TO_VD_SCRIPT = Path("./svg_to_vd.py")  # must exist in same folder or adjust path
BACKGROUND_COLOR = "#2ecc71"
OUTPUT_BASE_SIZE = 512  # used for rasterized webp generation
WEBP_QUALITY = 90
ROUND_PADDING = 0.06
# -------------------------------------------------

DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

def get_best_resample():
    try:
        return Image.Resampling.LANCZOS
    except Exception:
        try:
            return Image.LANCZOS
        except Exception:
            return Image.BICUBIC

RESAMPLE = get_best_resample()

def ensure_folder(p: Path):
    p.mkdir(parents=True, exist_ok=True)

def hex_to_rgba(hex_color: str):
    if not hex_color:
        return None
    s = hex_color.lstrip("#")
    if len(s) == 3:
        s = "".join(ch*2 for ch in s)
    r = int(s[0:2], 16)
    g = int(s[2:4], 16)
    b = int(s[4:6], 16)
    return (r, g, b, 255)

def svg_to_pil_intrinsic_then_resize(svg_path: Path, target_size: int):
    png_bytes = cairosvg.svg2png(url=str(svg_path))
    img = Image.open(io.BytesIO(png_bytes)).convert("RGBA")
    if img.size[0] != img.size[1]:
        w, h = img.size
        side = min(w, h)
        left = (w - side) // 2
        top = (h - side) // 2
        img = img.crop((left, top, left + side, top + side))
    if target_size and img.size[0] != target_size:
        img = img.resize((target_size, target_size), RESAMPLE)
    return img

def save_webp(image: Image.Image, out_path: Path, quality=90):
    ensure_folder(out_path.parent)
    image.convert("RGBA").save(out_path, "WEBP", quality=quality)

def make_round(image: Image.Image, padding_frac: float = 0.06):
    w, h = image.size
    side = min(w, h)
    if (w, h) != (side, side):
        left = (w - side)//2
        top = (h - side)//2
        image = image.crop((left, top, left + side, top + side)).resize((side, side), RESAMPLE)
    mask = Image.new("L", (side, side), 0)
    draw = ImageDraw.Draw(mask)
    pad = int(side * padding_frac)
    draw.ellipse((pad, pad, side - pad - 1, side - pad - 1), fill=255)
    out = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    out.paste(image, (0, 0), mask)
    return out

def run_svg_to_vd(svg_path: Path, out_path: Path):
    """Run svg_to_vd.py to convert svg to vector drawable XML."""
    ensure_folder(out_path.parent)
    print(f"Converting {svg_path} -> VectorDrawable {out_path}")
    result = subprocess.run(
        [sys.executable, str(SVG_TO_VD_SCRIPT), str(svg_path), str(out_path)],
        capture_output=True,
        text=True
    )
    if result.returncode != 0:
        print("svg_to_vd.py failed:\n", result.stderr)
        sys.exit(1)
    else:
        print("svg_to_vd.py output:\n", result.stdout)

def write_background_xml(out_path: Path, hex_color: str):
    ensure_folder(out_path.parent)
    content = f"""<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="{hex_color}" />
</shape>
"""
    out_path.write_text(content, encoding="utf-8")

def generate(svg_file: Path, res_folder: Path, bg_color_hex: str):
    svg_file = Path(svg_file)
    res_folder = Path(res_folder)
    if not svg_file.exists():
        raise SystemExit(f"ERROR: SVG_FILE not found: {svg_file}")
    if not SVG_TO_VD_SCRIPT.exists():
        raise SystemExit(f"ERROR: svg_to_vd.py not found: {SVG_TO_VD_SCRIPT}")

    print(f"SVG: {svg_file}")
    print(f"res folder: {res_folder}")

    # 1) Convert SVG to VectorDrawable
    fg_xml_path = res_folder / "drawable" / "ic_launcher_foreground.xml"
    run_svg_to_vd(svg_file, fg_xml_path)

    # 2) Write background XML
    bg_xml_path = res_folder / "drawable" / "ic_launcher_background.xml"
    write_background_xml(bg_xml_path, bg_color_hex)

    # 3) Rasterize foreground for per-density webps
    fg_master = svg_to_pil_intrinsic_then_resize(svg_file, OUTPUT_BASE_SIZE)

    for density, px in DENSITIES.items():
        mipmap_folder = res_folder / density
        ensure_folder(mipmap_folder)
        print(f"Generating {density} -> {px}x{px}")
        fg_resized = fg_master.resize((px, px), RESAMPLE)
        launcher_webp = mipmap_folder / "ic_launcher.webp"
        save_webp(fg_resized, launcher_webp, quality=WEBP_QUALITY)
        round_img = make_round(fg_resized, padding_frac=ROUND_PADDING)
        launcher_round_webp = mipmap_folder / "ic_launcher_round.webp"
        save_webp(round_img, launcher_round_webp, quality=WEBP_QUALITY)

    print("\nDone.")
    print(" - VectorDrawable foreground written to drawable/ic_launcher_foreground.xml")
    print(" - Background color written to drawable/ic_launcher_background.xml")
    print(" - Per-mipmap: ic_launcher.webp and ic_launcher_round.webp created.")

if __name__ == "__main__":
    try:
        generate(SVG_FILE, RES_FOLDER, BACKGROUND_COLOR)
    except Exception as e:
        print("ERROR:", e)
        sys.exit(1)
