import { env } from './config/env.js';
import { buildApp } from './app.js';

async function main() {
  const app = await buildApp();
  let isShuttingDown = false;

  const shutdown = async (signal: 'SIGINT' | 'SIGTERM') => {
    if (isShuttingDown) return;
    isShuttingDown = true;

    app.log.info({ signal }, 'Shutting down Dashboard API...');
    try {
      await app.close(); // triggers onClose hooks (e.g., prisma.$disconnect())
      app.log.info('Shutdown complete');
      process.exit(0);
    } catch (err) {
      app.log.error(err, 'Shutdown failed');
      process.exit(1);
    }
  };

  process.on('SIGINT', () => void shutdown('SIGINT'));
  process.on('SIGTERM', () => void shutdown('SIGTERM'));

  try {
    await app.listen({ port: env.PORT, host: '0.0.0.0' });
    app.log.info(`Dashboard API running on port ${env.PORT}`);
  } catch (err) {
    app.log.error(err);
    process.exit(1);
  }
}

main();
