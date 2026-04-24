# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS builder

ENV GRADLE_VERSION=8.14.4
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip openjdk-17-jdk \
    && rm -rf /var/lib/apt/lists/*
RUN curl -fsSL https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && rm /tmp/gradle.zip

ENV PATH="/opt/gradle-${GRADLE_VERSION}/bin:${PATH}"
ENV GRADLE_OPTS="-Dorg.gradle.java.installations.auto-download=false"

WORKDIR /build
COPY . .
RUN gradle :server:installDist --no-daemon

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /build/server/build/install/server/ .
COPY docker-entrypoint.sh .
RUN chmod +x docker-entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["./docker-entrypoint.sh"]
