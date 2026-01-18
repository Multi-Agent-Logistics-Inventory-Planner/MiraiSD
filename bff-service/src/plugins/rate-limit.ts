import rateLimit from '@fastify/rate-limit';
import fp from 'fastify-plugin';
import type { FastifyPluginAsync } from 'fastify';
import { env } from '../config/env.js';

const rateLimitPlugin: FastifyPluginAsync = async (fastify) => {
  await fastify.register(rateLimit, {
    max: env.RATE_LIMIT_MAX,
    timeWindow: env.RATE_LIMIT_WINDOW,
    errorResponseBuilder: (request, context) => ({
      timestamp: new Date().toISOString(),
      status: 429,
      error: 'Too Many Requests',
      message: `Rate limit exceeded. Try again in ${Math.ceil(context.ttl / 1000)} seconds.`,
    }),
  });
};

export default fp(rateLimitPlugin, {
  name: 'rate-limit',
});
