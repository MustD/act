#!/usr/bin/env bash
# deploy [version]: no argument builds, pushes and deploys the current act.version; a version redeploys that tag as it is
# on Docker Hub and skips the tests, build and push — a rollback, or the roll forward after one, which is why the
# current act.version is accepted too. Never rolls back by itself.
# shellcheck source=lib.sh
. "$(dirname "$0")/lib.sh"
require_env DOCKERHUB_USER EDGE_SSH TF_VAR_edge_private_ip AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY
# Terraform is never run from here; `terraform output` reads the S3 state with the Spaces key alone, so nothing below
# needs the token.
unset DIGITALOCEAN_TOKEN

# Whether an argument was given, not whether it equals act.version: `deploy <act.version>` after a rollback must
# redeploy the pushed tag, and building it again would only be refused by push.sh.
REDEPLOY=0
[ $# = 0 ] || REDEPLOY=1
VERSION="${1:-$(act_version)}"
# It ends up in shell strings run on the droplet and in its .env, so it must be a plain tag and nothing else.
[[ $VERSION =~ ^[0-9A-Za-z._-]+$ ]] || { echo "Not a version: $VERSION" >&2; exit 1; }

if [ "$REDEPLOY" = 1 ]; then
	# Before the droplet is touched, so a mistyped or never-pushed version fails here and not as a failed pull.
	for image in act-web act-relay; do
		tag_exists "$image" "$VERSION" || { echo "$DOCKERHUB_USER/$image:$VERSION is not on Docker Hub." >&2; exit 1; }
	done
else
	if [ -n "$(git status --porcelain)" ]; then
		echo "Working tree is dirty. Commit or stash first — an image tag must name a commit." >&2
		exit 1
	fi
	gradle :server:test :app:shared:wasmJsTest
	"$ROOT/deploy/scripts/build.sh"
	"$ROOT/deploy/scripts/push.sh"
fi

DROPLET_IP="$(droplet_ip)"
PREVIOUS=""
rollback_hint() {
	echo >&2
	echo "Deploy of $VERSION failed. Nothing was rolled back." >&2
	if [ -n "$PREVIOUS" ] && [ "$PREVIOUS" != "$VERSION" ]; then
		echo "To go back:  mise run deploy $PREVIOUS" >&2
	else
		echo "There is no previous version recorded on the droplet to go back to." >&2
	fi
}
# shellcheck disable=SC2154
trap 'rc=$?; [ "$rc" = 0 ] || rollback_hint' EXIT

PREVIOUS="$(remote "cat $REMOTE_DIR/.env 2>/dev/null | sed -n 's/^ACT_VERSION=//p'" || true)"

remote "mkdir -p $REMOTE_DIR"
remote_copy deploy/docker-compose.yml "deploy@$DROPLET_IP:$REMOTE_DIR/docker-compose.yml"
remote "cat > $REMOTE_DIR/.env" <<ENV
ACT_VERSION=$VERSION
DOCKERHUB_USER=$DOCKERHUB_USER
PRIVATE_IP=$DROPLET_IP
EDGE_PRIVATE_IP=$TF_VAR_edge_private_ip
ENV

remote "cd $REMOTE_DIR && docker compose pull && docker compose up -d --remove-orphans"

echo "Waiting for both containers to report healthy..."
for _ in $(seq 1 40); do
	# `|| true`: a container not up yet makes docker inspect fail, which is a reason to wait, not to stop.
	states="$(remote "cd $REMOTE_DIR && for s in relay web; do docker inspect --format '{{.State.Health.Status}}' \$(docker compose ps -q \$s); done" | tr '\n' ' ' || true)"
	[ "$states" = "healthy healthy " ] && break
	sleep 3
done
[ "$states" = "healthy healthy " ] || { echo "Not healthy after 2 minutes: $states" >&2; exit 1; }

# Keep this version's images and the previous one's, so a rollback does not need a pull.
KEEP=""
for image in act-web act-relay; do
	for tag in "$VERSION" "${PREVIOUS:-$VERSION}"; do
		KEEP="$KEEP -e $DOCKERHUB_USER/$image:$tag"
	done
done
remote "docker image ls --format '{{.Repository}}:{{.Tag}}' | grep -E '^$DOCKERHUB_USER/act-(web|relay):' \\
	| grep -vxF $KEEP | xargs -r docker image rm" || true

echo "Smoke test through the edge:"
"$ROOT/deploy/scripts/smoke.sh"
echo "Deployed $VERSION."
