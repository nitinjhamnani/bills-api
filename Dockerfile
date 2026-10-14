# DigitalOcean App Platform builds this from the repository root.
# docker build -t b2bfintech-api .

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -pl api-app -am clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN useradd --create-home --shell /bin/false appuser
COPY --from=build /workspace/api-app/target/api-app.jar app.jar
RUN chown appuser:appuser /app/app.jar
USER appuser

# App Platform injects PORT (default 8080) and routes traffic there.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
