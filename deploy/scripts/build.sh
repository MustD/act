#!/usr/bin/env bash
# Builds both images for the current act.version. Gradle never sees the deploy env.
# shellcheck source=lib.sh
. "$(dirname "$0")/lib.sh"
# No MAL_CLIENT_ID check: :core:requireMalClientId fails the bundle itself, and also accepts -Pmal.clientId.
require_env DOCKERHUB_USER
VERSION="$(act_version)"

gradle :app:webApp:wasmJsBrowserDistribution :server:installDist

docker build --platform linux/amd64 -f deploy/web.Dockerfile --build-context deploy=deploy \
	-t "$DOCKERHUB_USER/act-web:$VERSION" app/webApp/build/dist/wasmJs/productionExecutable
docker build --platform linux/amd64 -f deploy/relay.Dockerfile \
	-t "$DOCKERHUB_USER/act-relay:$VERSION" server/build/install/server
