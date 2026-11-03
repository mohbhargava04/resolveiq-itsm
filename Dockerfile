# Builds the whole app from source - the React frontend and the Spring Boot backend - into one
# small image. Used by docker compose and by cloud hosts such as Render (see render.yaml).
#   docker build -t resolveiq .

# ---- 1. frontend: type-check and bundle the React app ------------------------------------
FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# ---- 2. backend: compile with the Maven wrapper, serving the fresh frontend build ---------
FROM eclipse-temurin:21-jdk AS api
WORKDIR /app
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY backend/src src
RUN rm -rf src/main/resources/static
COPY --from=web /web/dist src/main/resources/static
RUN ./mvnw -B -q package -DskipTests

# ---- 3. runtime: JRE only, unprivileged user ----------------------------------------------
# (the host checks /actuator/health; Render does it via healthCheckPath in render.yaml)
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 resolveiq
COPY --from=api /app/target/resolveiq-backend-*.jar app.jar
USER resolveiq
ENV SPRING_PROFILES_ACTIVE=postgres
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
