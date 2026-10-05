FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /build
COPY ${MODULE}/pom.xml pom.xml
COPY ${MODULE}/src src
RUN mvn -B -ntp -DskipTests package
RUN cp target/*.jar /app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app.jar app.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
