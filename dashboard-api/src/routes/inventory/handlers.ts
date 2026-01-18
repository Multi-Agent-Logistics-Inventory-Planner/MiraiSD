import type { FastifyRequest, FastifyReply } from 'fastify';
import { InventoryService } from '../../services/inventory.service.js';

export async function getSummary(request: FastifyRequest, reply: FastifyReply) {
  const service = new InventoryService(request.server.prisma);
  const summary = await service.getSummary();
  return summary;
}
