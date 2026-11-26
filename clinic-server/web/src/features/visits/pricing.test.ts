import { describe, expect, it } from "vitest";
import { discountOf, priceFor } from "./pricing";

const consultation = { price: 150000, perDoctor: true, doctorPrices: [{ staffId: "d1", price: 200000 }] };

describe("visit pricing preview", () => {
  it("uses the doctor's own price only for per-doctor services", () => {
    expect(priceFor(consultation, "d1")).toBe(200000);
    expect(priceFor(consultation, "d2")).toBe(150000);
    expect(priceFor(consultation, undefined)).toBe(150000);
    expect(priceFor({ ...consultation, perDoctor: false }, "d1")).toBe(150000);
  });

  it("rounds the discount half up to whole sum like the server", () => {
    expect(discountOf(150000, 20)).toBe(30000);
    expect(discountOf(12345, 20)).toBe(2469);
    expect(discountOf(12345, 10)).toBe(1235);
    expect(discountOf(12344, 10)).toBe(1234);
    expect(discountOf(5000, undefined)).toBe(0);
  });
});
