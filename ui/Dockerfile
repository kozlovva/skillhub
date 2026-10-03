FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_API_URL=
ARG VITE_OIDC_URL=http://localhost:8180
ARG VITE_OIDC_REALM=skillhub
ARG VITE_OIDC_CLIENT_ID=skillhub-ui
RUN npm run build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
