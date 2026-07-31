ARG NODE_IMAGE=swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/node:20-slim
ARG NGINX_IMAGE=swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/nginx:1.27
FROM ${NODE_IMAGE} AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM ${NGINX_IMAGE}
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK CMD wget --quiet --tries=1 --spider http://localhost/ || exit 1
