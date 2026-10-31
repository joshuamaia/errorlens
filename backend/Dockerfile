# syntax=docker/dockerfile:1.7
# Contexto de build: raiz do repositório (o backend empacota a pasta examples/).

# ---------- Build ----------
FROM eclipse-temurin:25-jdk AS build
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*
# CAs extras opcionais (proxy corporativo/antivírus com inspeção TLS) - somente no build
COPY infrastructure/certs/ /tmp/extra-ca/
RUN set -e; for c in /tmp/extra-ca/*.crt; do \
      [ -f "$c" ] || continue; \
      cp "$c" /usr/local/share/ca-certificates/; \
      keytool -importcert -noprompt -cacerts -storepass changeit -alias "extra-$(basename "$c")" -file "$c"; \
    done; update-ca-certificates
WORKDIR /workspace/backend
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline -DexcludeReactor=true || true
COPY backend/src src
COPY examples /workspace/examples
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
    && cp target/errorlens-backend.jar /workspace/app.jar

# ---------- Runtime ----------
FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --home-dir /app errorlens
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
USER errorlens
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=lab
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=10 \
  CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
