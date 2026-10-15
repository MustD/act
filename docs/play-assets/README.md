# Play assets and the ACT icon

`icon.svg` is the one source of every icon: the `>ACT_` wordmark — a shell prompt, the name, the cursor — with ink
letters and a phosphor `>` and `_` on Act's dark background (the colours of `ActTheme.kt`). It deliberately shares
nothing with MAL's blue or logo shape.

All five glyphs are filled paths outlined from IBM Plex Mono SemiBold (the app's own font, OFL), so there is no `<text>`
and no font dependency. They are made by `outline_glyphs.py` (`pip install fonttools`, then
`python3 docs/play-assets/outline_glyphs.py`), which prints the `ink` (`ACT`) and `accent` (`>` and `_`) path data, scaled
together to fit the 66×66 safe zone and centred on the canvas. Paste those into `icon.svg`, `feature-graphic.svg` and the
two Android drawables.

| Derived file | From | How |
|---|---|---|
| `store-icon-512.png`, `feature-graphic-1024x500.png` (this folder, uploaded by hand to Play Console) | `icon.svg`, `feature-graphic.svg` | `regenerate.sh` |
| Android `mipmap-*/ic_launcher{,_round}.png` (API < 26 fallback) | `icon.svg` | `regenerate.sh` |
| Desktop `app/desktopApp/src/main/resources/icon.png` (window) and `app/desktopApp/icons/icon.{png,ico,icns}` (packaging) | `icon.svg` | `regenerate.sh` |
| Web `app/webApp/src/webMain/resources/favicon.svg` | `icon.svg` | `regenerate.sh` (a copy) |
| Android `drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`; the splash icon is the foreground | `icon.svg` | **by hand** — same path data as the SVG, as vector `<path>`s |

`ic_launcher_background.xml` is the solid `#0D0E0C`. `feature-graphic.svg` reuses the glyph markup of `icon.svg`,
so a change to the letters has to be made in both files.

## Store screenshots

`screenshots/` holds the six phone screenshots, 1080×1920 (9:16), in upload order — the shot list is in
`docs/play-listing.md`. They come from raw `adb exec-out screencap -p` captures on the test phone, kept outside git in
`.scratch/screenshots/raw/`; `screenshots.sh` crops away the system bars (a 1080×2400 capture is too tall for Play),
blurs every cover and paints out Samsung's Edge Panel handle. The cover rectangles in it are measured by hand on those
captures, so a recapture means measuring them again.

## Regenerating the icon

To regenerate: run `outline_glyphs.py`, edit `icon.svg` (and the two Android vector drawables to match), then run
`docs/play-assets/regenerate.sh`. It needs `rsvg-convert` and ImageMagick (`magick`); nothing is fetched.
