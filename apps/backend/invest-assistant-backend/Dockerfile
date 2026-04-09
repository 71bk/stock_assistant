# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy whole backend project (multi-module Maven build).
COPY . .

# Build executable jar for app-bootstrap only (with required modules).
RUN chmod +x mvnw \
    && ./mvnw -B -DskipTests -pl modules/app-bootstrap -am package \
    && mkdir -p /out \
    && JAR_FILE=$(find modules/app-bootstrap/target -maxdepth 1 -type f -name "*.jar" ! -name "*.original" | head -n 1) \
    && cp "${JAR_FILE}" /out/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /out/app.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
