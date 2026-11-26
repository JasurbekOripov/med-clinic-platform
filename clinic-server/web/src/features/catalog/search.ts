/** Case- and apostrophe-insensitive "contains" for short client-side lists (price list, discounts). */
export function matchesSearch(query: string, ...fields: (string | null | undefined)[]): boolean {
  const q = fold(query);
  if (!q) return true;
  return fields.some((f) => f != null && fold(f).includes(q));
}

// Uzbek apostrophe variants (ʻ ʼ ' ` ‘ ’) are typed inconsistently; ignore them all.
function fold(text: string): string {
  return text.toLocaleLowerCase().replace(/[ʻʼ'`‘’]/g, "").replace(/\s+/g, " ").trim();
}
