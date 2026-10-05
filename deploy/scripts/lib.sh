#!/usr/bin/env bash
# shellcheck disable=SC2034
# Sourced by the deploy tasks. Loads .secure.env for this process only and checks what the caller needs.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

ACT_URL="${ACT_URL:-https://act.io-workshop.net}"
REMOTE_DIR=act

load_env() {
	[ -f .secure.env ] || { echo "No .secure.env — copy .secure.env.example and fill it in." >&2; exit 1; }
	set -a
	# shellcheck disable=SC1091
	. ./.secure.env
	set +a
	local v
	for v in "$@"; do
		[ -n "${!v:-}" ] || { echo ".secure.env: $v is empty." >&2; exit 1; }
	done
}

act_version() { grep '^act.version=' gradle.properties | cut -d= -f2; }

droplet_ip() { (cd deploy/terraform && terraform output -raw private_ip); }

# ssh/scp through the edge as a jump host, so a recreated droplet needs no ~/.ssh/config edit.
SSH_OPTS=(-o BatchMode=yes -o StrictHostKeyChecking=accept-new)
remote() { ssh "${SSH_OPTS[@]}" -J "$EDGE_SSH" "deploy@$DROPLET_IP" "$@"; }
remote_copy() { scp "${SSH_OPTS[@]}" -J "$EDGE_SSH" "$@"; }
