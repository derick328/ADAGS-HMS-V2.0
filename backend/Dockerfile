FROM gradle:8.10.2-jdk21-jammy AS build
USER root
WORKDIR /app
ENV GRADLE_USER_HOME=/app/.gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src
RUN --mount=type=cache,target=/app/.gradle gradle --no-daemon bootJar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system adags && useradd --system --gid adags adags
COPY --from=build /app/build/libs/*.jar /app/app.jar
USER adags
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
