# Play assets and the ACT icon

`icon.svg` is the one source of every icon: the `ACT_` wordmark, ink letters and a phosphor underscore cursor on
Act's dark background (the colours of `ActTheme.kt`). It deliberately shares nothing with MAL's blue or logo shape.
It is a placeholder, replaceable before production.

| Derived file | From | How |
|---|---|---|
| `store-icon-512.png`, `feature-graphic-1024x500.png` (this folder, uploaded by hand to Play Console) | `icon.svg`, `feature-graphic.svg` | `regenerate.sh` |
| Android `mipmap-*/ic_launcher{,_round}.png` (API < 26 fallback) | `icon.svg` | `regenerate.sh` |
| Desktop `app/desktopApp/src/main/resources/icon.png` (window) and `app/desktopApp/icons/icon.{png,ico,icns}` (packaging) | `icon.svg` | `regenerate.sh` |
| Web `app/webApp/src/webMain/resources/favicon.svg` | `icon.svg` | `regenerate.sh` (a copy) |
| Android `drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`; the splash icon is the foreground | `icon.svg` | **by hand** — same path data as the SVG, as vector `<path>`s |

`ic_launcher_background.xml` is the solid `#0D0E0C`. `feature-graphic.svg` reuses the glyph markup of `icon.svg`,
so a change to the letters has to be made in both files.

To regenerate: edit `icon.svg` (and the two Android vector drawables to match), then run
`docs/play-assets/regenerate.sh`. It needs `rsvg-convert` and ImageMagick (`magick`); nothing is fetched.
