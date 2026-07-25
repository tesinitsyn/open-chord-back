# syntax=docker/dockerfile:1
FROM maven:3.9.16-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY .mvn/container-settings.xml .mvn/container-settings.xml
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -s .mvn/container-settings.xml -DskipTests package

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl ffmpeg \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 openchord \
    && useradd --system --uid 10001 --gid openchord --home-dir /app openchord \
    && mkdir -p /media \
    && chown openchord:openchord /media
WORKDIR /app
COPY --from=builder /build/target/open-chord-back-0.0.1-SNAPSHOT.jar app.jar
USER openchord

ENV MEDIA_ROOT=/media
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
  CMD ["curl", "--fail", "--silent", "http://127.0.0.1:8080/actuator/health/liveness"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
