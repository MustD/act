#!/usr/bin/env bash
# release:desktop: attaches the installer this OS built (.deb, .msi or .dmg) to the GitHub Release already written by
# hand for act.version. Never creates or edits a Release, never replaces an asset. Checks run in a fixed order and each
# fails before anything is uploaded.
# shellcheck source=lib.sh
. "$(dirname "$0")/lib.sh"

command -v gh >/dev/null || { echo "release:desktop needs the GitHub CLI (gh) on PATH." >&2; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "gh is not logged in — run: gh auth login" >&2; exit 1; }

VERSION="$(act_version)"
[[ $VERSION =~ ^[0-9A-Za-z._-]+$ ]] || { echo "Not a version: $VERSION" >&2; exit 1; }

case "$(uname -s)" in
Linux) FORMAT=deb ;;
Darwin) FORMAT=dmg ;;
MINGW* | MSYS* | CYGWIN*) FORMAT=msi ;;
*) echo "No desktop installer format for $(uname -s)." >&2; exit 1 ;;
esac

if [ -n "$(git status --porcelain)" ]; then
	echo "Working tree is dirty. Commit or stash first — a release asset must be built from a commit." >&2
	exit 1
fi

TAGGED="$(git rev-parse -q --verify "refs/tags/$VERSION^{commit}" || true)"
[ -n "$TAGGED" ] || { echo "No tag $VERSION. Tag the release commit first." >&2; exit 1; }
if [ "$TAGGED" != "$(git rev-parse HEAD)" ]; then
	echo "HEAD is not the commit tagged $VERSION ($(git rev-parse --short "$TAGGED")). Check out the tag so the asset matches it." >&2
	exit 1
fi

gh release view "$VERSION" >/dev/null 2>&1 ||
	{ echo "No GitHub Release for $VERSION. Write it by hand first; this task never creates one." >&2; exit 1; }

DIR="app/desktopApp/build/compose/binaries/main/$FORMAT"
shopt -s nullglob
FILES=("$DIR/"*"$VERSION"*."$FORMAT")
[ "${#FILES[@]}" = 1 ] ||
	{ echo "Expected one .$FORMAT for $VERSION in $DIR/, found ${#FILES[@]}. Run: mise run build:desktop:$FORMAT" >&2; exit 1; }
FILE="${FILES[0]}"
NAME="$(basename "$FILE")"

if gh release view "$VERSION" --json assets --jq '.assets[].name' | grep -Fxq "$NAME"; then
	echo "$NAME is already attached to Release $VERSION. Assets are never replaced." >&2
	exit 1
fi

gh release upload "$VERSION" "$FILE"
echo "Attached $NAME to Release $VERSION."
