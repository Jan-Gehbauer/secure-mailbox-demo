# Stage 1: Build
FROM node:22-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# Stage 2: Runtime (nginx liefert die fertig gebauten statischen Dateien aus)
FROM nginx:1.27-alpine

# Passe den Pfad ggf. an: "ng build" legt den Output standardmässig unter
# dist/<projektname>/browser ab (Angular 17+ mit application builder).
# Falls dein Ordnername anders ist als "frontend", hier anpassen.
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

COPY docker/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
