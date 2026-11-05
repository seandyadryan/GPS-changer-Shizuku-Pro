FROM node:24-alpine
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts --no-audit --no-fund
COPY *.mjs schema.sql ./
USER node
EXPOSE 8080
CMD ["node", "server.mjs"]
