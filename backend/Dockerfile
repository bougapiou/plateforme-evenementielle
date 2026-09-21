# --- build ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B clean package -DskipTests

# --- runtime ---
FROM eclipse-temurin:21-jre-alpine
# UID/GID fixes : le dossier de stockage monté depuis l'hôte doit leur appartenir
# (chown -R 10001:10001, voir infra/README-HEBERGEMENT.md).
RUN addgroup -S -g 10001 plateforme && adduser -S -u 10001 -G plateforme plateforme
WORKDIR /app
COPY --from=build /app/target/plateforme.jar app.jar
RUN mkdir -p /app/storage && chown -R plateforme:plateforme /app
USER plateforme
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
