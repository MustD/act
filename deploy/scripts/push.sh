#!/usr/bin/env bash
# Pushes <act.version> of both images. Tags are immutable: an existing tag is refused, and `latest` is never pushed.
# shellcheck source=lib.sh
. "$(dirname "$0")/lib.sh"
require_env DOCKERHUB_USER
VERSION="$(act_version)"

# Checks both before pushing either, so a refusal never leaves one image pushed and the other not.
for image in act-web act-relay; do
	if tag_exists "$image" "$VERSION"; then
		echo "$DOCKERHUB_USER/$image:$VERSION already exists on Docker Hub. Tags are immutable — bump act.version in" \
			"gradle.properties, or \`mise run deploy $VERSION\` to redeploy it as it is." >&2
		exit 1
	fi
done

for image in act-web act-relay; do
	docker push "$DOCKERHUB_USER/$image:$VERSION"
done
