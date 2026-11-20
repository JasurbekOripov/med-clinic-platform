import { describe, expect, it } from "vitest";
import en from "./locales/en.json";
import ru from "./locales/ru.json";
import uzCyrl from "./locales/uz-Cyrl.json";
import uzLatn from "./locales/uz-Latn.json";

function keys(obj: object, prefix = ""): string[] {
  return Object.entries(obj).flatMap(([k, v]) => (v && typeof v === "object" ? keys(v, `${prefix}${k}.`) : [`${prefix}${k}`]));
}

function placeholders(obj: object): Record<string, string[]> {
  const out: Record<string, string[]> = {};
  const walk = (o: object, prefix: string) => {
    for (const [k, v] of Object.entries(o)) {
      if (v && typeof v === "object") walk(v, `${prefix}${k}.`);
      else out[`${prefix}${k}`] = [...String(v).matchAll(/\{\{(\w+)\}\}/g)].map((m) => m[1]!).sort();
    }
  };
  walk(obj, "");
  return out;
}

const locales = { "uz-Latn": uzLatn, "uz-Cyrl": uzCyrl, ru, en };

describe("locales", () => {
  it.each(Object.entries(locales))("%s has exactly the reference keys", (_, locale) => {
    expect(keys(locale).sort()).toEqual(keys(en).sort());
  });

  it.each(Object.entries(locales))("%s keeps the same interpolation placeholders", (_, locale) => {
    expect(placeholders(locale)).toEqual(placeholders(en));
  });

  it("translates every error code the backend can return", () => {
    // Keep in sync with DomainException codes in the backend.
    const backendCodes = [
      "common.not_found", "common.version_conflict", "common.duplicate", "common.internal_error",
      "request.malformed", "request.invalid", "request.invalid_parameter",
      "auth.bad_credentials", "auth.too_many_attempts", "auth.unauthenticated", "auth.forbidden", "auth.csrf_invalid",
      "auth.session_expired", "auth.password_change_required",
      "staff.login_taken", "staff.last_owner", "staff.cannot_deactivate_self", "staff.use_change_password",
      "clinic.archived", "clinic.delete_requires_archived", "branch.archived", "branch.delete_requires_archived", "branch.name_taken",
      "tenant.invalid_status_transition", "license.current_exists", "license.not_current", "license.already_revoked", "license.none",
      "pairing.branch_not_active", "pairing.code_not_open", "pairing.invalid_code", "pairing.branch_limit_reached",
      "pairing.too_many_attempts", "device.already_revoked", "device.unauthorized",
      "validation.failed", "validation.not_blank", "validation.not_null", "validation.size", "validation.range", "validation.phone",
      "validation.login_format", "validation.password_policy", "validation.password_incorrect", "validation.password_reused",
      "validation.plan_format", "validation.unknown_modules", "validation.ends_before_start", "validation.below_current_branches",
    ];
    const available = new Set(keys(en));
    expect(backendCodes.filter((c) => !available.has(`errors.${c}`))).toEqual([]);
  });

  it("uses the Uzbek oʻ/gʻ letter (U+02BB), not ASCII apostrophes", () => {
    expect(JSON.stringify(uzLatn)).not.toMatch(/[og]'/);
  });
});
