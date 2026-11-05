# syntax=docker/dockerfile:1
# ---------------------------------------------------------------------------
# UniTrade container image: build with Maven, run on a small Java 21 runtime.
#   docker build -t unitrade .
#   docker run -p 8080:8080 -e DB_URL=... -e DB_USERNAME=... -e DB_PASSWORD=... unitrade
# ---------------------------------------------------------------------------

# ---- Stage 1: build the jar ------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Download dependencies first so they are cached between builds.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests need a MySQL database, so they run in CI (.github/workflows/ci.yml), not here.
RUN mvn -B -q package -DskipTests && cp target/unitrade-*.jar /workspace/app.jar

# ---- Stage 2: runtime --------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as an unprivileged user. /data is where uploaded photos are stored (mount a volume there).
RUN groupadd --system unitrade && useradd --system --gid unitrade --home /app unitrade \
    && mkdir -p /data/uploads && chown -R unitrade:unitrade /data /app
COPY --from=build --chown=unitrade:unitrade /workspace/app.jar /app/app.jar
USER unitrade

ENV SPRING_PROFILES_ACTIVE=prod \
    UPLOAD_DIR=/data/uploads \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# The platform sets PORT; 8080 is the default (see application.properties).
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
