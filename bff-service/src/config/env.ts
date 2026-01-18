import 'dotenv/config';
import { z } from 'zod';

const envSchema = z.object({
  DATABASE_URL: z.string().url(),

  /**
   * Local/dev escape hatch: when true, BFF skips Supabase token validation.
   * Do NOT enable in production.
   */
  AUTH_DISABLED: z.coerce.boolean().default(false),

  // Required unless AUTH_DISABLED=true
  SUPABASE_URL: z.string().url().optional(),
  SUPABASE_ANON_KEY: z.string().min(1).optional(),
  SUPABASE_JWT_SECRET: z.string().min(1).optional(),

  RATE_LIMIT_MAX: z.coerce.number().int().positive().default(100),
  RATE_LIMIT_WINDOW: z.coerce.number().int().positive().default(60000),
  PORT: z.coerce.number().int().positive().default(5001),
}).superRefine((val, ctx) => {
  if (val.AUTH_DISABLED) return;

  if (!val.SUPABASE_URL) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['SUPABASE_URL'], message: 'Required unless AUTH_DISABLED=true' });
  }
  if (!val.SUPABASE_ANON_KEY) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['SUPABASE_ANON_KEY'], message: 'Required unless AUTH_DISABLED=true' });
  }
  if (!val.SUPABASE_JWT_SECRET) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['SUPABASE_JWT_SECRET'], message: 'Required unless AUTH_DISABLED=true' });
  }
});

export type Env = z.infer<typeof envSchema>;

function loadEnv(): Env {
  const result = envSchema.safeParse(process.env);

  if (!result.success) {
    console.error('Invalid environment variables:');
    console.error(result.error.format());
    process.exit(1);
  }

  if (result.data.AUTH_DISABLED) {
    // Provide placeholders so other modules can rely on keys being present.
    return {
      ...result.data,
      SUPABASE_URL: result.data.SUPABASE_URL ?? 'http://localhost',
      SUPABASE_ANON_KEY: result.data.SUPABASE_ANON_KEY ?? 'dev-anon-key',
      SUPABASE_JWT_SECRET: result.data.SUPABASE_JWT_SECRET ?? 'dev-jwt-secret',
    };
  }

  // At this point, superRefine ensures these exist.
  return result.data as Env;
}

export const env = loadEnv();
