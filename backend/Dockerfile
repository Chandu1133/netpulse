# Stage 1: Build Spring Boot JAR with Maven + Java 21
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests clean package

# Stage 2: Minimal Java 21 runtime with traceroute & curl for healthchecks
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache curl traceroute \
    && addgroup -S netpulse \
    && adduser -S netpulse -G netpulse

COPY --from=builder /app/target/*.jar app.jar
RUN chown -R netpulse:netpulse /app

USER netpulse
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=25s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
