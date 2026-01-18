import type { FastifyPluginAsync } from 'fastify';
import { getItems } from './handlers.js';
import { getItemsSchema } from './schemas.js';

const itemRoutes: FastifyPluginAsync = async (fastify) => {
  // All item routes require authentication
  fastify.addHook('preHandler', fastify.authenticate);

  // GET /items - List items with qty + forecast status
  fastify.get('/', {
    schema: getItemsSchema,
    handler: getItems,
  });
};

export default itemRoutes;
