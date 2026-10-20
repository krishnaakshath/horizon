#!/usr/bin/env bash
# Rebuild app/src/main/assets/brand/brand_display.ttf (the Horizon wordmark face) from Unbounded,
# google/fonts commit 47d54ca1ebb14fd864695f1d39dc734f39017c33, ofl/unbounded (SIL OFL 1.1, no Reserved Font Name).
# Usage: fonts/build-brand-font.sh [venv-dir] [Unbounded[wght].ttf]
set -euo pipefail
export SOURCE_DATE_EPOCH=1760918400 # fixed head.modified: byte-identical rebuilds
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/../app/src/main/assets/brand"
COMMIT=47d54ca1ebb14fd864695f1d39dc734f39017c33
WANT=323b511be380c8d474ef030686b71aedde501f8d9cd46da558b7c40454372c3f
VENV="${1:-${FONTS_VENV:-$HERE/.fonts-venv}}"
if [ ! -x "$VENV/bin/fonttools" ]; then python3 -m venv "$VENV"; "$VENV/bin/pip" -q install fonttools==4.60.2; fi
WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT
SRC="${2:-$WORK/Unbounded.ttf}"
[ -f "$SRC" ] || curl -fsSL -o "$SRC" "https://raw.githubusercontent.com/google/fonts/$COMMIT/ofl/unbounded/Unbounded%5Bwght%5D.ttf"
GOT="$(shasum -a 256 "$SRC" | cut -d' ' -f1)"; [ "$GOT" = "$WANT" ] || { echo "unexpected font hash $GOT" >&2; exit 1; }
# Static Medium (wght 500) instance, then only the glyphs the wordmark uses.
"$VENV/bin/fonttools" varLib.instancer "$SRC" wght=500 --static -o "$WORK/Unbounded-Medium.ttf"
mkdir -p "$OUT"
"$VENV/bin/pyftsubset" "$WORK/Unbounded-Medium.ttf" \
  --unicodes="U+0020-0021,U+0026-0029,U+002C-002F,U+0030-003A,U+0041-005A,U+0061-007A,U+00B7,U+2019,U+2013-2014" \
  --layout-features='kern' --no-hinting --desubroutinize --name-IDs='*' --notdef-outline \
  --output-file="$OUT/brand_display.ttf"
shasum -a 256 "$OUT/brand_display.ttf"; ls -l "$OUT"
