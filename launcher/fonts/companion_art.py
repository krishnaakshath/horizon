#!/usr/bin/env python3
"""Writes the companion artwork in the H02 look: H02 Store and H02 Browser (banner, launcher icons, wordmarks,
browser splash mark) and the TV banners of H02 Weather, H02 Helper and H02 Link. The H02 mark is set in the
brand font as vector paths, on a glass card over a horizon sunrise, with the app's caption below.
Usage (fontTools venv, see build-brand-font.sh): python companion_art.py [brand_display.ttf] [launcher dir]"""
import sys, os
HERE = os.path.dirname(os.path.abspath(__file__))
FONT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "../app/src/main/assets/brand/brand_display.ttf")
ROOT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(HERE, "..")
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
F = TTFont(FONT); GS = F.getGlyphSet(); CMAP = F.getBestCmap(); UPM = F["head"].unitsPerEm; CAPH = F["OS/2"].sCapHeight

def width(text, cap, track=0.0):
    s = cap / CAPH
    adv = [GS[CMAP[ord(c)]].width for c in text]
    return (sum(adv) + track * UPM * (len(adv) - 1)) * s

def g(text, cx, base, cap, track=0.0):
    s = cap / CAPH
    x = cx - width(text, cap, track) / 2
    out = []
    for c in text:
        n = CMAP[ord(c)]
        pen = SVGPathPen(GS, ntos=lambda v: ("%.2f" % v).rstrip("0").rstrip("."))
        GS[n].draw(TransformPen(pen, (s, 0, 0, -s, x, base)))
        out.append(pen.getCommands())
        x += (GS[n].width + track * UPM) * s
    return " ".join(p for p in out if p)

MARK = "H02"  # Brand.SHORT (Horizon); BrandTest checks the generated files
TEXT = "#FFF5F7FA"; TEXT2 = "#FFA7B0C0"; CORE = "#FFB7CAED"
NS = '''xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"'''
def hdr(what):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {what}
     Original artwork in the H02 look. Letters are outlines of Unbounded Medium (SIL OFL 1.1) converted
     to paths: no font is loaded. Regenerate with fonts/companion_art.py. -->
'''
def night(w, h):
    return f'''    <path android:pathData="M0,0h{w}v{h}h-{w}z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="0" android:startY="0" android:endX="0" android:endY="{h}">
                <item android:offset="0" android:color="#FF0B0D12" />
                <item android:offset="0.62" android:color="#FF131A2B" />
                <item android:offset="1" android:color="#FF0B0D12" />
            </gradient>
        </aapt:attr>
    </path>
'''
def rrect(x, y, w, h, r):
    return f"M{x+r},{y}h{w-2*r}a{r},{r} 0,0 1 {r},{r}v{h-2*r}a{r},{r} 0,0 1 -{r},{r}h-{2*r-w}a{r},{r} 0,0 1 -{r},-{r}v-{h-2*r}a{r},{r} 0,0 1 {r},-{r}z".replace("h--", "h")
def glass(x, y, w, h, r, sw=1.0):
    d = f"M{x+r},{y}H{x+w-r}A{r},{r} 0,0 1 {x+w},{y+r}V{y+h-r}A{r},{r} 0,0 1 {x+w-r},{y+h}H{x+r}A{r},{r} 0,0 1 {x},{y+h-r}V{y+r}A{r},{r} 0,0 1 {x+r},{y}Z"
    return f'''    <path android:pathData="{d}" android:strokeColor="#38FFFFFF" android:strokeWidth="{sw}">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="{x}" android:startY="{y}" android:endX="{x}" android:endY="{y+h}">
                <item android:offset="0" android:color="#2EFFFFFF" />
                <item android:offset="0.45" android:color="#14FFFFFF" />
                <item android:offset="1" android:color="#0AFFFFFF" />
            </gradient>
        </aapt:attr>
    </path>
