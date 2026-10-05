# The act-relay image: :server's installDist on a slim JRE.
#
# Build context is server/build/install/server, produced by `./gradlew :server:installDist`. The jars are
# built on the host rather than in a multi-stage build because `deploy` has already run Gradle there (tests,
# the web bundle), so a builder stage would re-download the toolchain and the dependency graph into a cold
# layer cache for the same jars, and would need the whole repo in the context.
#
#   docker build --platform linux/amd64 -f deploy/relay.Dockerfile \
#       -t "$DOCKERHUB_USER/act-relay:$ACT_VERSION" server/build/install/server
#
# Started by the generated start script, not by mainClass, so renaming the Kotlin package touches nothing here.
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S -g 10001 relay && adduser -S -u 10001 -G relay -H -s /sbin/nologin relay

WORKDIR /opt/relay
COPY --chown=root:root bin/ bin/
COPY --chown=root:root lib/ lib/
# Gradle keeps some jars at 0600 from its cache, which the non-root user then cannot read.
RUN chmod 0755 bin/server && chmod -R a+rX lib

# Sized for a 1 GB droplet: the heap takes 60% of whatever memory limit Compose sets (mem_limit), leaving the
# rest for metaspace, Netty's direct buffers and thread stacks.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError" \
    ACT_RELAY_HOST=0.0.0.0 \
    ACT_RELAY_PORT=18010

USER relay
EXPOSE 18010

HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -q -O - "http://127.0.0.1:${ACT_RELAY_PORT}/" | grep -q '^mal_ui relay$' || exit 1

ENTRYPOINT ["/opt/relay/bin/server"]
