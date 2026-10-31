# syntax=docker/dockerfile:1.7
# Contexto de build: raiz do repositório.

# ---------- Build ----------
FROM node:24-alpine AS build
COPY infrastructure/certs/ /tmp/extra-ca/
RUN cat /tmp/extra-ca/*.crt > /tmp/extra-ca.pem 2>/dev/null || true
ENV NODE_EXTRA_CA_CERTS=/tmp/extra-ca.pem
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npx ng build --configuration production

# ---------- Runtime ----------
FROM nginx:1.29-alpine
COPY infrastructure/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/errorlens-frontend/browser /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=6 \
  CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1
