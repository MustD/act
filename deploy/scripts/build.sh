#!/usr/bin/env bash
# Builds both images for the current act.version. Gradle sees MAL_CLIENT_ID and nothing else from .secure.env.
# shellcheck source=lib.sh
. "$(dirname "$0")/lib.sh"
load_env MAL_CLIENT_ID DOCKERHUB_USER
VERSION="$(act_version)"

env -u DIGITALOCEAN_TOKEN -u EDGE_SSH \
	./gradlew :app:webApp:wasmJsBrowserDistribution :server:installDist

docker build --platform linux/amd64 -f deploy/web.Dockerfile --build-context deploy=deploy \
	-t "$DOCKERHUB_USER/act-web:$VERSION" app/webApp/build/dist/wasmJs/productionExecutable
docker build --platform linux/amd64 -f deploy/relay.Dockerfile \
	-t "$DOCKERHUB_USER/act-relay:$VERSION" server/build/install/server
