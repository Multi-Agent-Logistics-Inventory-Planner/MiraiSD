import { describe, it, expect } from "vitest";
import { isRelevantToCurrentSite } from "./site-relevance";

describe("isRelevantToCurrentSite", () => {
  it("treats a missing payload siteId as possibly relevant", () => {
    expect(isRelevantToCurrentSite(undefined, "site-1")).toBe(true);
  });

  it("treats a missing current siteId as possibly relevant", () => {
    expect(isRelevantToCurrentSite("site-1", undefined)).toBe(true);
  });

  it("is relevant when both sites match", () => {
    expect(isRelevantToCurrentSite("site-1", "site-1")).toBe(true);
  });

  it("is not relevant when sites differ", () => {
    expect(isRelevantToCurrentSite("site-2", "site-1")).toBe(false);
  });
});
