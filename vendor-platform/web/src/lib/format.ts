import i18n from "@/i18n";

export const TIME_ZONE = "Asia/Tashkent";

const INTL_LOCALE: Record<string, string> = {
  "uz-Latn": "uz-Latn-UZ",
  "uz-Cyrl": "uz-Cyrl-UZ",
  ru: "ru-RU",
  en: "en-GB",
};

function intlLocale(): string {
  return INTL_LOCALE[i18n.language] ?? "uz-Latn-UZ";
}

/** Instants are stored in UTC and always displayed in Tashkent time, whatever the browser's zone is. */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  return new Intl.DateTimeFormat(intlLocale(), {
    timeZone: TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(iso));
}

/** Calendar dates (`yyyy-MM-dd`) have no time zone; format them without shifting. */
export function formatDate(isoDate: string | null | undefined): string {
  if (!isoDate) return "—";
  const [y, m, d] = isoDate.split("-").map(Number);
  if (!y || !m || !d) return isoDate;
  return new Intl.DateTimeFormat(intlLocale(), { timeZone: "UTC", year: "numeric", month: "2-digit", day: "2-digit" }).format(
    new Date(Date.UTC(y, m - 1, d)),
  );
}

/** Today's calendar date in Tashkent as `yyyy-MM-dd` (for date inputs). */
export function todayInTashkent(now: Date = new Date()): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: TIME_ZONE, year: "numeric", month: "2-digit", day: "2-digit" }).format(now);
}

export function addYears(isoDate: string, years: number): string {
  const [y, m, d] = isoDate.split("-").map(Number);
  const date = new Date(Date.UTC((y ?? 2000) + years, (m ?? 1) - 1, d ?? 1));
  date.setUTCDate(date.getUTCDate() - 1);
  return date.toISOString().slice(0, 10);
}

/** `+998901234567` → `+998 90 123 45 67`; anything unexpected is shown unchanged. */
export function formatPhone(e164: string | null | undefined): string {
  if (!e164) return "—";
  const m = /^\+998(\d{2})(\d{3})(\d{2})(\d{2})$/.exec(e164);
  return m ? `+998 ${m[1]} ${m[2]} ${m[3]} ${m[4]}` : e164;
}

const UNITS = ["B", "KB", "MB", "GB", "TB"] as const;

export function formatBytes(bytes: number | null | undefined): string {
  if (bytes == null) return "—";
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < UNITS.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value.toFixed(value >= 100 || unit === 0 ? 0 : 1)} ${UNITS[unit]}`;
}

/** Compact relative age, e.g. "3 min" / "2 h" / "5 d", localised through i18n. */
export function formatAge(iso: string | null | undefined, now: number = Date.now()): string {
  if (!iso) return "—";
  const seconds = Math.max(0, Math.round((now - new Date(iso).getTime()) / 1000));
  if (seconds < 60) return i18n.t("time.seconds", { count: seconds });
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return i18n.t("time.minutes", { count: minutes });
  const hours = Math.round(minutes / 60);
  if (hours < 48) return i18n.t("time.hours", { count: hours });
  return i18n.t("time.days", { count: Math.round(hours / 24) });
}

export function formatUptime(seconds: number | null | undefined): string {
  if (seconds == null) return "—";
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  return days > 0 ? `${i18n.t("time.days", { count: days })} ${i18n.t("time.hours", { count: hours })}` : i18n.t("time.hours", { count: hours });
}
