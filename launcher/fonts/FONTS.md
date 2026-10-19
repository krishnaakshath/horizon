# Bundled fonts

Family: Manrope (SIL OFL 1.1). Upstream: https://github.com/googlefonts/manrope, distributed via google/fonts.
All inputs are pinned to google/fonts commit `b31870aff700ab7a1d74fa0c6887d95beb9e0037` (latest commit touching ofl/manrope),
downloaded over HTTPS on 2026-10-19. Tooling: fontTools 4.60.2 (python 3.9, venv).

## Inputs
| file | URL | bytes | sha256 |
|---|---|---|---|
| Manrope[wght].ttf (variable, wght 200-800, magic 00010000) | https://raw.githubusercontent.com/google/fonts/b31870aff700ab7a1d74fa0c6887d95beb9e0037/ofl/manrope/Manrope%5Bwght%5D.ttf | 164700 | 3ae11c49db0455a3cc33e37d380f20fdb8c7f8b41dc07625c177e3d87a9d6ae6 |
| OFL.txt | https://raw.githubusercontent.com/google/fonts/b31870aff700ab7a1d74fa0c6887d95beb9e0037/ofl/manrope/OFL.txt | 4387 | 58172e0c0fac2cda8a37b348164bb55e44b0e69051e557e92b1d3f6910141f7b |

`cmp` of a fresh pinned download against `app/src/main/res/raw/font_license_ofl.txt`: IDENTICAL (same sha256). Its first line is
`Copyright 2018 The Manrope Project Authors (https://github.com/googlefonts/manrope)`. (The font's own name table nameID 0 says
"Copyright 2019 The Manrope Project Authors (https://github.com/googlefonts/manrope)"; both are upstream's, the notice quotes the OFL.txt line.)
A unit test pins the license sha256.

## Outputs (app/src/main/assets/fonts), 2026-10-19
Commands (copy-pasteable; `$SRC` = the variable font above, `$V` = venv with fonttools 4.60.2). Repeat for 300:light 400:regular 500:medium 600:semibold 700:bold:
```
UNI="U+0020-007E,U+00A0-00FF,U+0100-017F,U+2010-2015,U+2018-201E,U+2020-2022,U+2026,U+2030,U+2032-2033,U+20AC,U+20B9,U+2122,U+2190-2193,U+2212"
$V/bin/fonttools varLib.instancer "$SRC" wght=300 -o inst_light.ttf --update-name-table -q
$V/bin/pyftsubset inst_light.ttf --unicodes="$UNI" --layout-features='kern,liga,calt,tnum,pnum,case,locl,ccmp,mark,mkmk' --no-hinting --desubroutinize --name-IDs='*' --notdef-outline --output-file=ui_light.ttf
```
Or run `fonts/build-fonts.sh [venv] [variable-ttf]` (portable; verifies the source hash). Rebuilds are equal in content and size but not
byte-identical (instancer stamps head.modified), so the hashes below are for the committed files.

| file | weight | bytes | sha256 |
|---|---|---|---|
| ui_light.ttf | 300 | 49300 | 3e45597eb888d216ecf3e055e73ada947ef12cb26a553cabb5a59e01403a0792 |
| ui_regular.ttf | 400 | 49356 | a65997b4b3544a8c8078dedba65a813c5e1827eea9887147ef58acfacb961654 |
| ui_medium.ttf | 500 | 49424 | 742f82d94e72f83b4039c689d8bb551ebcf17d46165bbd4fce3d6e5e23260253 |
| ui_semibold.ttf | 600 | 49432 | fb24c0fb62a6eec07c99a2a935b01ce571edc4764ad884506f2f75aeac024bd3 |
| ui_bold.ttf | 700 | 49308 | a0ce0c6d151474d21e46ca7cd3672cda504e0245a49a601b0524e305849329e2 |

Total 246,820 bytes. Manrope has no Reserved Font Name. Not in the font (fall back to the system font): U+25B2, U+25BC, U+2605, U+2713.
Metrics (all instances): upm 2000, hhea ascent 2132 / descent -600 / lineGap 0, OS/2 typo identical, USE_TYPO_METRICS set; natural line height 1.366 em.
