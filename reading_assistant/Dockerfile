# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    cp src/main/resources/application.yml.example src/main/resources/application.yml \
    && mvn -B -DskipTests \
        -Daether.connector.http.connectTimeout=60000 \
        -Daether.connector.http.requestTimeout=120000 \
        -Dmaven.wagon.http.retryHandler.count=3 \
        package

FROM eclipse-temurin:17-jre

WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app \
    && mkdir -p /data/http-reading \
    && chown -R app:app /app /data/http-reading

COPY --from=build --chown=app:app /workspace/target/http-reading-*.jar app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