'''
def sun(cx, y, r, glow, accent):
    a = accent[3:]
    return f'''    <path android:pathData="M{cx-glow},{y}A{glow},{glow} 0,0 1 {cx+glow},{y}Z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="{cx}" android:centerY="{y}" android:gradientRadius="{glow}"
                android:startColor="#66{a}" android:endColor="#00{a}" />
        </aapt:attr>
    </path>
    <path android:pathData="M{cx-r},{y}A{r},{r} 0,0 1 {cx+r},{y}Z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="{cx}" android:centerY="{y - r*0.35:.2f}" android:gradientRadius="{r*1.1:.2f}"
                android:startColor="{CORE}" android:endColor="{accent}" />
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
def mark(d, color=TEXT, name=None):
    n = f' android:name="{name}"' if name else ''
    return f'    <path{n} android:fillColor="{color}" android:pathData="{d}" />\n'
def vec(w, h, body, tools=False):
    t = '\n    xmlns:tools="http://schemas.android.com/tools"\n    tools:ignore="VectorRaster"' if tools else ''
    return f'''<vector {NS}{t}
    android:width="{w}dp"
    android:height="{h}dp"
    android:viewportWidth="{w}"
    android:viewportHeight="{h}">
{body}</vector>
'''
def banner(caption, accent):
    b = night(320, 180) + glass(52, 26, 216, 128, 22) + sun(160, 112, 11, 62, accent) + line(70, 250, 112, 1.4)
    b += mark(g(MARK, 160, 92, 40)) + mark(g(caption, 160, 137, 7, 0.62), TEXT2)
    return vec(320, 180, b, tools=True)
def fg_body(accent):
    return glass(24, 24, 60, 60, 16, 0.8) + sun(54, 64, 5, 22, accent) + line(30, 78, 64, 1.1) + mark(g(MARK, 54, 56, 13))
def fg(accent):
    return vec(108, 108, fg_body(accent))
def bg():
    return vec(108, 108, night(108, 108))
def legacy(accent):
    k = 48 / 66
    inner = "".join("    " + l + "\n" for l in fg_body(accent).rstrip("\n").split("\n"))
    body = f'    <path android:fillColor="#FF111624" android:pathData="M10,0h28a10,10 0 0,1 10,10v28a10,10 0 0,1 -10,10h-28a10,10 0 0,1 -10,-10v-28a10,10 0 0,1 10,-10z" />\n'
    body += f'    <group android:scaleX="{k:.4f}" android:scaleY="{k:.4f}" android:translateX="{-21*k:.3f}" android:translateY="{-21*k:.3f}">\n{inner}    </group>\n'
    return vec(48, 48, body)
def wordmark(text, cap=20, color=TEXT):
    w = width(text, cap) + 4
    h = cap + 4
    return vec(round(w, 2), h, mark(g(text, w / 2, cap + 2, cap), color))
def adaptive(bgname, fgname, mono=False):
    m = f'\n    <monochrome android:drawable="@drawable/{fgname}" />' if mono else ''
    return f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/{bgname}" />
    <foreground android:drawable="@drawable/{fgname}" />{m}
</adaptive-icon>
'''
def browser_mark(named):
    # 48 dp splash / start-page mark: a glass disc with H02 over a small sunrise.
    body = glass(2, 2, 44, 44, 22, 0.8)
    inner = sun(24, 31, 3.5, 14, BROWSER_ACCENT) + line(9, 39, 31, 0.9) + mark(g(MARK, 24, 27, 9.5), name="letters" if named else None)
    if named:
        inner = "".join("    " + l + "\n" for l in inner.rstrip("\n").split("\n"))
        body += f'    <group android:name="mark" android:pivotX="24" android:pivotY="24">\n{inner}    </group>\n'
    else:
        body += inner
    return vec(48, 48, body)
REVEAL = '''<?xml version="1.0" encoding="utf-8"?>
<!-- Cold-start reveal of the H02 Browser mark: the sunrise scales up and the letters fade in (about 520 ms). -->
<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:drawable="@drawable/brand_mark_anim_base">
    <target android:name="mark">
        <aapt:attr name="android:animation">
            <set>
                <objectAnimator android:propertyName="scaleX" android:valueFrom="0.82" android:valueTo="1"
                    android:duration="420" android:interpolator="@android:interpolator/fast_out_slow_in"/>
                <objectAnimator android:propertyName="scaleY" android:valueFrom="0.82" android:valueTo="1"
                    android:duration="420" android:interpolator="@android:interpolator/fast_out_slow_in"/>
            </set>
        </aapt:attr>
    </target>
    <target android:name="letters">
        <aapt:attr name="android:animation">
            <objectAnimator android:propertyName="fillAlpha" android:valueFrom="0" android:valueTo="1"
                android:startOffset="160" android:duration="360" android:interpolator="@android:interpolator/fast_out_slow_in"/>
        </aapt:attr>
    </target>
