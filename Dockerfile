# syntax=docker/dockerfile:1.7
# The pinned index digests are the current official Java-21 images checked on 2026-07-31.
FROM maven:3.9.16-eclipse-temurin-21@sha256:2b4496088e7b80ae10a8c9f74e574ea21380325a006ec684532ad6bad5bc7273 AS build

WORKDIR /workspace
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn --batch-mode --no-transfer-progress -DskipTests clean package

FROM eclipse-temurin:21-jre@sha256:273396ed5998598ed1091e8d72711c2d36980a0e65103859c55a4e977a41ffd3 AS runtime

ARG VCS_REF=unknown
ARG VERSION=1.0.0
ARG BUILD_DATE=unknown

LABEL org.opencontainers.image.title="gridwords-bot" \
      org.opencontainers.image.description="GridWords and QuadWords Discord bot" \
      org.opencontainers.image.source="https://github.com/venomenon328/gridwords-bot" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.created="${BUILD_DATE}"

# curl is part of the pinned Temurin runtime image and is required by the container healthcheck.
RUN command -v curl >/dev/null \
    && groupadd --system --gid 10001 gridwords \
    && useradd --system --uid 10001 --gid gridwords --home-dir /app --shell /usr/sbin/nologin gridwords

WORKDIR /app
COPY --from=build --chown=gridwords:gridwords /workspace/target/gridwords-bot-*.jar /app/gridwords-bot.jar

USER gridwords:gridwords

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -XX:InitialRAMPercentage=25.0 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE="production,database"

HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=8 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8081/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "/app/gridwords-bot.jar"]
