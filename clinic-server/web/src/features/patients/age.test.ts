import { describe, expect, it } from "vitest";
import { ageOn } from "./age";

describe("ageOn", () => {
  it("counts full years, the birthday itself included", () => {
    expect(ageOn("1985-04-12", "2026-04-11")).toBe(40);
    expect(ageOn("1985-04-12", "2026-04-12")).toBe(41);
    expect(ageOn("2026-11-01", "2026-11-25")).toBe(0);
    expect(ageOn("2000-02-29", "2026-02-28")).toBe(25);
  });
});
