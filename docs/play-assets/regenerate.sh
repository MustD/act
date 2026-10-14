#!/usr/bin/env bash
# Rebuilds every raster derived from icon.svg / feature-graphic.svg. Needs rsvg-convert and ImageMagick.
# The Android vector drawables (ic_launcher_foreground, ic_launcher_monochrome, splash) are NOT generated:
# they carry the same path data as icon.svg and are edited by hand when it changes.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
res="$root/app/androidApp/src/main/res"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT

rsvg-convert -w 512 -h 512 "$here/icon.svg" -o "$here/store-icon-512.png"
rsvg-convert "$here/feature-graphic.svg" -o "$here/feature-graphic-1024x500.png"

# Android < 26 ignores adaptive icons: the launcher icon is the middle of the canvas, per density.
# The adaptive canvas is 108dp and the visible part 72dp, so crop the middle 72/108.
for d in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
  dir="mipmap-${d%%:*}"; px="${d##*:}"
  rsvg-convert -w $((px * 3 / 2)) -h $((px * 3 / 2)) "$here/icon.svg" -o "$tmp/full.png"
  magick "$tmp/full.png" -gravity center -crop "${px}x${px}+0+0" +repage "$res/$dir/ic_launcher.png"
  magick "$res/$dir/ic_launcher.png" \( -size "${px}x${px}" xc:none -fill white -draw "circle $((px/2)),$((px/2)) $((px/2)),0" \) \
    -alpha set -compose DstIn -composite "$res/$dir/ic_launcher_round.png"
done

# Desktop: window icon, and the packager's per-OS files.
desk="$root/app/desktopApp"
mkdir -p "$desk/src/main/resources" "$desk/icons"
rsvg-convert -w 512 -h 512 "$here/icon.svg" -o "$desk/src/main/resources/icon.png"
cp "$desk/src/main/resources/icon.png" "$desk/icons/icon.png"
magick "$desk/icons/icon.png" -define icon:auto-resize=256,128,64,48,32,16 "$desk/icons/icon.ico"
magick "$desk/icons/icon.png" "$desk/icons/icon.icns"

# Web favicon: the SVG itself, served as-is.
cp "$here/icon.svg" "$root/app/webApp/src/webMain/resources/favicon.svg"
