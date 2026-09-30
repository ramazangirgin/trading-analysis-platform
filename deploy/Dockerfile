# Platform image: the single jar (backend + UI) on a Java 25 JRE (PLAN.md section 3.9, D12).
# Build the jar first; `mise run docker-build` does both:
#
#   ./gradlew :backend:bootJar
#   docker build -f deploy/Dockerfile -t trading-analysis-platform:0.0.1 .
#
# The build context is the repository root, cut down by /.dockerignore to the jar alone.

FROM eclipse-temurin:25-jre-noble
LABEL org.opencontainers.image.title="trading-analysis-platform" \
      org.opencontainers.image.description="TradingAgents Platform: backend and UI in one jar" \
      org.opencontainers.image.source="https://github.com/ramazangirgin/trading-analysis-platform"
RUN useradd --uid 10002 --user-group --create-home --home-dir /home/platform platform \
    && mkdir -p /data/platform /data/tradingagents \
    && chown platform:platform /data/platform
COPY artifact/backend/build/libs/backend-0.0.1-SNAPSHOT.jar /app/platform.jar
# In a container: listen on all interfaces (Compose publishes the port on 127.0.0.1 only), keep
# state under /data, and start analyses as ta-runner containers.
ENV SERVER_ADDRESS=0.0.0.0 \
    PLATFORM_HOME=/data/platform \
    TRADINGAGENTS_HOME=/data/tradingagents \
    PLATFORM_RUNNER=docker
USER platform
WORKDIR /home/platform
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/platform.jar"]
