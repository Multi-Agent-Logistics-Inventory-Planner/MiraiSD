import type { FastifyPluginAsync } from 'fastify';
import { getForecastByItemId, getAtRiskForecasts } from './handlers.js';
import { getForecastByItemIdSchema, getAtRiskForecastsSchema } from './schemas.js';

const forecastRoutes: FastifyPluginAsync = async (fastify) => {
  // All forecast routes require authentication
  fastify.addHook('preHandler', fastify.authenticate);

  // GET /forecasts/at-risk - Get items at risk of stockout
  fastify.get('/at-risk', {
    schema: getAtRiskForecastsSchema,
    handler: getAtRiskForecasts,
  });

  // GET /forecasts/:itemId - Get forecast for a specific item
  fastify.get('/:itemId', {
    schema: getForecastByItemIdSchema,
    handler: getForecastByItemId,
  });
};

export default forecastRoutes;
