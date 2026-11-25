import { describe, expect, it } from "vitest";
import { formatPhoneInput, isEmptyPhone, isValidUzPhone, nationalDigits } from "./phone";

describe("uz phone input", () => {
  it("formats progressively while typing", () => {
    expect(formatPhoneInput("")).toBe("+998");
    expect(formatPhoneInput("+998 9")).toBe("+998 9");
    expect(formatPhoneInput("+998 901")).toBe("+998 90 1");
    expect(formatPhoneInput("901234567")).toBe("+998 90 123 45 67");
    expect(formatPhoneInput("+998901234567")).toBe("+998 90 123 45 67");
    expect(formatPhoneInput("998 90 123 45 67")).toBe("+998 90 123 45 67");
    expect(formatPhoneInput("(90) 123-45-6789")).toBe("+998 90 123 45 67");
  });

  it("validates exactly 9 national digits", () => {
    expect(isValidUzPhone("+998 90 123 45 67")).toBe(true);
    expect(isValidUzPhone("901234567")).toBe(true);
    expect(isValidUzPhone("+998 90 123 45 6")).toBe(false);
    expect(isValidUzPhone("+998")).toBe(false);
    expect(nationalDigits("+998 99 8")).toBe("998");
  });

  it("treats the bare prefix as empty", () => {
    expect(isEmptyPhone("+998 ")).toBe(true);
    expect(isEmptyPhone(undefined)).toBe(true);
    expect(isEmptyPhone("+998 9")).toBe(false);
  });
});
