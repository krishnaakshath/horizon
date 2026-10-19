#!/usr/bin/env bash
# Rebuild app/src/main/assets/fonts/ui_*.ttf from the official variable Manrope.
# Usage: fonts/build-fonts.sh [venv-dir] [variable-ttf]
#   venv-dir      python venv containing fonttools (default: $FONTS_VENV or ./.fonts-venv, created if absent)
#   variable-ttf  Manrope[wght].ttf (default: downloaded from the pinned google/fonts commit)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/../app/src/main/assets/fonts"
SHA=b31870aff700ab7a1d74fa0c6887d95beb9e0037
WANT=3ae11c49db0455a3cc33e37d380f20fdb8c7f8b41dc07625c177e3d87a9d6ae6
VENV="${1:-${FONTS_VENV:-$HERE/.fonts-venv}}"
if [ ! -x "$VENV/bin/fonttools" ]; then python3 -m venv "$VENV"; "$VENV/bin/pip" -q install fonttools==4.60.2 brotli; fi
WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT
SRC="${2:-$WORK/Manrope-wght.ttf}"
[ -f "$SRC" ] || curl -fsSL -o "$SRC" "https://raw.githubusercontent.com/google/fonts/$SHA/ofl/manrope/Manrope%5Bwght%5D.ttf"
GOT="$(shasum -a 256 "$SRC" | cut -d' ' -f1)"; [ "$GOT" = "$WANT" ] || { echo "unexpected source hash $GOT" >&2; exit 1; }
UNI="U+0020-007E,U+00A0-00FF,U+0100-017F,U+2010-2015,U+2018-201E,U+2020-2022,U+2026,U+2030,U+2032-2033,U+20AC,U+20B9,U+2122,U+2190-2193,U+2212"
mkdir -p "$OUT"
for p in 300:light 400:regular 500:medium 600:semibold 700:bold; do
  w=${p%%:*}; n=${p#*:}
  "$VENV/bin/fonttools" varLib.instancer "$SRC" wght=$w -o "$WORK/inst_$n.ttf" --update-name-table -q
  "$VENV/bin/pyftsubset" "$WORK/inst_$n.ttf" --unicodes="$UNI" \
    --layout-features='kern,liga,calt,tnum,pnum,case,locl,ccmp,mark,mkmk' \
    --no-hinting --desubroutinize --name-IDs='*' --notdef-outline --output-file="$OUT/ui_$n.ttf"
done
shasum -a 256 "$OUT"/ui_*.ttf
