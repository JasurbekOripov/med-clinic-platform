import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { PhoneInput } from "./ui/input";

describe("PhoneInput", () => {
  it("formats national digits as they are typed and can be cleared", async () => {
    render(<PhoneInput aria-label="phone" />);
    const input = screen.getByLabelText("phone") as HTMLInputElement;

    await userEvent.type(input, "901234567");
    expect(input.value).toBe("+998 90 123 45 67");

    await userEvent.clear(input);
    expect(input.value).toBe("");

    await userEvent.type(input, "+998 99 812 34 56");
    expect(input.value).toBe("+998 99 812 34 56");
  });
});
