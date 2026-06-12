# ── Stage 1: Build frontend ──
FROM node:22-alpine AS frontend-build
WORKDIR /app/frontend
COPY trade_j_frontend/package.json trade_j_frontend/package-lock.json ./
RUN npm ci
COPY trade_j_frontend/ ./
RUN npx vite build

# ── Stage 2: Build backend ──
FROM gradle:8.12-jdk21 AS backend-build
WORKDIR /app
COPY --chown=gradle:gradle . .
COPY --from=frontend-build /app/frontend/dist app/src/main/resources/static/console
RUN gradle :app:bootJar --no-daemon -x test -x check

# ── Stage 3: Runtime ──
FROM eclipse-temurin:21-jre-alpine
LABEL maintainer="Trade-J Team"
LABEL description="Trade-J Algorithmic Trading Platform"

RUN addgroup -S tradej && adduser -S tradej -G tradej

WORKDIR /opt/tradej

COPY --from=backend-build /app/app/build/libs/trade-j-app.jar app.jar

# Data directories
RUN mkdir -p /opt/tradej/data /opt/tradej/runtime /opt/tradej/logs \
    && chown -R tradej:tradej /opt/tradej

USER tradej

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

# JVM tuning for containerized environments
ENV JAVA_OPTS="-XX:+UseZGC \
    -XX:+ZGenerational \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseContainerSupport \
    -XX:+ExitOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
