# syntax=docker/dockerfile:1.7
# Contexto de build: raiz do repositório (para reutilizar infrastructure/certs).

# ---------- Build ----------
FROM eclipse-temurin:25-jdk AS build
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*
COPY infrastructure/certs/ /tmp/extra-ca/
RUN set -e; for c in /tmp/extra-ca/*.crt; do \
      [ -f "$c" ] || continue; \
      cp "$c" /usr/local/share/ca-certificates/; \
      keytool -importcert -noprompt -cacerts -storepass changeit -alias "extra-$(basename "$c")" -file "$c"; \
    done; update-ca-certificates
WORKDIR /workspace
COPY dependency-simulator/.mvn .mvn
COPY dependency-simulator/mvnw dependency-simulator/pom.xml ./
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline || true
COPY dependency-simulator/src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
    && cp target/errorlens-dependency-simulator.jar /workspace/app.jar

# ---------- Runtime ----------
FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --home-dir /app simulator
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
USER simulator
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75" \
    SPRING_PROFILES_ACTIVE=lab
EXPOSE 8090
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=10 \
  CMD curl -fsS http://localhost:8090/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
