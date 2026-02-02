# Stage 1: Build
FROM gradle:8.12-jdk21 AS builder
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY server/build.gradle ./server/
COPY server/src ./server/src
COPY ab-testing/build.gradle ./ab-testing/
RUN gradle :server:bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/server/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
