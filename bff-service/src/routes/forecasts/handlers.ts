import type { FastifyRequest, FastifyReply } from 'fastify';
import { ForecastService } from '../../services/forecast.service.js';

interface GetForecastParams {
  itemId: string;
}

interface GetAtRiskQuery {
  threshold?: number;
  limit?: number;
  offset?: number;
}

export async function getForecastByItemId(
  request: FastifyRequest<{ Params: GetForecastParams }>,
  reply: FastifyReply
) {
  const service = new ForecastService(request.server.prisma);
  const forecast = await service.getLatestForecast(request.params.itemId);

  if (!forecast) {
    return reply.status(404).send({
      timestamp: new Date().toISOString(),
      status: 404,
      error: 'Not Found',
      message: `Forecast not found for item ${request.params.itemId}`,
    });
  }

  return forecast;
}

export async function getAtRiskForecasts(
  request: FastifyRequest<{ Querystring: GetAtRiskQuery }>,
  reply: FastifyReply
) {
  const { threshold = 7, limit = 50, offset = 0 } = request.query;

  const service = new ForecastService(request.server.prisma);
  const result = await service.getAtRiskForecasts(threshold, limit, offset);

  return result;
}
