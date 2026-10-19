#!/usr/bin/env bash
# Rebuild app/src/main/assets/fonts/ui_*.ttf from the official Inter 4.1 release (static TTFs).
# Usage: fonts/build-fonts.sh [venv-dir] [Inter-4.1.zip]
#   venv-dir  python venv containing fonttools (default: $FONTS_VENV or ./.fonts-venv, created if absent)
#   zip       Inter-4.1.zip (default: downloaded from the rsms/inter GitHub release; sha256 verified)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/../app/src/main/assets/fonts"
WANT=9883fdd4a49d4fb66bd8177ba6625ef9a64aa45899767dde3d36aa425756b11e
VENV="${1:-${FONTS_VENV:-$HERE/.fonts-venv}}"
if [ ! -x "$VENV/bin/fonttools" ]; then python3 -m venv "$VENV"; "$VENV/bin/pip" -q install fonttools==4.60.2 brotli; fi
WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT
ZIP="${2:-$WORK/Inter-4.1.zip}"
[ -f "$ZIP" ] || curl -fsSL -o "$ZIP" "https://github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip"
GOT="$(shasum -a 256 "$ZIP" | cut -d' ' -f1)"; [ "$GOT" = "$WANT" ] || { echo "unexpected zip hash $GOT" >&2; exit 1; }
mkdir "$WORK/x"; unzip -q "$ZIP" -d "$WORK/x"
UNI="U+0020-007E,U+00A0-00FF,U+0100-017F,U+2010-2015,U+2018-201E,U+2020-2022,U+2026,U+2030,U+2032-2033,U+20AC,U+20B9,U+2122,U+2190-2193,U+2212,U+25B2,U+25BC,U+2605,U+2713"
mkdir -p "$OUT"
# out-name : source ttf (Light uses the Display optical cut: clock and big numerals)
for p in light:InterDisplay-Light regular:Inter-Regular medium:Inter-Medium semibold:Inter-SemiBold bold:Inter-Bold; do
  n=${p%%:*}; s=${p#*:}
  "$VENV/bin/pyftsubset" "$WORK/x/extras/ttf/$s.ttf" --unicodes="$UNI" \
    --layout-features='kern,liga,calt,tnum,pnum,case,locl,ccmp,mark,mkmk' \
    --no-hinting --desubroutinize --name-IDs='*' --notdef-outline --output-file="$OUT/ui_$n.ttf"
done
shasum -a 256 "$OUT"/ui_*.ttf; ls -l "$OUT"
