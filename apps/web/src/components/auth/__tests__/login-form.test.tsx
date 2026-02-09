import { describe, it, expect } from 'vitest';
import { z } from 'zod';

const passwordSchema = z
  .string()
  .min(12, "Password must be at least 12 characters")
  .regex(/[A-Z]/, "Password must contain at least one uppercase letter")
  .regex(/[a-z]/, "Password must contain at least one lowercase letter")
  .regex(/[0-9]/, "Password must contain at least one number")
  .regex(/[^A-Za-z0-9]/, "Password must contain at least one special character");

describe('Password Validation', () => {
  it('rejects password shorter than 12 characters', () => {
    const result = passwordSchema.safeParse('Short1!');
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.errors[0].message).toContain('at least 12 characters');
    }
  });

  it('rejects password without uppercase letter', () => {
    const result = passwordSchema.safeParse('nocapitals123!');
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.errors[0].message).toContain('uppercase letter');
    }
  });

  it('rejects password without lowercase letter', () => {
    const result = passwordSchema.safeParse('NOLOWERCASE123!');
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.errors[0].message).toContain('lowercase letter');
    }
  });

  it('rejects password without number', () => {
    const result = passwordSchema.safeParse('NoNumbers!@#');
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.errors[0].message).toContain('number');
    }
  });

  it('rejects password without special character', () => {
    const result = passwordSchema.safeParse('NoSpecialChar123');
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.errors[0].message).toContain('special character');
    }
  });

  it('accepts strong password with all requirements', () => {
    const result = passwordSchema.safeParse('StrongPassword123!');
    expect(result.success).toBe(true);
  });

  it('accepts password with various special characters', () => {
    const passwords = [
      'ValidPass123!',
      'ValidPass123@',
      'ValidPass123#',
      'ValidPass123$',
      'ValidPass123%',
    ];

    passwords.forEach(password => {
      const result = passwordSchema.safeParse(password);
      expect(result.success).toBe(true);
    });
  });
});
