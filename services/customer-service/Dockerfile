# Stage 1: Build — uses repo root as context to resolve common:common-message dependency
FROM gradle:9.3.1-jdk25-ubi10 AS build
WORKDIR /app

# Copy root settings (required for multi-project resolution)
COPY settings.gradle.kts ./

# Copy shared libraries
COPY common/ ./common/

# Copy service source
COPY services/customer-service/ ./services/customer-service/

# Build the customer-service (skip tests for smaller image)
RUN gradle :services:customer-service:build -x test --no-daemon

# Stage 2: Runtime image
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/services/customer-service/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
