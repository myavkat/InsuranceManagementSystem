# Stage 1: Build — uses repo root as context to resolve common:common-message dependency
FROM gradle:9.3.1-jdk25-ubi10 AS build
WORKDIR /app

# Copy root settings (required for multi-project resolution)
COPY settings.gradle.kts ./

# Copy shared libraries
COPY common/ ./common/

# Copy service source
COPY services/estimation-service/ ./services/estimation-service/

# Build the estimation-service (skip tests for smaller image)
RUN gradle :services:estimation-service:build -x test --no-daemon

# Stage 2: Runtime image
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/services/estimation-service/build/libs/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
