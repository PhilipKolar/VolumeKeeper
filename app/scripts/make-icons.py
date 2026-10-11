#!/usr/bin/env python3
"""Regenerates app/assets/{icon,adaptive-icon,adaptive-icon-mono,splash-icon,favicon}.png.

A speaker with an upward arrow ("the level comes back"), accent blue on the app's #0a0a0a.
Run from app/: python3 scripts/make-icons.py
"""
from PIL import Image, ImageDraw

BG = (10, 10, 10, 255)
ACCENT = (91, 141, 239, 255)
GREEN = (34, 227, 154, 255)
WHITE = (255, 255, 255, 255)
CLEAR = (0, 0, 0, 0)


def glyph(size, scale, speaker, arrow):
    """Draws the glyph centred on a transparent canvas; `scale` = glyph width / canvas width."""
    img = Image.new("RGBA", (size, size), CLEAR)
    d = ImageDraw.Draw(img)
    s = size * scale / 100.0  # glyph drawn on a 100-unit grid
    ox = (size - 100 * s) / 2
    oy = (size - 100 * s) / 2

    def p(x, y):
        return (ox + x * s, oy + y * s)

    # speaker: box + cone
    d.polygon([p(4, 38), p(22, 38), p(46, 16), p(46, 84), p(22, 62), p(4, 62)], fill=speaker)
    # arrow: shaft + head, rising on the right
    d.rectangle([p(68, 34), p(80, 82)], fill=arrow)
    d.polygon([p(56, 40), p(74, 14), p(92, 40)], fill=arrow)
    return img


def compose(size, scale, bg, speaker, arrow):
    base = Image.new("RGBA", (size, size), bg)
    base.alpha_composite(glyph(size, scale, speaker, arrow))
    return base


compose(1024, 0.62, BG, ACCENT, GREEN).save("assets/icon.png")
glyph(1024, 0.50, ACCENT, GREEN).save("assets/adaptive-icon.png")  # inside the 66% safe zone
glyph(1024, 0.50, WHITE, WHITE).save("assets/adaptive-icon-mono.png")
glyph(400, 0.80, ACCENT, GREEN).save("assets/splash-icon.png")
compose(48, 0.70, BG, ACCENT, GREEN).save("assets/favicon.png")
print("icons written")
