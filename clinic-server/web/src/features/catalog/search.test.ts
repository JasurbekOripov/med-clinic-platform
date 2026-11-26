import { describe, expect, it } from "vitest";
import { matchesSearch } from "./search";

describe("matchesSearch", () => {
  it("matches name or code, ignoring case, spacing and apostrophe variants", () => {
    expect(matchesSearch("", "Anything")).toBe(true);
    expect(matchesSearch("qon", "Umumiy QON tahlili", null)).toBe(true);
    expect(matchesSearch("l-1", "Qon", "L-1")).toBe(true);
    expect(matchesSearch("o'zbek", "Oʻzbek")).toBe(true);
    expect(matchesSearch("koʻz", "Ko'z shifokori")).toBe(true);
    expect(matchesSearch("rentgen", "Qon tahlili", "L1")).toBe(false);
  });
});