</animated-vector>
'''
STORE_ACCENT = "#FF2DD4BF"; BROWSER_ACCENT = "#FF8B5CF6"
WEATHER_ACCENT = "#FFFFB347"; HELPER_ACCENT = "#FF4C8DFF"; LINK_ACCENT = "#FF4FC3F7"
st = os.path.join(ROOT, "store/src/main/res"); br = os.path.join(ROOT, "browser/src/main/res")
wx = os.path.join(ROOT, "weather/src/main/res"); hp = os.path.join(ROOT, "helper/src/main/res"); lk = os.path.join(ROOT, "link/src/main/res")
files = {
    f"{st}/drawable/banner.xml": hdr("320 x 180 dp TV banner of H02 Store: the H02 mark on a glass card over a sunrise, STORE caption.") + banner("STORE", STORE_ACCENT),
    f"{st}/drawable/ic_launcher_foreground.xml": hdr("H02 Store adaptive-icon foreground (108 dp, inside the 66 dp safe zone).") + fg(STORE_ACCENT),
    f"{st}/drawable/ic_launcher_background.xml": hdr("H02 Store adaptive-icon background: the night base.") + bg(),
    f"{st}/drawable/ic_launcher_legacy.xml": hdr("H02 Store icon for API 24-25: rounded night square with the glass H02 mark.") + legacy(STORE_ACCENT),
    f"{st}/drawable/wordmark.xml": hdr("H02 Store wordmark for the page header.") + wordmark("H02 Store"),
    f"{br}/drawable/banner_browser.xml": hdr("320 x 180 dp TV banner of H02 Browser: the H02 mark on a glass card over a sunrise, BROWSER caption.") + banner("BROWSER", BROWSER_ACCENT),
    f"{br}/drawable/ic_launcher_fg.xml": hdr("H02 Browser adaptive-icon foreground (108 dp, inside the 66 dp safe zone).") + fg(BROWSER_ACCENT),
    f"{br}/drawable/ic_launcher_bg.xml": hdr("H02 Browser adaptive-icon background: the night base.") + bg(),
    f"{br}/mipmap-anydpi/ic_launcher.xml": hdr("H02 Browser icon for API 24-25: rounded night square with the glass H02 mark.") + legacy(BROWSER_ACCENT),
    f"{br}/drawable/brand_mark.xml": hdr("H02 Browser mark (splash, start page, tabs): glass disc, H02 over a sunrise.") + browser_mark(False),
    f"{br}/drawable/brand_mark_anim_base.xml": hdr("Base of brand_mark_reveal: the H02 Browser mark with named group and letters.") + browser_mark(True),
    f"{br}/drawable/brand_mark_reveal.xml": REVEAL,
    f"{br}/drawable/brand_wordmark.xml": hdr("H02 Browser wordmark (splash and start page).") + wordmark("H02 Browser"),
    f"{wx}/drawable/banner_weather.xml": hdr("320 x 180 dp TV banner of H02 Weather: the H02 mark on a glass card over a sunrise, WEATHER caption.") + banner("WEATHER", WEATHER_ACCENT),
    f"{hp}/drawable/banner.xml": hdr("320 x 180 dp TV banner (and icon) of H02 Helper: the H02 mark on a glass card over a sunrise, HELPER caption.") + banner("HELPER", HELPER_ACCENT),
    f"{lk}/drawable/banner.xml": hdr("320 x 180 dp TV banner of H02 Link (shown in the TV's app lists): the H02 mark on a glass card over a sunrise, LINK caption.") + banner("LINK", LINK_ACCENT),
}
for p, s in files.items():
    open(p, "w").write(s)
    print("wrote", os.path.relpath(p, ROOT))
