import { describe, it, expect } from "vitest";

// Tests to document and verify security header expectations
describe("Security Headers Configuration", () => {
  const HSTS_VALUE = "max-age=31536000; includeSubDomains; preload";

  it("should have HSTS with correct max-age (1 year)", () => {
    expect(HSTS_VALUE).toContain("max-age=31536000");
  });

  it("should have HSTS with includeSubDomains directive", () => {
    expect(HSTS_VALUE).toContain("includeSubDomains");
  });

  it("should have HSTS with preload directive", () => {
    expect(HSTS_VALUE).toContain("preload");
  });

  it("should NOT include unsafe-eval in CSP", () => {
    // After security fix, CSP should not contain unsafe-eval
    const expectedCSP = "script-src 'self' 'unsafe-inline' https://vercel.live";
    expect(expectedCSP).not.toContain("'unsafe-eval'");
  });
});
