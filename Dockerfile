# syntax=docker/dockerfile:1.7

# Build stage: dependencies cached in their own layer.
FROM maven:3.9.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package

# Runtime stage
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -S nmi && adduser -S nmi -G nmi && chown -R nmi:nmi /app
USER nmi
COPY --from=build --chown=nmi:nmi /workspace/target/skill-expert-service-*.jar app.jar

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom" \
    SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- "http://localhost:${SERVER_PORT}/actuator/health/liveness" || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
