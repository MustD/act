#!/usr/bin/env bash
# What is running on the droplet.
# shellcheck source=lib.sh
. "$(dirname "$0")/lib.sh"
require_env EDGE_SSH
DROPLET_IP="$(droplet_ip)"
remote "cd $REMOTE_DIR && grep -E '^(ACT_VERSION)=' .env && docker compose ps"
