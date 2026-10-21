# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src/main ./src/main
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -Dmaven.test.skip=true package
RUN cp target/*.jar /app.jar

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app.jar app.jar
ENV SERVER_ADDRESS=0.0.0.0 SERVER_PORT=8086 JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
USER 10001:10001
EXPOSE 8086
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
    CMD curl --fail --silent "http://127.0.0.1:${SERVER_PORT}/actuator/health" >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
