# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /build
COPY ${MODULE}/pom.xml pom.xml
COPY ${MODULE}/src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package
RUN cp target/*.jar /app.jar

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app.jar app.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
