import type { LabFlag, LabResultRow } from "@/api/types";

/**
 * Live flag while the lab worker types, using the bounds the server chose for this patient (sex, age, state).
 * Mirrors the server's evaluator: critical limits first, then the normal range; the saved flag comes from the server.
 */
export function liveFlag(row: Pick<LabResultRow, "normalLow" | "normalHigh" | "criticalLow" | "criticalHigh">, value: number | null): LabFlag | null {
  if (value === null) return null;
  if (row.criticalLow != null && value < row.criticalLow) return "CRITICAL_LOW";
  if (row.criticalHigh != null && value > row.criticalHigh) return "CRITICAL_HIGH";
  if (row.normalLow == null && row.normalHigh == null) return "NO_RANGE";
  if (row.normalLow != null && value < row.normalLow) return "LOW";
  if (row.normalHigh != null && value > row.normalHigh) return "HIGH";
  return "NORMAL";
}

/** Arrow printed next to a value. */
export function flagMark(flag: LabFlag | null | undefined): string {
  switch (flag) {
    case "LOW":
      return "↓";
    case "HIGH":
      return "↑";
    case "CRITICAL_LOW":
      return "↓↓";
    case "CRITICAL_HIGH":
      return "↑↑";
    case "ABNORMAL":
      return "!";
    default:
      return "";
  }
}

export const isAbnormal = (flag: LabFlag | null | undefined) => flag != null && flag !== "NORMAL" && flag !== "NO_RANGE";
export const isCritical = (flag: LabFlag | null | undefined) => flag === "CRITICAL_LOW" || flag === "CRITICAL_HIGH";

/** A number in the row's precision; the lab chose the number of decimals per row. */
export function formatValue(value: number, decimals: number): string {
  return value.toFixed(decimals);
}
