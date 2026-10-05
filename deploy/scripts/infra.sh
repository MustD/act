#!/usr/bin/env bash
# infra.sh plan|apply — Terraform with .secure.env's DIGITALOCEAN_TOKEN and TF_VAR_*. Applied by hand, never by a deploy.
# shellcheck source=lib.sh
. "$(dirname "$0")/lib.sh"
load_env DIGITALOCEAN_TOKEN TF_VAR_edge_private_ip TF_VAR_vpc_name TF_VAR_ssh_public_key
cd deploy/terraform || exit 1
terraform init -input=false
exec terraform "$1"
