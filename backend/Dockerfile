# --- build ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B clean package -DskipTests

# --- runtime ---
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S plateforme && adduser -S plateforme -G plateforme
WORKDIR /app
COPY --from=build /app/target/plateforme.jar app.jar
RUN mkdir -p /app/storage && chown -R plateforme:plateforme /app
USER plateforme
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
