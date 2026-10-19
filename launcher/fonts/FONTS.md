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
