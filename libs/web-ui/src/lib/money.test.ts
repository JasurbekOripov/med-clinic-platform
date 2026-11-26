import { describe, expect, it } from "vitest";
import { caretAfterDigits, formatAmount, parseAmount } from "./money";

const nb = (s: string) => s.replace(/ /g, " ");

describe("money", () => {
  it("groups thousands", () => {
    expect(formatAmount(0)).toBe("0");
    expect(formatAmount(950)).toBe("950");
    expect(formatAmount(150000)).toBe(nb("150 000"));
    expect(formatAmount(1234567)).toBe(nb("1 234 567"));
    expect(formatAmount(-5000)).toBe(nb("−5 000"));
    expect(formatAmount(null)).toBe("—");
  });

  it("parses typed or pasted amounts, empty is null", () => {
    expect(parseAmount("")).toBeNull();
    expect(parseAmount("abc")).toBeNull();
    expect(parseAmount("150 000 soʻm")).toBe(150000);
    expect(parseAmount("007")).toBe(7);
    expect(parseAmount("0")).toBe(0);
  });

  it("keeps the caret after the same digit when spaces move", () => {
    expect(caretAfterDigits(nb("150 000"), 4)).toBe(5);
    expect(caretAfterDigits(nb("1 500"), 1)).toBe(1);
    expect(caretAfterDigits(nb("1 500"), 0)).toBe(0);
    expect(caretAfterDigits("15", 9)).toBe(2);
  });
});
