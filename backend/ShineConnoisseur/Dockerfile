# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw mvnw.cmd ./

RUN --mount=type=cache,target=/root/.m2/repository \
    mvn dependency:go-offline -B -q || true

COPY src src

RUN --mount=type=cache,target=/root/.m2/repository \
    mvn package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache ca-certificates tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

RUN mkdir -p /app/uploads/images/avatar /app/uploads/images/poster /app/uploads/images/movie

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
