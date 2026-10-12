FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /build
COPY backend-v2/pom.xml ./pom.xml
RUN mvn --batch-mode dependency:go-offline
COPY backend-v2/src ./src
RUN mvn --batch-mode package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app/backend-v2
COPY --from=build /build/target/transport-backend-v2-2.0.1.jar ./app.jar
COPY *.html /app/site/
COPY css /app/site/css
COPY js /app/site/js
COPY images /app/site/images
COPY backend/admin /app/admin

ENV APP_SITE_ROOT=/app/site \
    APP_ADMIN_ROOT=/app/admin

EXPOSE 3001
USER 10001
ENTRYPOINT ["java", "-jar", "/app/backend-v2/app.jar"]
