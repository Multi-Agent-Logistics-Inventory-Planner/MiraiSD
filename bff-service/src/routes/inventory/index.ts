import type { FastifyPluginAsync } from 'fastify';
import { getSummary } from './handlers.js';

const inventoryRoutes: FastifyPluginAsync = async (fastify) => {
  // All inventory routes require authentication
  fastify.addHook('preHandler', fastify.authenticate);

  // GET /inventory/summary - Get aggregate stats across all inventory tables
  fastify.get('/summary', {
    handler: getSummary,
  });
};

export default inventoryRoutes;
