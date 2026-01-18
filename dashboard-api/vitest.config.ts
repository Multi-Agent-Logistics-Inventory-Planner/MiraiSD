import { defineConfig } from 'vitest/config';

// Cursor sandboxing can block access to `.env` (often gitignored).
// Point Vite/Vitest env loading at a non-existent directory so tests
// rely on explicit `process.env` values set in test setup instead.
export default defineConfig({
  envDir: './.vitest-env',
  test: {
    environment: 'node',
    // Avoid tinypool thread shutdown issues in some sandboxed environments.
    pool: 'forks',
    poolOptions: {
      forks: {
        singleFork: true,
      },
    },
    sequence: {
      concurrent: false,
    },
  },
});

