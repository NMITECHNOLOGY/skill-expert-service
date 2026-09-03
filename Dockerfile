# syntax=docker/dockerfile:1.7

# Author: Viraj Sachin
# Created: 2026-09-03
# Copyright (c) 2026 NMI Infra Pvt Ltd

FROM maven:3.9.9-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

COPY src src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S nmi && adduser -S nmi -G nmi
USER nmi

COPY --from=build /workspace/target/skill-expert-service.jar app.jar

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_PROFILES_ACTIVE=local

EXPOSE 8089

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8089/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
