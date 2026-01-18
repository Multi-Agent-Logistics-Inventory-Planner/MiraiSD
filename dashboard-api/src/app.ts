import Fastify, { FastifyInstance } from 'fastify';
import cors from '@fastify/cors';
import authPlugin from './plugins/auth.js';
import rateLimitPlugin from './plugins/rate-limit.js';
import errorHandlerPlugin from './plugins/error-handler.js';
import healthRoutes from './routes/health.js';
import forecastRoutes from './routes/forecasts/index.js';
import inventoryRoutes from './routes/inventory/index.js';
import itemRoutes from './routes/items/index.js';

export interface BuildAppOptions {
  /**
   * By default, the app registers the Prisma plugin which connects to the DB.
   * For unit tests, set `registerPrisma=false` and provide a mocked prisma client.
   */
  registerPrisma?: boolean;
  prisma?: unknown;
}

export async function buildApp(options: BuildAppOptions = {}): Promise<FastifyInstance> {
  const fastify = Fastify({
    logger: {
      level: process.env.LOG_LEVEL ?? 'info',
    },
  });

  // Register CORS
  await fastify.register(cors, {
    origin: true,
    credentials: true,
  });

  // Register plugins
  await fastify.register(errorHandlerPlugin);
  await fastify.register(rateLimitPlugin);
  if (options.registerPrisma === false) {
    if (!options.prisma) {
      throw new Error('buildApp(registerPrisma=false) requires options.prisma');
    }
    // Decorate prisma directly (used by route handlers/services).
    // Type is `unknown` here to keep production typing unchanged.
    fastify.decorate('prisma', options.prisma as any);
  } else {
    // Lazy import so unit tests can avoid importing Prisma (which tries to read `.env`)
    // and so we only pay Prisma startup cost when actually registering the plugin.
    const { default: prismaPlugin } = await import('./plugins/prisma.js');
    await fastify.register(prismaPlugin);
  }
  await fastify.register(authPlugin);

  // Register routes
  await fastify.register(healthRoutes);
  await fastify.register(forecastRoutes, { prefix: '/forecasts' });
  await fastify.register(inventoryRoutes, { prefix: '/inventory' });
  await fastify.register(itemRoutes, { prefix: '/items' });

  return fastify;
}
