import { describe, expect, it } from "vitest";
import ru from "./locales/ru.json";
import uzCyrl from "./locales/uz-Cyrl.json";
import uzLatn from "./locales/uz-Latn.json";

function flatten(obj: object, prefix = ""): Record<string, string> {
  return Object.entries(obj).reduce<Record<string, string>>((acc, [k, v]) => {
    if (v && typeof v === "object") Object.assign(acc, flatten(v, `${prefix}${k}.`));
    else acc[`${prefix}${k}`] = String(v);
    return acc;
  }, {});
}

const reference = flatten(uzLatn);
const locales = { "uz-Cyrl": flatten(uzCyrl), ru: flatten(ru) };
const placeholders = (s: string) => [...s.matchAll(/\{\{(\w+)\}\}/g)].map((m) => m[1]).sort();

describe("clinic locales", () => {
  it.each(Object.entries(locales))("%s has exactly the Uzbek Latin keys and placeholders", (_, locale) => {
    expect(Object.keys(locale).sort()).toEqual(Object.keys(reference).sort());
    for (const [key, text] of Object.entries(reference)) expect(placeholders(locale[key]!), key).toEqual(placeholders(text));
  });

  it("translates the error codes the clinic server returns", () => {
    const codes = [
      "auth.bad_credentials", "auth.too_many_attempts", "auth.password_change_required", "staff.login_taken", "staff.last_admin",
      "role.system_locked", "role.name_taken", "role.in_use", "license.none", "license.expired", "license.revoked", "license.clock_rollback",
      "license.max_users_reached", "setup.already_paired", "setup.already_completed", "vendor.unreachable", "pairing.invalid_code",
      "import.duplicate_in_file", "import.expired", "validation.image_type", "validation.unknown_role", "validation.role_not_assignable",
      "validation.working_hours", "validation.vendor_url", "validation.license_bad_signature", "validation.license_wrong_clinic",
      "validation.license_older", "validation.import_wrong_template", "validation.import_not_xlsx",
    ];
    expect(codes.filter((c) => !(`errors.${c}` in reference))).toEqual([]);
  });

  it("has a label for every permission and role template", () => {
    const permissions = [
      "STAFF_VIEW", "STAFF_MANAGE", "ROLE_MANAGE", "SETTINGS_MANAGE", "AUDIT_VIEW", "LICENSE_MANAGE", "DATA_IMPORT", "PATIENT_VIEW",
      "PATIENT_EDIT", "VISIT_CREATE", "VISIT_EDIT_UNPAID", "QUEUE_MANAGE", "RESULTS_HANDOVER", "PAYMENT_ACCEPT", "SHIFT_MANAGE", "DEBT_VIEW",
      "REFUND", "PAID_SERVICE_CANCEL", "EMERGENCY_ALLOW", "PRICE_LIST_MANAGE", "REPORTS_VIEW", "MEDICAL_HISTORY_VIEW", "CONSULTATION_WRITE",
      "SERVICE_ORDER", "LAB_WORK", "LAB_TEMPLATES_MANAGE", "INPATIENT_WORK", "OPERATIONS_WORK",
    ];
    expect(permissions.filter((p) => !(`permission.${p}` in reference))).toEqual([]);
    for (const r of ["ADMIN", "RECEPTION", "CASHIER", "DOCTOR", "LAB", "NURSE", "VENDOR_SUPPORT"]) expect(reference[`roleTemplate.${r}`]).toBeTruthy();
  });

  it("writes Uzbek oʻ/gʻ with U+02BB", () => {
    expect(JSON.stringify(uzLatn)).not.toMatch(/[og]'/);
  });
});
