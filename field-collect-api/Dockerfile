# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 app && mkdir -p /data/documents && chown app /data/documents
WORKDIR /app
COPY --from=build /src/target/field-collect-api-*.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod DOCS_DIR=/data/documents
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
