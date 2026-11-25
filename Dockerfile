# Single-container production Dockerfile (ideal for Render Free Tier: $0/month)
# Builds the React + TypeScript frontend and bundles it into Spring Boot's static assets
# so UI, REST API, Swagger UI, and WebSockets (/ws/telemetry) all run on one origin.

# Stage 1: Build Frontend SPA
FROM node:22-alpine AS frontend-builder
WORKDIR /frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# Stage 2: Build Spring Boot Backend with bundled frontend static files
FROM maven:3.9.9-eclipse-temurin-21-alpine AS backend-builder
WORKDIR /backend

COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY backend/src ./src
COPY --from=frontend-builder /frontend/dist ./src/main/resources/static

RUN mvn -B -DskipTests clean package

# Stage 3: Minimal Java 21 Runtime tuned for 512MB free-tier containers
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache curl traceroute \
    && addgroup -S netpulse \
    && adduser -S netpulse -G netpulse

COPY --from=backend-builder /backend/target/*.jar app.jar
RUN chown -R netpulse:netpulse /app

USER netpulse
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=dev
EXPOSE 8080

HEALTHCHECK --interval=20s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -fsS http://localhost:${PORT}/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=65.0 -XX:+UseSerialGC -jar /app/app.jar"]
