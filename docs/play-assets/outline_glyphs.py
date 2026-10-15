#!/usr/bin/env python3
"""Outlines `>ACT_` from IBM Plex Mono SemiBold into 108x108 canvas path data (needs fontTools).

Prints three lines, `ink`, `accent`: the SVG `d` strings. `>` and `_` are the accent, `ACT` the ink. The five glyphs
are scaled together so the ink fits INK_WIDTH, centred on the canvas. See README.md.
"""
import sys
from pathlib import Path
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

CANVAS, INK_WIDTH, TEXT = 108, 62, ">ACT_"
font_path = Path(__file__).resolve().parents[2] / "app/shared/src/commonMain/composeResources/font/ibm_plex_mono_semibold.ttf"
font = TTFont(font_path)
gs, cmap = font.getGlyphSet(), font.getBestCmap()
adv = gs[cmap[ord("A")]].width

def bounds(name):
    p = BoundsPen(gs); gs[name].draw(p); return p.bounds

# Ink extent across the whole run, for scale and centring (y is up in the font).
xs, ys = [], []
for i, ch in enumerate(TEXT):
    x0, y0, x1, y1 = bounds(cmap[ord(ch)])
    xs += [i * adv + x0, i * adv + x1]; ys += [y0, y1]
scale = INK_WIDTH / (max(xs) - min(xs))
ox = CANVAS / 2 - (min(xs) + max(xs)) / 2 * scale
oy = CANVAS / 2 + (min(ys) + max(ys)) / 2 * scale

def path(chars_at):
    pen = SVGPathPen(gs, ntos=lambda v: f"{v:.2f}".rstrip("0").rstrip("."))
    for i in chars_at:
        gs[cmap[ord(TEXT[i])]].draw(TransformPen(pen, (scale, 0, 0, -scale, ox + i * adv * scale, oy)))
    return pen.getCommands()

print("ink   ", path([1, 2, 3]))
print("accent", path([0, 4]))
