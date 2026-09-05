# Build context is the repo root (not apps/web) - apps/web depends on the local,
# unpublished @mirai/api-client package at packages/api-client (see package.json's
# "file:../../packages/api-client" dependency), which must be present alongside
# apps/web for npm to resolve and symlink it, and for Next's standalone output
# tracing (outputFileTracingRoot in next.config.mjs) to include its files.
FROM node:22-alpine AS builder

WORKDIR /app

# Build args for Next.js public env vars (baked into client bundle at build time)
ARG NEXT_PUBLIC_SUPABASE_URL
ARG NEXT_PUBLIC_SUPABASE_ANON_KEY
ARG NEXT_PUBLIC_BACKEND_URL
ARG NEXT_PUBLIC_API_URL
ARG NEXT_PUBLIC_R2_PUBLIC_BASE_URL

ENV NEXT_PUBLIC_SUPABASE_URL=$NEXT_PUBLIC_SUPABASE_URL
ENV NEXT_PUBLIC_SUPABASE_ANON_KEY=$NEXT_PUBLIC_SUPABASE_ANON_KEY
ENV NEXT_PUBLIC_BACKEND_URL=$NEXT_PUBLIC_BACKEND_URL
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_R2_PUBLIC_BASE_URL=$NEXT_PUBLIC_R2_PUBLIC_BASE_URL

# Copy package manifests first for better layer caching - both apps/web and the
# local package it depends on. packages/api-client is not part of an npm workspace,
# so it needs its own `npm ci` too: apps/web's install only symlinks
# node_modules/@mirai/api-client to ../../packages/api-client, it does not hoist
# that package's own dependencies (openapi-fetch) anywhere Node would resolve them
# from packages/api-client's own location.
COPY apps/web/package*.json ./apps/web/
COPY packages/api-client/package*.json ./packages/api-client/

WORKDIR /app/packages/api-client
RUN npm ci

WORKDIR /app/apps/web
RUN npm ci

WORKDIR /app

# Copy source code
COPY apps/web ./apps/web
COPY packages/api-client ./packages/api-client

WORKDIR /app/apps/web

# Build the application
RUN npm run build

FROM node:22-alpine AS runner

WORKDIR /app

# Set production environment
ENV NODE_ENV=production

# npm ships in every official node:* image by default even though this
# stage never invokes it (output: 'standalone' means CMD is just
# `node server.js`) — strip it so a CVE in npm's bundled tooling (e.g.
# CVE-2026-59873 in its vendored tar) can't fail the image scan for a
# binary nothing here ever runs.
RUN rm -rf /usr/local/lib/node_modules/npm /usr/local/bin/npm /usr/local/bin/npx

# Create non-root user
RUN addgroup --system --gid 1001 nodejs && \
    adduser --system --uid 1001 nextjs

# Copy necessary files from builder. outputFileTracingRoot (set to the repo root in
# next.config.mjs) makes the standalone output mirror apps/web's monorepo-relative
# path, so server.js lands at apps/web/server.js, not ./server.js.
COPY --from=builder /app/apps/web/public ./apps/web/public
COPY --from=builder --chown=nextjs:nodejs /app/apps/web/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/apps/web/.next/static ./apps/web/.next/static

# Switch to non-root user
USER nextjs

# Expose port
EXPOSE 3000

# Set environment variables
ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

# Start the application
CMD ["node", "apps/web/server.js"]
