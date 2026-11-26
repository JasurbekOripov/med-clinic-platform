import { describe, expect, it } from "vitest";
import { flagMark, liveFlag } from "./flags";

/** Arbitrary test bounds, not medical values. */
const row = { normalLow: 100, normalHigh: 200, criticalLow: 60, criticalHigh: 250 };

describe("liveFlag", () => {
  it("checks critical limits first, then the normal range", () => {
    expect(liveFlag(row, null)).toBeNull();
    expect(liveFlag(row, 150)).toBe("NORMAL");
    expect(liveFlag(row, 99)).toBe("LOW");
    expect(liveFlag(row, 201)).toBe("HIGH");
    expect(liveFlag(row, 59)).toBe("CRITICAL_LOW");
    expect(liveFlag(row, 251)).toBe("CRITICAL_HIGH");
    expect(liveFlag(row, 100)).toBe("NORMAL");
  });

  it("one-sided and missing ranges", () => {
    expect(liveFlag({ normalLow: null, normalHigh: 5.7, criticalLow: null, criticalHigh: null }, 6)).toBe("HIGH");
    expect(liveFlag({ normalLow: null, normalHigh: null, criticalLow: null, criticalHigh: null }, 6)).toBe("NO_RANGE");
    expect(flagMark("CRITICAL_HIGH")).toBe("↑↑");
  });
});
