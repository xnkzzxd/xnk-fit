#!/usr/bin/env python3
"""Shrinks the reference PNGs (64-colour palette, no dithering) so docs/reference/xnk/screens stays small.
Run after capture-reference.js (needs Pillow). Usage: python3 tools/compare/shrink-reference.py [dir]"""
import glob, os, sys
from PIL import Image
d = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "..", "docs/reference/xnk/screens")
for f in glob.glob(os.path.join(d, "*_*_*.png")):
    Image.open(f).convert("RGB").quantize(64, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).save(f, optimize=True)
