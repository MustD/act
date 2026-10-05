# The act-web image: Caddy, the production Wasm bundle, privacy.html and the Caddyfile.
#
# Build context is the bundle, app/webApp/build/dist/wasmJs/productionExecutable, produced by
# `./gradlew :app:webApp:wasmJsBrowserDistribution` (which refuses to run without a Client ID). The Caddyfile comes
# in as a named context so it need not live inside the bundle:
#
#   docker build --platform linux/amd64 -f deploy/web.Dockerfile --build-context deploy=deploy \
#       -t "$DOCKERHUB_USER/act-web:$ACT_VERSION" app/webApp/build/dist/wasmJs/productionExecutable
#
# Runtime env: EDGE_PRIVATE_IP (required), RELAY_UPSTREAM (default relay:18010).
FROM caddy:2-alpine

COPY --chown=root:root . /srv/
# Source maps are not shipped: they cover only the JS glue (the Wasm has none), and the Caddyfile answers .map with 404.
RUN find /srv -name '*.map' -delete && chmod -R a+rX /srv
COPY --from=deploy --chown=root:root Caddyfile /etc/caddy/Caddyfile

# Port 80 stays bindable unprivileged: the caddy binary carries cap_net_bind_service. Caddy's state dirs must be
# writable by the non-root user.
ENV XDG_DATA_HOME=/tmp/caddy-data XDG_CONFIG_HOME=/tmp/caddy-config
USER 10001
EXPOSE 80

HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1

CMD ["caddy", "run", "--config", "/etc/caddy/Caddyfile"]
