#!/usr/bin/env bash
# What is running on the droplet.
# shellcheck source=lib.sh
. "$(dirname "$0")/lib.sh"
require_env EDGE_SSH AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY
unset DIGITALOCEAN_TOKEN # `terraform output` reads the S3 state with the Spaces key alone
DROPLET_IP="$(droplet_ip)"
remote "cd $REMOTE_DIR && grep -E '^(ACT_VERSION)=' .env && docker compose ps"
