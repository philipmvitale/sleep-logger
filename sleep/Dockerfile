FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

COPY sleep/build.gradle.kts sleep/gradlew sleep/settings.gradle.kts ./
COPY sleep/gradle/ gradle/

RUN ./gradlew dependencies --no-daemon

COPY .git/ .git/
COPY sleep/src/ src/

RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]
