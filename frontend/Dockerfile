# syntax=docker/dockerfile:1

# ---- Build stage: produce the optimized Angular production bundle ----
FROM node:22-alpine AS build
WORKDIR /app

# Install dependencies from the lockfile first (cached unless the lockfile changes).
COPY package.json package-lock.json ./
RUN npm ci

# Build the app. Default Angular output is dist/frontend/browser.
COPY . .
RUN npm run build

# ---- Runtime stage: serve the static SPA with nginx + reverse-proxy the API ----
FROM nginx:1.27-alpine AS runtime

# Our server config: SPA history fallback + /api and /ws proxied to the backend.
COPY nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --retries=6 \
    CMD wget -qO- http://localhost/ >/dev/null 2>&1 || exit 1
