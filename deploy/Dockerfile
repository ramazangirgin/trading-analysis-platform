# syntax=docker/dockerfile:1
# Platform image: the single jar (backend + UI) on a Java 25 JRE (PLAN.md section 3.9, D12).
# Build the jar first; `mise run docker-build` does both:
#
#   ./gradlew :backend:bootJar
#   docker build -f deploy/Dockerfile --build-arg VERSION="$(scripts/version.sh)" \
#     -t trading-analysis-platform:latest .
#
# The build context is the repository root, cut down by /.dockerignore to the jar alone.

# The jar split into Spring Boot's layers (libraries, loader, snapshot libraries, application code),
# each copied as its own image layer below: a code change rebuilds, pushes and pulls only the small
# application layer, while the ~100 MB of libraries stay cached.
FROM eclipse-temurin:25-jre-noble AS layers
WORKDIR /build
COPY artifact/backend/build/libs/platform.jar platform.jar
RUN java -Djarmode=tools -jar platform.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jre-noble
LABEL org.opencontainers.image.title="trading-analysis-platform" \
      org.opencontainers.image.description="TradingAgents Platform: backend and UI in one jar" \
      org.opencontainers.image.source="https://github.com/ramazangirgin/trading-analysis-platform"
RUN useradd --uid 10002 --user-group --create-home --home-dir /home/platform platform \
    && mkdir -p /data/platform /data/tradingagents \
    && chown platform:platform /data/platform
WORKDIR /app
# Least to most often changed.
COPY --from=layers /build/extracted/dependencies/ ./
COPY --from=layers /build/extracted/spring-boot-loader/ ./
COPY --from=layers /build/extracted/snapshot-dependencies/ ./
COPY --from=layers /build/extracted/application/ ./
# In a container: listen on all interfaces (Compose publishes the port on 127.0.0.1 only), keep
# state under /data, and start analyses as ta-runner containers.
ENV SERVER_ADDRESS=0.0.0.0 \
    PLATFORM_HOME=/data/platform \
    TRADINGAGENTS_HOME=/data/tradingagents \
    PLATFORM_RUNNER=docker
USER platform
WORKDIR /home/platform
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-cp", "/app", "org.springframework.boot.loader.launch.JarLauncher"]
# The platform version (scripts/version.sh), passed by CI and `mise run docker-build`. Last, because a
# changed build argument invalidates every RUN layer after its ARG.
ARG VERSION=unknown
LABEL org.opencontainers.image.version="${VERSION}"
