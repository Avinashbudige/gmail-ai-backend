# Stage 1: Build Stage (compile TypeScript to JavaScript)
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json tsconfig.json ./
RUN npm ci
COPY src ./src
RUN npm run build

# Stage 2: Runtime Stage (lightweight production environment)
FROM node:20-alpine AS runner
WORKDIR /app
COPY package*.json ./
# Install only production dependencies (saves container memory)
RUN npm ci --only=production
COPY --from=builder /app/dist ./dist
EXPOSE 3000
CMD ["node", "dist/server.js"]