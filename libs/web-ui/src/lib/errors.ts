import type { TFunction } from "i18next";
import { ApiError, type FieldError } from "@medclinic/web-ui/api/client";

/** Turns any thrown value into a user-facing, translated sentence. */
export function errorMessage(t: TFunction, error: unknown): string {
  if (error instanceof ApiError) {
    return t(`errors.${error.code}`, { ...error.params, defaultValue: t("errors.unknown") });
  }
  return t("errors.unknown");
}

export function fieldErrorMessage(t: TFunction, error: FieldError): string {
  return t(`errors.${error.code}`, { ...(error.params ?? {}), defaultValue: t("errors.validation.invalid") });
}

/** Field errors keyed by field name (first error per field). */
export function fieldErrors(t: TFunction, error: unknown): Record<string, string> {
  if (!(error instanceof ApiError)) return {};
  const result: Record<string, string> = {};
  for (const fe of error.fieldErrors) {
    result[fe.field] ??= fieldErrorMessage(t, fe);
  }
  return result;
}
