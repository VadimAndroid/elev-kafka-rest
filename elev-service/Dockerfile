# syntax=docker/dockerfile:1

# ---- Stage 1: build the jar with the Gradle wrapper ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Copy the build files first, so Docker can cache the dependency download
# as long as only the source code changes.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew dependencies --no-daemon -q > /dev/null

COPY src src
# Tests are run in CI, not in the image build.
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon -q \
 && cp build/libs/*-SNAPSHOT.jar app.jar

# ---- Stage 2: small runtime image with only the JRE and the jar ----
FROM eclipse-temurin:25-jre
WORKDIR /app

# curl is used by the healthcheck in docker-compose.yaml.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Do not run the application as root.
RUN useradd --system --uid 1001 spring
USER spring

COPY --from=build /app/app.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
