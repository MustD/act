#!/usr/bin/env bash
# release:aur [pkgrel] / release:aur:local [deb] [pkgrel]: renders packaging/aur/PKGBUILD.in for act.version around the
# .deb attached to its GitHub Release, builds it with makepkg as a check and, unless local, pushes it to the AUR.
# Local mode does everything but clone and push, from a .deb on disk or else the Release asset.
# shellcheck source=lib.sh
. "$(dirname "$0")/lib.sh"

MODE="${1:?usage: release-aur.sh push [pkgrel] | local [deb] [pkgrel]}"
shift
[[ $MODE == push || $MODE == local ]] || { echo "Unknown mode '$MODE' (push or local)." >&2; exit 1; }
DEB_ARG=""
if [ "$MODE" = local ] && [[ ${1:-} == *.deb ]]; then DEB_ARG="$1"; shift; fi
PKGREL="${1:-1}"

# gh only fetches the Release's .deb, dpkg-deb only reads a local one's version, vercmp only orders the AUR's versions.
TOOLS=(makepkg namcap)
if [ -n "$DEB_ARG" ]; then TOOLS+=(dpkg-deb); else TOOLS+=(gh); fi
[ "$MODE" = push ] && TOOLS+=(vercmp)
for t in "${TOOLS[@]}"; do
	command -v "$t" >/dev/null ||
		{ echo "release:aur needs $t on PATH (makepkg, vercmp: pacman; namcap: pacman -S namcap; dpkg-deb: pacman -S dpkg)." >&2; exit 1; }
done
[[ $PKGREL =~ ^[1-9][0-9]*$ ]] || { echo "pkgrel must be a positive integer, got: $PKGREL" >&2; exit 1; }

VERSION="$(act_version)"
[[ $VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Not a plain version: $VERSION" >&2; exit 1; }
DEB="io-workshop-act_${VERSION}_amd64.deb"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
BUILD="$WORK/build"
mkdir "$BUILD"

if [ -n "$DEB_ARG" ]; then
	[ -f "$DEB_ARG" ] || { echo "No such file: $DEB_ARG" >&2; exit 1; }
	DEB_VERSION="$(dpkg-deb -f "$DEB_ARG" Version 2>/dev/null || true)"
	[ "$DEB_VERSION" = "$VERSION" ] ||
		{ echo "$DEB_ARG is version '$DEB_VERSION', act.version is $VERSION. Build it again: mise run build:desktop:deb" >&2; exit 1; }
	cp "$DEB_ARG" "$BUILD/$DEB"
else
	gh auth status >/dev/null 2>&1 || { echo "gh is not logged in — run: gh auth login" >&2; exit 1; }
	gh release download "$VERSION" --repo MustD/act --pattern "$DEB" --dir "$BUILD" 2>/dev/null ||
		{ echo "Release $VERSION has no $DEB. Run release:desktop first (or pass a local .deb to release:aur:local)." >&2; exit 1; }
fi

# The checksum is taken from the very file makepkg will build from, which is what users download.
SHA="$(sha256sum "$BUILD/$DEB" | cut -d' ' -f1)"
# The licence travels in the AUR repo beside the PKGBUILD, so it needs no Release asset of its own.
cp "$ROOT/LICENSE" "$BUILD/LICENSE"
LICENSE_SHA="$(sha256sum "$BUILD/LICENSE" | cut -d' ' -f1)"
sed -e "s/@PKGVER@/$VERSION/" -e "s/@PKGREL@/$PKGREL/" -e "s/@SHA256@/$SHA/" -e "s/@LICENSE_SHA256@/$LICENSE_SHA/" \
	packaging/aur/PKGBUILD.in >"$BUILD/PKGBUILD"

(
	cd "$BUILD"
	makepkg --printsrcinfo >.SRCINFO
	# The .deb is already beside the PKGBUILD, so makepkg uses it instead of downloading, and still verifies the sum.
	makepkg --force --nodeps --cleanbuild
	namcap PKGBUILD ./*.pkg.tar.* || true
)

if [ "$MODE" = local ]; then
	OUT="$ROOT/packaging/aur/build"
	rm -rf "$OUT"
	mkdir -p "$OUT"
	cp "$BUILD/PKGBUILD" "$BUILD/.SRCINFO" "$BUILD/LICENSE" "$BUILD"/*.pkg.tar.* "$OUT/"
	echo "Built $OUT/ — not pushed. Check: namcap output above, then pacman -U $OUT/*.pkg.tar.*"
	exit 0
fi

REPO="$WORK/aur"
git clone ssh://aur@aur.archlinux.org/io-workshop-act-bin.git "$REPO"
if [ -f "$REPO/.SRCINFO" ]; then
	PUBLISHED="$(awk -F' = ' '$1 ~ /^[[:space:]]*pkgver$/ {v=$2} $1 ~ /^[[:space:]]*pkgrel$/ {r=$2} END {print v "-" r}' "$REPO/.SRCINFO")"
	if [ "$(vercmp "$VERSION-$PKGREL" "$PUBLISHED")" -le 0 ]; then
		echo "The AUR already has io-workshop-act-bin $PUBLISHED; $VERSION-$PKGREL is not newer. Pass a higher pkgrel; versions are never pushed twice or backwards." >&2
		exit 1
	fi
fi
cp "$BUILD/PKGBUILD" "$BUILD/.SRCINFO" "$BUILD/LICENSE" "$REPO/"
git -C "$REPO" add PKGBUILD .SRCINFO LICENSE
git -C "$REPO" commit -m "io-workshop-act-bin $VERSION-$PKGREL"
git -C "$REPO" push origin HEAD:master
echo "Pushed io-workshop-act-bin $VERSION-$PKGREL to the AUR."
