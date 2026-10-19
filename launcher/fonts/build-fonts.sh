set -e
S=$(pwd); W=/Users/k2a/Desktop/Dev/tv-fonts/launcher/app/src/main
UNI="U+0020-007E,U+00A0-00FF,U+0100-017F,U+2010-2015,U+2018-201E,U+2020-2022,U+2026,U+2030,U+2032-2033,U+20AC,U+20B9,U+2122,U+2190-2193,U+2212,U+25B2,U+25BC,U+2605,U+2713"
for p in 300:light 400:regular 500:medium 600:semibold 700:bold; do
 w=${p%%:*}; n=${p#*:}
 venv/bin/fonttools varLib.instancer dl/manrope.ttf wght=$w -o out/inst_$n.ttf --update-name-table -q
 venv/bin/pyftsubset out/inst_$n.ttf --unicodes="$UNI" --layout-features='kern,liga,calt,tnum,pnum,case,locl,ccmp,mark,mkmk' --no-hinting --desubroutinize --name-IDs='*' --notdef-outline --output-file=$W/assets/fonts/ui_$n.ttf
done
