# Bundled fonts

Family: Manrope (SIL OFL 1.1), Copyright 2018 The Manrope Project Authors.
Upstream: https://github.com/googlefonts/manrope, distributed via https://github.com/google/fonts (ofl/manrope).

## Sources (downloaded over HTTPS, 2026-10-19)
- https://raw.githubusercontent.com/google/fonts/main/ofl/manrope/Manrope%5Bwght%5D.ttf
  sha256 3ae11c49db0455a3cc33e37d380f20fdb8c7f8b41dc07625c177e3d87a9d6ae6 (variable, wght 200-800, magic 00010000)
- https://raw.githubusercontent.com/google/fonts/main/ofl/manrope/OFL.txt -> app/src/main/res/raw/font_license_ofl.txt

## Derived static instances (app/src/main/assets/fonts)
Made with fontTools 4.60.2: `varLib.instancer` (wght=300/400/500/600/700) then `pyftsubset` (Basic Latin, Latin-1,
Latin Extended-A, general punctuation, currency, arrows, a few symbols; features kern liga calt tnum pnum case locl ccmp
mark mkmk; hinting dropped). Recipe: fonts/build-fonts.sh (needs dl/manrope.ttf = the file above).
The font has no Reserved Font Name; modified subsets keep the Manrope name, which OFL 1.1 allows.

| file | weight | bytes | sha256 |
|---|---|---|---|
| ui_light.ttf | 300 | 49300 | 5d6490055c77e86a534df7f386a8fa21b7bf9110830e151dfcd19d3fc5dbec09 |
| ui_regular.ttf | 400 | 49356 | bd636c352768c528aeec678bb2842c04b8749ff0f14bc79bc98c672f165b0512 |
| ui_medium.ttf | 500 | 49424 | 96f2bdfe8b730f0be3a10117a0f20e033dc07d38a750f225f6c9c79242e2bc7f |
| ui_semibold.ttf | 600 | 49432 | ecb68f10ce631b3a7913c6ddedc29be531dde491c37639cecf6c83d4a4003a27 |
| ui_bold.ttf | 700 | 49308 | b76ce30a1b51a5f70df210e00a0f55a8663bfb7239f2c5d8a857f921048aac80 |

Total 246,820 bytes. Glyphs not in the subset (e.g. U+25B2, U+2605, U+2713) fall back to the system font automatically.
