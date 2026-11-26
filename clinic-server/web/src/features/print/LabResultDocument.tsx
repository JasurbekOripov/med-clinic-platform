import { useTranslation } from "react-i18next";
import { formatDateTime } from "@medclinic/web-ui/lib/format";
import { cn } from "@medclinic/web-ui/lib/utils";
import type { LabResult, PrintSettings } from "@/api/types";
import { flagMark, formatValue, isAbnormal, isCritical } from "@/features/lab/flags";
import { A4Page, DocumentTitle, LetterheadHeader, PatientBox, SignatureFooter } from "./Letterhead";

/** A4 lab result: values outside the norm in bold with ↑/↓, critical values double-marked, the norm chosen for this patient. */
export function LabResultDocument({ result, clinic }: { result: LabResult; clinic: PrintSettings | undefined }) {
  const { t } = useTranslation();
  const state = result.state !== "NONE" ? (result.state === "CUSTOM" ? result.customState ?? "" : t(`lab.stateName.${result.state}`)) : null;
  return (
    <A4Page>
      <LetterheadHeader clinic={clinic} />
      <DocumentTitle title={result.templateName ?? result.serviceName} subtitle={formatDateTime(result.readyAt ?? result.createdAt)} />
      {result.patient && <PatientBox patient={result.patient} extra={state ? { label: t("lab.state"), value: state } : undefined} />}
      <table className="mt-5 w-full border-collapse font-sans text-[10.5pt]">
        <thead>
          <tr className="border-b-2 border-black text-left text-[9pt] uppercase tracking-wide">
            <th className="py-1.5 pr-3">{t("lab.indicator")}</th>
            <th className="py-1.5 pr-3 text-right">{t("lab.result")}</th>
            <th className="py-1.5 pr-3">{t("lab.unit")}</th>
            <th className="py-1.5">{t("lab.norm")}</th>
          </tr>
        </thead>
        <tbody>
          {result.rows.map(({ row, value, flag, norm }) =>
            row.kind === "HEADER" ? (
              <tr key={row.id}>
                <td colSpan={4} className="pb-1 pt-3 text-[10pt] font-bold uppercase tracking-wide">{row.name}</td>
              </tr>
            ) : (
              <tr key={row.id} className="border-b border-neutral-300 align-top">
                <td className="py-1.5 pr-3">{row.name}</td>
                <td className={cn("whitespace-pre-line py-1.5 pr-3 text-right tabular-nums", isAbnormal(flag) && "font-bold", isCritical(flag) && "underline")}>
                  {display(value, row.kind, row.decimals, t)} {flagMark(flag)}
                </td>
                <td className="py-1.5 pr-3">{row.unit}</td>
                <td className="py-1.5">{row.kind === "POS_NEG" ? t(row.normalPositive ? "lab.positive" : "lab.negative") : norm ?? ""}</td>
              </tr>
            ),
          )}
        </tbody>
      </table>
      {result.comment && <p className="mt-4 whitespace-pre-line"><span className="font-sans text-[10pt] font-bold">{t("lab.comment")}: </span>{result.comment}</p>}
      {result.correctedAt && (
        <p className="mt-3 font-sans text-[9pt] text-neutral-700">{t("lab.correctedNote", { date: formatDateTime(result.correctedAt), reason: result.correctionReason })}</p>
      )}
      <p className="mt-3 font-sans text-[8.5pt] text-neutral-600">{t("lab.legend")}</p>
      <div className="flex-1" />
      <SignatureFooter role={t("lab.performedBy")} name={result.readyByName} signatureUrl={`/api/v1/lab/results/${result.id}/signature`} clinic={clinic} />
    </A4Page>
  );
}

function display(value: LabResult["rows"][number]["value"], kind: string, decimals: number, t: (k: string) => string): string {
  if (!value) return "—";
  if (kind === "NUMBER" && value.number != null) return formatValue(value.number, decimals);
  if (kind === "CHOICE") return value.choice ?? "—";
  if (kind === "POS_NEG" && value.positive != null) return t(value.positive ? "lab.positive" : "lab.negative");
  return value.text ?? "—";
}
