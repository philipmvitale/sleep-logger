FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY sleep/build.gradle sleep/gradlew sleep/settings.gradle ./
COPY sleep/gradle/ gradle/

RUN ./gradlew dependencies --no-daemon

COPY .git/ .git/
COPY sleep/src/ src/

RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]
