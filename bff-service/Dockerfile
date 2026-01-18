FROM node:20-alpine AS builder

WORKDIR /app

# Copy package files
COPY package*.json ./
COPY prisma ./prisma/

# Install dependencies and generate Prisma client
RUN npm ci && npx prisma generate

# Copy source files
COPY tsconfig.json ./
COPY src ./src/

# Build TypeScript
RUN npm run build

# Production stage
FROM node:20-alpine

WORKDIR /app

# Copy node_modules with Prisma client
COPY --from=builder /app/node_modules ./node_modules

# Copy built files
COPY --from=builder /app/dist ./dist

# Copy Prisma schema (needed at runtime)
COPY --from=builder /app/prisma ./prisma

EXPOSE 5001

CMD ["node", "dist/index.js"]
