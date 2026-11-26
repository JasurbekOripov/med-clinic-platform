/**
 * Money is whole UZS (`bigint` on the server, safe JS integers here: far below 2^53).
 * Thousands are grouped with a narrow no-break space ("150 000"), the same in every UI language,
 * so cashiers read amounts the same way whatever language they use.
 */
const GROUP = " ";

export function formatAmount(uzs: number | null | undefined): string {
  if (uzs === null || uzs === undefined || !Number.isFinite(uzs)) return "—";
  const sign = uzs < 0 ? "−" : "";
  return sign + groupDigits(String(Math.trunc(Math.abs(uzs))));
}

/** Digits of a typed amount; empty input is `null` (not zero), so "not filled in" stays distinguishable. */
export function parseAmount(text: string): number | null {
  const digits = text.replace(/\D/g, "").replace(/^0+(?=\d)/, "");
  return digits ? Number(digits) : null;
}

export function groupDigits(digits: string): string {
  return digits.replace(/\B(?=(\d{3})+(?!\d))/g, GROUP);
}

/** Caret position after reformatting: just after the same number of digits as before it. */
export function caretAfterDigits(formatted: string, digitsBefore: number): number {
  if (digitsBefore <= 0) return 0;
  let seen = 0;
  for (let i = 0; i < formatted.length; i++) {
    if (/\d/.test(formatted[i]!)) seen++;
    if (seen === digitsBefore) return i + 1;
  }
  return formatted.length;
}
