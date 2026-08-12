# ---- Build stage: lint + test + bundle ----------------------------------
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci
COPY . .
RUN npm run lint && npm test && npm run build

# ---- Runtime stage: static files over nginx ------------------------------
FROM nginx:1.25-alpine
COPY --from=build /app/dist/{{repoName}}/browser /usr/share/nginx/html
EXPOSE 80