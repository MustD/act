#!/usr/bin/env bash
# Turns raw phone captures into the Play Store phone screenshots in screenshots/. Needs ImageMagick (`magick`).
#
#   docs/play-assets/screenshots.sh [raw-dir]     # default: .scratch/screenshots/raw (gitignored)
#
# The raw captures are 1080x2400 from `adb exec-out screencap -p` on the test phone. Play rejects a screenshot whose long
# side is more than twice its short side, and 9:20 is, so each one is cut to 1080x1920 (9:16, the ratio Play wants
# for promotion): the status bar goes (0-92, and the light Theme's scrim under it, to 126), and the 1920 rows below
# it are kept, which drops the navigation bar.
#
# Every cover is blurred: the art belongs to its studio, and other MAL clients blur it in their listings too. The
# rectangles below are measured on those captures; a recapture with a different layout, list or device means measuring
# them again. Samsung's Edge Panel handle (a grey tab on the right edge) is painted out with the column beside it.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
raw="${1:-$root/.scratch/screenshots/raw}"
out="$here/screenshots"
mkdir -p "$out"

# shot <raw name> <output name> [cover WxH+X+Y ...]
shot() {
  local src="$raw/$1.png" dst="$out/$2.png"; shift 2
  local args=()
  for r in "$@"; do args+=(-region "$r" -blur 0x28); done
  magick "$src" \
    \( +clone -crop 1x348+1049+455 +repage -scale 31x348! \) -geometry +1049+455 -composite \
    "${args[@]}" +region \
    -crop 1080x1920+0+126 +repage -strip "$dst"
}

# Cards: four cards, one cover each, 400px apart.
shot cards 01-cards 206x306+67+663 206x306+67+1063 206x306+67+1463 206x306+67+1863
# Anime Page: the cover, and the Related row (the fourth card is clipped by the screen edge).
shot anime-page 02-anime-page 284x428+46+318 274x414+46+1518 274x414+350+1518 274x414+654+1518 124x414+956+1518
# Editing a List Entry: only the page's cover.
shot edit-entry 03-edit-entry 284x428+46+318
# Rows: one thumbnail per row, 211px apart.
shot list 04-rows 106x158+44+621 106x158+44+832 106x158+44+1043 106x158+44+1254 106x158+44+1465 106x158+44+1676 106x158+44+1887
# Table and Welcome Screen: no covers.
shot table 05-table
shot welcome 06-welcome
