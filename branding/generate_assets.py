#!/usr/bin/env python3
"""Create Play Store PNGs from editable SVG sources (run from repository root).

Prerequisites:
    python -m pip install cairosvg
Then:
    python branding/generate_assets.py

No Android/Gradle dependency or external image service required.
"""
from pathlib import Path
import cairosvg

root = Path(__file__).resolve().parent
out = root / "play-store"
out.mkdir(parents=True, exist_ok=True)
for source, target, width, height in [
    ("bpm_player_icon.svg", "main_store_icon_512.png", 512, 512),
    ("feature_graphic.svg", "feature_graphic_1024x500.png", 1024, 500),
]:
    data = (root / "source" / source).read_bytes()
    path = out / target
    cairosvg.svg2png(bytestring=data, write_to=str(path),
                    output_width=width, output_height=height)
    print(f"Generated {path}: {width}×{height}")
