#!/usr/bin/env bash
# Pushes <act.version> of both images. Tags are immutable: an existing tag is refused, and `latest` is never pushed.
# shellcheck source=lib.sh
. "$(dirname "$0")/lib.sh"
require_env DOCKERHUB_USER
VERSION="$(act_version)"

# Checks both before pushing either, so a refusal never leaves one image pushed and the other not.
for image in act-web act-relay; do
	ref="$DOCKERHUB_USER/$image:$VERSION"
	if out="$(docker manifest inspect "$ref" 2>&1)"; then
		echo "$ref already exists on Docker Hub. Tags are immutable — bump act.version in gradle.properties." >&2
		exit 1
	elif ! grep -qiE 'no such manifest|not found|manifest unknown' <<<"$out"; then
		echo "Could not check whether $ref exists (not logged in? offline?):" >&2
		echo "$out" >&2
		exit 1
	fi
done

for image in act-web act-relay; do
	docker push "$DOCKERHUB_USER/$image:$VERSION"
done
