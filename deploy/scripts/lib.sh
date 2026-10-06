#!/usr/bin/env bash
# shellcheck disable=SC2034
# Sourced by the deploy tasks. Reads what mise loaded and checks what the caller needs.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

ACT_URL="${ACT_URL:-https://act.io-workshop.net}"
REMOTE_DIR=act

# mise loads .secure.build.env / .secure.deploy.env per task (see mise.toml); this only checks that what the caller
# needs arrived, so an unfilled file fails here and not three steps in.
require_env() {
	local v
	for v in "$@"; do
		[ -n "${!v:-}" ] || { echo "$v is empty — set it in .secure.build.env or .secure.deploy.env (run through mise)." >&2; exit 1; }
	done
}

# Gradle with nothing from .secure.deploy.env: the DigitalOcean token, the state bucket's Spaces key, the edge's SSH
# address and every TF_VAR_*.
# The deploy tasks load that file, and Gradle — with its daemon, plugins and build scans — has no use for any of it.
gradle() {
	local strip=(-u DIGITALOCEAN_TOKEN -u AWS_ACCESS_KEY_ID -u AWS_SECRET_ACCESS_KEY -u EDGE_SSH) v
	for v in $(compgen -e); do
		if [[ $v == TF_VAR_* ]]; then strip+=(-u "$v"); fi
	done
	env "${strip[@]}" ./gradlew "$@"
}

act_version() { grep '^act.version=' gradle.properties | cut -d= -f2; }

droplet_ip() { (cd deploy/terraform && terraform output -raw private_ip); }

# ssh/scp through the edge as a jump host, so a recreated droplet needs no ~/.ssh/config edit.
SSH_OPTS=(-o BatchMode=yes -o StrictHostKeyChecking=accept-new)
remote() { ssh "${SSH_OPTS[@]}" -J "$EDGE_SSH" "deploy@$DROPLET_IP" "$@"; }
remote_copy() { scp "${SSH_OPTS[@]}" -J "$EDGE_SSH" "$@"; }
