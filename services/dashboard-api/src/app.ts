import Fastify, { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import cors from '@fastify/cors';
import { env } from './config/env.js';
import { prisma } from './lib/prisma.js';
import { supabase } from './lib/supabase.js';
import healthRoutes from './routes/health.js';
import forecastRoutes from './routes/forecasts.js';
import inventoryRoutes from './routes/inventory.js';
import itemRoutes from './routes/items.js';

declare module 'fastify' {
  interface FastifyInstance {
    authenticate: (request: FastifyRequest, reply: FastifyReply) => Promise<void>;
  }
  interface FastifyRequest {
    userId?: string;
    userEmail?: string;
  }
}

export async function buildApp(): Promise<FastifyInstance> {
  const fastify = Fastify({
    logger: {
      level: process.env.LOG_LEVEL ?? 'info',
    },
  });

  // Graceful shutdown: ensure Prisma connection pool is closed when Fastify closes.
  fastify.addHook('onClose', async (instance) => {
    instance.log.info('Disconnecting Prisma client...');
    // In tests, prisma may be mocked; guard to avoid crashing on shutdown.
    if (typeof (prisma as any)?.$disconnect === 'function') {
      await (prisma as any).$disconnect();
    } else {
      instance.log.warn('Prisma client has no $disconnect(); skipping disconnect');
    }
  });

  // CORS
  await fastify.register(cors, {
    origin: true,
    credentials: true,
  });

  // Error handler
  fastify.setErrorHandler((error: Error & { statusCode?: number }, request, reply) => {
    request.log.error(error);
    const statusCode = error.statusCode ?? 500;
    reply.status(statusCode).send({
      timestamp: new Date().toISOString(),
      status: statusCode,
      error: error.name ?? 'Internal Server Error',
      message: error.message ?? 'An unexpected error occurred',
    });
  });

  // Auth decorator
  if (env.AUTH_DISABLED) {
    fastify.log.warn('AUTH_DISABLED=true; skipping Supabase auth checks (dev only)');
  }

  fastify.decorate('authenticate', async (request: FastifyRequest, reply: FastifyReply) => {
    if (env.AUTH_DISABLED) {
      request.userId = 'dev-user';
      request.userEmail = 'dev@local';
      return;
    }

    const authHeader = request.headers.authorization;
    if (!authHeader?.startsWith('Bearer ')) {
      return reply.status(401).send({
        timestamp: new Date().toISOString(),
        status: 401,
        error: 'Unauthorized',
        message: 'Missing or invalid authorization header',
      });
    }

    const token = authHeader.substring(7);
    const { data: { user }, error } = await supabase.auth.getUser(token);

    if (error || !user) {
      return reply.status(401).send({
        timestamp: new Date().toISOString(),
        status: 401,
        error: 'Unauthorized',
        message: 'Invalid or expired token',
      });
    }

    request.userId = user.id;
    request.userEmail = user.email;
  });

  // Routes
  await fastify.register(healthRoutes);
  await fastify.register(forecastRoutes, { prefix: '/forecasts' });
  await fastify.register(inventoryRoutes, { prefix: '/inventory' });
  await fastify.register(itemRoutes, { prefix: '/items' });

  return fastify;
}
