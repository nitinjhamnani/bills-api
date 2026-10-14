# Multi-stage build for the whole Maven reactor, producing a single runnable image for api-app.
# Build from the repo root: docker build -t b2bfintech-api .

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Copy every module's pom.xml first so Docker can cache the dependency resolution layer
# independently of source changes.
COPY pom.xml .
COPY common/pom.xml common/
COPY db-migration/pom.xml db-migration/
COPY notification-core/pom.xml notification-core/
COPY billing-core/pom.xml billing-core/
COPY wallet-core/pom.xml wallet-core/
COPY platform-module/pom.xml platform-module/
COPY company-module/pom.xml company-module/
COPY agency-module/pom.xml agency-module/
COPY api-app/pom.xml api-app/

COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -q -pl api-app -am clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN useradd --create-home --shell /bin/false appuser
COPY --from=build /workspace/api-app/target/api-app.jar app.jar
RUN mkdir -p /app/data/documents && chown -R appuser:appuser /app
USER appuser

EXPOSE 8081
# All the same env vars the local application.yml reads: DB_USERNAME, DB_PASSWORD, JWT_SECRET,
# DOCUMENTS_STORAGE_DIR, etc. - point DB_* / spring.datasource.url at Cloud SQL for GCP (Phase 5).
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
