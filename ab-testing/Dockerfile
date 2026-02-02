# Stage 1: Build
FROM gradle:8.12-jdk21 AS builder
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY ab-testing/build.gradle ./ab-testing/
COPY ab-testing/src ./ab-testing/src
COPY server/build.gradle ./server/
RUN gradle :ab-testing:bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/ab-testing/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
