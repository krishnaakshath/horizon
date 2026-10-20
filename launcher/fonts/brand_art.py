#!/usr/bin/env python3
"""Writes the launcher banner and adaptive icon (H02 mark: a sun rising on a horizon line) as vector XML.
The letters are the brand font's outlines converted to paths, so the icons need no font at runtime.
Usage (fontTools venv, see build-brand-font.sh): python brand_art.py [brand_display.ttf] [app/src/main/res]"""
import sys, os
HERE = os.path.dirname(os.path.abspath(__file__))
FONT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "../app/src/main/assets/brand/brand_display.ttf")
res = sys.argv[2] if len(sys.argv) > 2 else os.path.join(HERE, "../app/src/main/res")
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

def g(text, cx, base, cap, track=0.0):
    """pathData for [text] set in the brand TTF, centred on cx, cap height [cap] px, tracking in em."""
    f = TTFont(FONT); gs = f.getGlyphSet(); cmap = f.getBestCmap()
    s = cap / f["OS/2"].sCapHeight; upm = f["head"].unitsPerEm
    names = [cmap[ord(c)] for c in text]
    adv = [gs[n].width for n in names]
    x = cx - (sum(adv) + track * upm * (len(names) - 1)) * s / 2
    out = []
    for n, a in zip(names, adv):
        pen = SVGPathPen(gs, ntos=lambda v: ("%.2f" % v).rstrip("0").rstrip("."))
        gs[n].draw(TransformPen(pen, (s, 0, 0, -s, x, base)))
        out.append(pen.getCommands())
        x += (a + track * upm) * s
    return " ".join(p for p in out if p)
MARK = "H02"; CAPTION = "HORIZON"  # Brand.SHORT and Brand.CAPTION
ACCENT = "#FF4C8DFF"; CORE = "#FFB7CAED"; TEXT = "#FFF5F7FA"; TEXT2 = "#FFA7B0C0"
HDR = '''<?xml version="1.0" encoding="utf-8"?>
<!-- %s
     Original artwork. Letters are outlines of Unbounded Medium (SIL OFL 1.1) converted to paths:
     no font is loaded. Regenerate with fonts/brand_art.py. -->
'''
def sun(cx, y, r, glow_r):
    # Upper half-disc sitting on the horizon, plus a soft bloom (also clipped at the horizon).
    return f'''    <path android:pathData="M{cx-glow_r},{y}A{glow_r},{glow_r} 0,0 1 {cx+glow_r},{y}Z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="{cx}" android:centerY="{y}" android:gradientRadius="{glow_r}"
                android:startColor="#664C8DFF" android:endColor="#004C8DFF" />
        </aapt:attr>
    </path>
    <path android:pathData="M{cx-r},{y}A{r},{r} 0,0 1 {cx+r},{y}Z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="{cx}" android:centerY="{y - r*0.35:.2f}" android:gradientRadius="{r*1.1:.2f}"
                android:startColor="{CORE}" android:endColor="{ACCENT}" />
        </aapt:attr>
    </path>
'''
def line(x0, x1, y, w):
    return f'''    <path android:pathData="M{x0},{y - w/2:.2f}H{x1}V{y + w/2:.2f}H{x0}Z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="{x0}" android:startY="{y}" android:endX="{x1}" android:endY="{y}">
                <item android:offset="0" android:color="#00F5F7FA" />
                <item android:offset="0.5" android:color="#D9F5F7FA" />
                <item android:offset="1" android:color="#00F5F7FA" />
            </gradient>
        </aapt:attr>
    </path>
'''
def mark(d, color=TEXT):
    return f'    <path android:fillColor="{color}" android:pathData="{d}" />\n'

def body_banner():
    s = ''
    s += '''    <path android:pathData="M0,0h320v180h-320z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="0" android:startY="0" android:endX="0" android:endY="180">
                <item android:offset="0" android:color="#FF0B0D12" />
                <item android:offset="0.62" android:color="#FF131A2B" />
                <item android:offset="1" android:color="#FF0B0D12" />
            </gradient>
        </aapt:attr>
    </path>
'''
    s += sun(160, 120, 12, 70)
    s += line(36, 284, 120, 1.6)
    s += mark(g(MARK, 160, 98, 42))
    s += mark(g(CAPTION, 160, 148, 7, 0.62), TEXT2)
    return s

banner = HDR % "320 x 180 dp TV banner (android:banner): the H02 mark over a horizon line with a rising sun, and the HORIZON caption." + '''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    xmlns:tools="http://schemas.android.com/tools"
    tools:ignore="VectorRaster"
    android:width="320dp"
    android:height="180dp"
    android:viewportWidth="320"
    android:viewportHeight="180">
''' + body_banner() + '</vector>\n'

def fg_body():
    return sun(54, 66, 5, 24) + line(28, 80, 66, 1.2) + mark(g(MARK, 54, 57, 14))

fg = HDR % "Adaptive-icon foreground (108 dp, content inside the 66 dp safe zone): H02 over a sunrise." + '''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
''' + fg_body() + '</vector>\n'

bg = '''<?xml version="1.0" encoding="utf-8"?>
<!-- Adaptive-icon background: the near-black base with a faint night-sky lift toward the horizon. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="0" android:startY="0" android:endX="0" android:endY="108">
                <item android:offset="0" android:color="#FF0B0D12" />
                <item android:offset="0.6" android:color="#FF151D31" />
                <item android:offset="1" android:color="#FF0B0D12" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
'''
k = 48 / 66
legacy = HDR % "API 24-25 icon: rounded night square with the H02 sunrise mark (the adaptive foreground's safe zone)." + f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="48"
    android:viewportHeight="48">
    <path android:fillColor="#FF111624" android:pathData="M10,0h28a10,10 0 0,1 10,10v28a10,10 0 0,1 -10,10h-28a10,10 0 0,1 -10,-10v-28a10,10 0 0,1 10,-10z" />
    <group android:scaleX="{k:.4f}" android:scaleY="{k:.4f}" android:translateX="{-21*k:.3f}" android:translateY="{-21*k:.3f}">
''' + "".join("    " + l + "\n" if l else "\n" for l in fg_body().rstrip("\n").split("\n")) + '''    </group>
</vector>
'''
adaptive = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
'''
files = {"drawable/tv_banner.xml": banner, "drawable/ic_launcher_foreground.xml": fg,
         "drawable/ic_launcher_background.xml": bg, "mipmap-anydpi/ic_launcher.xml": legacy,
         "mipmap-anydpi-v26/ic_launcher.xml": adaptive}
for p, c in files.items():
    os.makedirs(os.path.dirname(os.path.join(res, p)), exist_ok=True)
    open(os.path.join(res, p), "w").write(c)
    print(p, len(c))
