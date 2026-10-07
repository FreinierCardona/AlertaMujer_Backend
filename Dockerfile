FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S spring && adduser -S spring -G spring \
    && mkdir -p /var/lib/alertamujer/evidence /run/secrets \
    && chown -R spring:spring /var/lib/alertamujer

WORKDIR /app

COPY --from=build /workspace/target/*.jar /app/app.jar
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint
RUN chmod 0555 /usr/local/bin/docker-entrypoint

USER spring

EXPOSE 8080

ENTRYPOINT ["docker-entrypoint"]
