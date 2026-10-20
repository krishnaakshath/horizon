# Bundled fonts

Family: Inter 4.1 (SIL OFL 1.1, no Reserved Font Name). Upstream: https://github.com/rsms/inter, official release asset
https://github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip (33,707,794 bytes, sha256
`9883fdd4a49d4fb66bd8177ba6625ef9a64aa45899767dde3d36aa425756b11e`), downloaded over HTTPS on 2026-10-20.
Tooling: fontTools 4.60.2 (python 3.9, venv). Static TTFs only (no variable axes; minSdk 24).

## Inputs (from the zip, extras/ttf/)
| output | source | role |
|---|---|---|
| ui_light.ttf (300) | InterDisplay-Light.ttf (Inter Display optical cut) | big clock, temperature, large numerals |
| ui_regular.ttf (400) | Inter-Regular.ttf | body |
| ui_medium.ttf (500) | Inter-Medium.ttf | labels, subtitles |
| ui_semibold.ttf (600) | Inter-SemiBold.ttf | titles, hero |
| ui_bold.ttf (700) | Inter-Bold.ttf | rarely used |

LICENSE.txt from the zip (sha256 `262481e844521b326f5ecd053e59b98c8b2da78c8ee1bdbb6e8174305e54935a`) is
`app/src/main/res/raw/font_license_ofl.txt`, verbatim; a unit test pins the hash. Notice text: `FontNotices` and
`res/raw/third_party_notices.txt`.

## Build
`fonts/build-fonts.sh [venv] [Inter-4.1.zip]` downloads (or uses the given zip), verifies the zip hash and subsets with
pyftsubset: Latin + Latin Extended-A, punctuation, currency, arrows, U+25B2 U+25BC U+2605 U+2713; layout features
kern, liga, calt, tnum, pnum, case, locl, ccmp, mark, mkmk; no hinting. Subsetting is a permitted modification
(no RFN).

| file | bytes | sha256 |
|---|---|---|
| ui_light.ttf | 52492 | 1eaec401804cc8a475e5b36a93d87a83c1228b323aaa40ff1974d744d69c8d49 |
| ui_regular.ttf | 52544 | dba423ace749e83d219d2776841aeefcf6d446a4560c7f070b13f04c3665cf60 |
| ui_medium.ttf | 53572 | c300f8632c6b5ae216917af9c4b53f8d79f07d0021c57250b99f3d03dbc64365 |
| ui_semibold.ttf | 53580 | a6d95ae229c231fd0d5c718bb038d16e21be747276763f252198c0c30f2ec065 |
| ui_bold.ttf | 53408 | e525c0a283c06bcced54b63fd478af234bca7f5a16f50d6593c1b3a2ea75e47f |

Total 265,596 bytes.

## Metrics and fallback
upm 2048, hhea ascent 1984 / descent -494 / lineGap 0, USE_TYPO_METRICS set: natural line height 1.21 em
(`LineHeight.INTER_NATURAL_EM`). Advance widths: Latin text about 1-3% wider than the previous face, tabular digits about
5% narrower; Theme.style applies size-based tracking (`Tracking`). Telugu, Devanagari, CJK, emoji etc. are not in the
subset, so Android falls back to the system fonts per glyph.

## Brand wordmark face (Mintaka / M02)

Family: Unbounded (SIL OFL 1.1, no Reserved Font Name; Copyright 2022 The Unbounded Project Authors,
https://github.com/googlefonts/unbounded). Source: the official Google Fonts repository,
https://github.com/google/fonts at commit `47d54ca1ebb14fd864695f1d39dc734f39017c33`, `ofl/unbounded/Unbounded[wght].ttf`
(778,272 bytes, sha256 `323b511be380c8d474ef030686b71aedde501f8d9cd46da558b7c40454372c3f`; METADATA.pb `license: "OFL"`),
downloaded over HTTPS on 2026-10-20. Chosen over Sora, Syne, Bricolage Grotesque and Outfit. Syne's old-style
figures turn a digit mark like "02" into "o2", and Sora reads as generic at display sizes. Unbounded's wide, even strokes hold up at
720p on dark glass.

`fonts/build-brand-font.sh [venv] [Unbounded[wght].ttf]` builds a static wght=500 instance (fontTools varLib.instancer)
and subsets it with pyftsubset: space, `! & ' ( ) , - . / : 0-9 A-Z a-z · ’ – —`, kern only, no hinting.
`SOURCE_DATE_EPOCH` is fixed, so rebuilds are byte-identical.

| file | bytes | sha256 |
|---|---|---|
| assets/brand/brand_display.ttf | 15916 | 562aaee867f28f5981dea7e7ab926ef5c89ced36f6bf2ee3a1f90f2294a89273 |

`OFL.txt` from the same commit (sha256 `31e5d4e83955e7103c34570dd49b0570ef490800bd65b42923c0dd02445263b3`) is
`app/src/main/res/raw/brand_font_license_ofl.txt`, verbatim. `BrandFontTest` pins both hashes, the size limit (< 40 KB)
and the notices (`FontNotices.BRAND`, `res/raw/third_party_notices.txt`). Use: `Theme.brand` / `BrandFont`, only for the
splash "M02" and "MINTAKA". The asset lives outside `assets/fonts/` so the Inter plan (`FontPlan`) is unchanged.
The subset already holds every letter of the 1.0.1 names (M, I, N, T, A, K and 0-9), so the rename to Mintaka / M02
did not rebuild the font: the file and its hash are unchanged.

Icon and banner: `fonts/brand_art.py` converts the same outlines to vector paths in `res/drawable/tv_banner.xml`,
`ic_launcher_foreground.xml`, `ic_launcher_background.xml` and `res/mipmap-anydpi*/ic_launcher.xml`. The app loads no font
for these. `fonts/companion_art.py` does the same for M02 Store and M02 Browser (banner, icons, wordmarks, browser mark)
and the banners of M02 Weather, M02 Helper and M02 Link. Both scripts need fontTools (the venv that
`build-brand-font.sh` creates): `.fonts-venv/bin/python fonts/brand_art.py`, then `fonts/companion_art.py`.
