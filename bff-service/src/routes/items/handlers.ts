import type { FastifyRequest, FastifyReply } from 'fastify';
import { ItemService } from '../../services/item.service.js';
import type { LocationType } from '../../repositories/item.repository.js';

interface GetItemsQuery {
  locationType?: LocationType;
  category?: string;
  includeForecasts?: boolean;
  limit?: number;
  offset?: number;
}

export async function getItems(
  request: FastifyRequest<{ Querystring: GetItemsQuery }>,
  reply: FastifyReply
) {
  const {
    locationType,
    category,
    includeForecasts = false,
    limit = 50,
    offset = 0,
  } = request.query;

  const service = new ItemService(request.server.prisma);
  const result = await service.getItems({
    locationType,
    category,
    includeForecasts,
    limit,
    offset,
  });

  return result;
}
