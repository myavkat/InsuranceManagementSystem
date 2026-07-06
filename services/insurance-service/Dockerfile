FROM eclipse-temurin:25-jre-alpine
COPY build/libs/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-Xmx256m", "-Xms128m", "-jar", "/app.jar"]
