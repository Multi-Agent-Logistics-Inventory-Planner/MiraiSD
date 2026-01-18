import { createClient, SupabaseClient } from '@supabase/supabase-js';
import fp from 'fastify-plugin';
import type { FastifyPluginAsync, FastifyRequest, FastifyReply } from 'fastify';
import { env } from '../config/env.js';

declare module 'fastify' {
  interface FastifyInstance {
    supabase: SupabaseClient;
    authenticate: (request: FastifyRequest, reply: FastifyReply) => Promise<void>;
  }
  interface FastifyRequest {
    userId?: string;
    userEmail?: string;
  }
}

const authPlugin: FastifyPluginAsync = async (fastify) => {
  const supabase = createClient(env.SUPABASE_URL!, env.SUPABASE_ANON_KEY!);

  fastify.decorate('supabase', supabase);

  if (env.AUTH_DISABLED) {
    fastify.log.warn('AUTH_DISABLED=true; skipping Supabase auth checks (dev only)');
  }

  fastify.decorate('authenticate', async (request: FastifyRequest, reply: FastifyReply) => {
    // Local/dev escape hatch: skip Supabase token validation.
    // NOTE: do not enable this in production.
    if (env.AUTH_DISABLED) {
      request.userId = request.userId ?? 'dev-user';
      request.userEmail = request.userEmail ?? 'dev@local';
      return;
    }

    const authHeader = request.headers.authorization;

    if (!authHeader || !authHeader.startsWith('Bearer ')) {
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
};

export default fp(authPlugin, {
  name: 'auth',
});
