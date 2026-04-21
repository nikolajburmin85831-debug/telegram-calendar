FROM gradle:8.10-jdk21 AS build
WORKDIR /src

COPY . .
RUN gradle :app-ec2:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /src/app-ec2/build/libs/*.jar /app/app.jar

RUN useradd -r -u 10001 appuser
USER appuser

ENTRYPOINT ["java", "-jar", "/app/app.jar"]