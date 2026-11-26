import { describe, expect, it } from "vitest";
import { parseDecimal } from "./LabFormEditor";

describe("parseDecimal", () => {
  it("accepts comma or dot, keeps partial input empty", () => {
    expect(parseDecimal("0,5")).toBe(0.5);
    expect(parseDecimal("4.")).toBe(4);
    expect(parseDecimal("12.75")).toBe(12.75);
    expect(parseDecimal("")).toBeNull();
    expect(parseDecimal("-")).toBeNull();
    expect(parseDecimal("1.2.3")).toBeNull();
  });
});
