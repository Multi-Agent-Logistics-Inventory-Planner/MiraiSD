import fp from 'fastify-plugin';
import type { FastifyPluginAsync, FastifyError } from 'fastify';

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
}

const errorHandlerPlugin: FastifyPluginAsync = async (fastify) => {
  fastify.setErrorHandler((error: FastifyError, request, reply) => {
    const statusCode = error.statusCode ?? 500;

    const errorNames: Record<number, string> = {
      400: 'Bad Request',
      401: 'Unauthorized',
      403: 'Forbidden',
      404: 'Not Found',
      409: 'Conflict',
      422: 'Unprocessable Entity',
      429: 'Too Many Requests',
      500: 'Internal Server Error',
    };

    const response: ApiError = {
      timestamp: new Date().toISOString(),
      status: statusCode,
      error: errorNames[statusCode] ?? 'Error',
      message: statusCode >= 500 ? 'An unexpected error occurred' : error.message,
    };

    if (statusCode >= 500) {
      fastify.log.error(error);
    }

    return reply.status(statusCode).send(response);
  });

  fastify.setNotFoundHandler((request, reply) => {
    return reply.status(404).send({
      timestamp: new Date().toISOString(),
      status: 404,
      error: 'Not Found',
      message: `Route ${request.method} ${request.url} not found`,
    });
  });
};

export default fp(errorHandlerPlugin, {
  name: 'error-handler',
});
