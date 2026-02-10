import { describe, it, expect } from 'vitest';
import { z } from 'zod';

const passwordSchema = z.string().min(1, "Password is required");

describe('Password Validation', () => {
  it('rejects empty password', () => {
    const result = passwordSchema.safeParse('');
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.errors[0].message).toContain('Password is required');
    }
  });

  it('accepts any non-empty password', () => {
    const passwords = [
      'short',
      'nocapitals123!',
      'NOLOWERCASE123!',
      'NoNumbers!@#',
      'NoSpecialChar123',
      'StrongPassword123!',
      'a',
    ];

    passwords.forEach(password => {
      const result = passwordSchema.safeParse(password);
      expect(result.success).toBe(true);
    });
  });
});
