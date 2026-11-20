import { describe, expect, it } from "vitest";
import { addYears, formatBytes, formatPhone, todayInTashkent } from "./format";

describe("format", () => {
  it("uses the Tashkent calendar day, not the browser's", () => {
    expect(todayInTashkent(new Date("2026-11-20T18:59:00Z"))).toBe("2026-11-20");
    expect(todayInTashkent(new Date("2026-11-20T19:00:00Z"))).toBe("2026-11-21");
  });

  it("one year of license ends the day before the anniversary, including leap days", () => {
    expect(addYears("2026-11-20", 1)).toBe("2027-11-19");
    expect(addYears("2028-03-01", 1)).toBe("2029-02-28");
    expect(addYears("2027-03-01", 1)).toBe("2028-02-29");
  });

  it("formats Uzbek phone numbers for reading", () => {
    expect(formatPhone("+998901234567")).toBe("+998 90 123 45 67");
    expect(formatPhone("+71234567890")).toBe("+71234567890");
    expect(formatPhone(null)).toBe("—");
  });

  it("formats bytes", () => {
    expect(formatBytes(512)).toBe("512 B");
    expect(formatBytes(5 * 1024 ** 3)).toBe("5.0 GB");
    expect(formatBytes(210 * 1024 ** 3)).toBe("210 GB");
    expect(formatBytes(null)).toBe("—");
  });
});
