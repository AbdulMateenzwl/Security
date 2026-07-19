# syntax=docker/dockerfile:1

# ---- Build stage: compile the Spring Boot fat jar with the Maven wrapper ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

# Copy the wrapper + POM first so the dependency layer is cached independently of source.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Then the sources, and build. The BuildKit cache mount keeps ~/.m2 across builds
# so dependencies are not re-downloaded on every source change.
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests clean package

# ---- Runtime stage: small JRE image, non-root, with a health probe ----
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# curl is used by the container HEALTHCHECK (actuator/health).
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as an unprivileged user.
RUN groupadd --system app && useradd --system --gid app --home-dir /app app

COPY --from=build /build/target/*.jar app.jar
USER app

EXPOSE 8080
ENV JAVA_OPTS=""

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
    CMD curl -fsS http://localhost:8080/actuator/health | grep -q UP || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
