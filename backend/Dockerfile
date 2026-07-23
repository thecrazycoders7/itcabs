# Build on JDK 21 (Spring Boot 3.3 supported line), run on a JRE.
FROM gradle:8.10.2-jdk21 AS build
WORKDIR /src
COPY . .
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre
# curl is used by the compose/orchestrator healthcheck against /actuator/health.
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
