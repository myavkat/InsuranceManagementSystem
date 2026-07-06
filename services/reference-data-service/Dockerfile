# Stage 1: Build — uses repo root as context to resolve common:common-message and common:common-web dependencies
FROM gradle:9.3.1-jdk25-ubi10 AS build
WORKDIR /app

# Copy root settings (required for multi-project resolution)
COPY settings.gradle.kts ./

# Copy shared libraries
COPY common/ ./common/

# Copy service source
COPY services/reference-data-service/ ./services/reference-data-service/

# Build the reference-data-service (skip tests for smaller image)
RUN gradle :services:reference-data-service:build -x test --no-daemon

# Stage 2: Runtime image
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/services/reference-data-service/build/libs/*.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-Xmx256m", "-Xms128m", "-jar", "app.jar"]
