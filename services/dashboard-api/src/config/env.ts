import 'dotenv/config';
import { z, type RefinementCtx } from 'zod';

const envSchemaBase = z.object({
  DATABASE_URL: z.string().url(),

  // Local/dev escape hatch: when true, skips Supabase token validation
  AUTH_DISABLED: z.coerce.boolean().default(false),

  // Required unless AUTH_DISABLED=true
  SUPABASE_URL: z.string().url().optional(),
  SUPABASE_ANON_KEY: z.string().min(1).optional(),
  SUPABASE_JWT_SECRET: z.string().min(1).optional(),

  PORT: z.coerce.number().int().positive().default(5001),
});

const envSchema = envSchemaBase.superRefine((val: z.infer<typeof envSchemaBase>, ctx: RefinementCtx) => {
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
    return {
      ...result.data,
      SUPABASE_URL: result.data.SUPABASE_URL ?? 'http://localhost',
      SUPABASE_ANON_KEY: result.data.SUPABASE_ANON_KEY ?? 'dev-anon-key',
      SUPABASE_JWT_SECRET: result.data.SUPABASE_JWT_SECRET ?? 'dev-jwt-secret',
    };
  }

  return result.data as Env;
}

export const env = loadEnv();
