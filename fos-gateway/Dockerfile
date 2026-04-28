# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

COPY . .
RUN mvn --batch-mode --no-transfer-progress -pl fos-gateway -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

ENV JAVA_OPTS="" \
    SPRING_PROFILES_ACTIVE=staging

COPY --from=builder /workspace/fos-gateway/target/fos-gateway-0.1.0-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app/app.jar"]
