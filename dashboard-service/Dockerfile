# ── Stage 1: Build Next.js static export ─────────────────────────────────────
FROM node:20-alpine AS frontend-build
WORKDIR /app/frontend

COPY frontend/package.json ./
RUN npm install

COPY frontend/ .
RUN npm run build
# Output: /app/frontend/out/


# ── Stage 2: Build Spring Boot jar ───────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS backend-build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src/ src/

# Embed the Next.js static export as Spring Boot static resources
COPY --from=frontend-build /app/frontend/out/ src/main/resources/static/

RUN mvn package -DskipTests -q


# ── Stage 3: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=backend-build /app/target/dashboard-service-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
