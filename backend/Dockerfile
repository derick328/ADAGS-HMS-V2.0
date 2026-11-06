FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
COPY src ./src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system adags && useradd --system --gid adags adags
COPY --from=build /app/build/libs/*.jar /app/app.jar
USER adags
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
