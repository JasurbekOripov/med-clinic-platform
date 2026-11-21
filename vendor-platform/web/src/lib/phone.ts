/**
 * Uzbekistan phone input helpers. Mirrors the server rule (UzPhone): `+998` followed by 9 digits; the
 * 9-digit national number alone is accepted too. Operator codes are not hard-coded (they change over time).
 */

const COUNTRY = "998";
const NATIONAL_LENGTH = 9;

/** The 9 national digits typed so far (country code and formatting stripped), at most 9. */
export function nationalDigits(input: string): string {
  let digits = input.replace(/\D/g, "");
  if (digits.startsWith(COUNTRY) && (digits.length > NATIONAL_LENGTH || input.trim().startsWith("+"))) {
    digits = digits.slice(COUNTRY.length);
  }
  return digits.slice(0, NATIONAL_LENGTH);
}

/** Live formatting while typing: `+998 90 123 45 67`. */
export function formatPhoneInput(input: string): string {
  const d = nationalDigits(input);
  const groups = [d.slice(0, 2), d.slice(2, 5), d.slice(5, 7), d.slice(7, 9)].filter(Boolean);
  return ["+998", ...groups].join(" ");
}

export function isValidUzPhone(input: string): boolean {
  return nationalDigits(input).length === NATIONAL_LENGTH;
}

/** True when the field holds nothing but the prefilled country code. */
export function isEmptyPhone(input: string | null | undefined): boolean {
  return !input || nationalDigits(input).length === 0;
}
