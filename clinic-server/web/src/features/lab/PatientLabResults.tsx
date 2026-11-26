import { FlaskConical, Printer } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { ErrorState, Spinner } from "@medclinic/web-ui/components/Common";
import { Badge } from "@medclinic/web-ui/components/ui/badge";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Card } from "@medclinic/web-ui/components/ui/card";
import { formatDateTime } from "@medclinic/web-ui/lib/format";
import { cn } from "@medclinic/web-ui/lib/utils";
import { usePatientLabResults, usePrintSettings } from "@/api/hooks";
import type { LabResult } from "@/api/types";
import { LabResultDocument } from "@/features/print/LabResultDocument";
import { printDocument } from "@/features/print/print";
import { PrintPortal } from "@/features/print/PrintPortal";
import { flagMark, formatValue, isAbnormal, isCritical } from "./flags";

/** Ready lab results on the patient page (the lab also sees unfinished ones); print on the letterhead. */
export function PatientLabResults({ patientId }: { patientId: string }) {
  const { t } = useTranslation();
  const { data, isPending, error, refetch } = usePatientLabResults(patientId, true);
  const clinic = usePrintSettings();
  const [open, setOpen] = React.useState<string | null>(null);
  const [printing, setPrinting] = React.useState<LabResult | null>(null);

  React.useEffect(() => {
    if (!printing) return;
    const done = () => setPrinting(null);
    window.addEventListener("afterprint", done, { once: true });
    const frame = requestAnimationFrame(() => printDocument("a4"));
    return () => { cancelAnimationFrame(frame); window.removeEventListener("afterprint", done); };
  }, [printing]);

  if (isPending) return <Spinner />;
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />;
  if (data.length === 0) return null;

  return (
    <section className="mb-6">
      <h2 className="mb-3 flex items-center gap-2 text-lg font-semibold">
        <FlaskConical className="size-5 text-primary" aria-hidden /> {t("lab.results")}
      </h2>
      <div className="space-y-3">
        {data.map((r) => {
          const abnormal = r.rows.filter((x) => isAbnormal(x.flag)).length;
          const critical = r.rows.some((x) => isCritical(x.flag));
          const expanded = open === r.id;
          return (
            <Card key={r.id} className="p-5">
              <div className="flex flex-wrap items-center gap-3">
                <button type="button" className="min-w-0 flex-1 text-left" onClick={() => setOpen(expanded ? null : r.id)} aria-expanded={expanded}>
                  <div className="font-semibold">{r.templateName ?? r.serviceName}</div>
                  <div className="text-sm text-muted-foreground">{r.readyByName ?? "—"} · {formatDateTime(r.readyAt ?? r.createdAt)}</div>
                </button>
                {r.status !== "READY" && <Badge tone="amber">{t(`lab.status.${r.status}`)}</Badge>}
                {critical && <Badge tone="red">{t("lab.flag.critical")}</Badge>}
                {abnormal > 0 && <Badge tone="amber">{t("lab.abnormalCount", { count: abnormal })}</Badge>}
                {r.status === "READY" && (
                  <Button size="sm" variant="ghost" disabled={clinic.isPending} onClick={() => setPrinting(r)}><Printer /> {t("common.print")}</Button>
                )}
              </div>
              {expanded && (
                <table className="mt-3 w-full text-sm">
                  <tbody className="divide-y divide-border">
                    {r.rows.map(({ row, value, flag, norm }) =>
                      row.kind === "HEADER" ? (
                        <tr key={row.id}><td colSpan={3} className="pt-3 font-semibold">{row.name}</td></tr>
                      ) : (
                        <tr key={row.id}>
                          <td className="py-1.5 pr-3">{row.name}</td>
                          <td className={cn("py-1.5 pr-3 text-right tabular-nums", isAbnormal(flag) && "font-bold text-amber-700", isCritical(flag) && "text-red-700")}>
                            {value?.number != null ? formatValue(value.number, row.decimals) : value?.choice ?? (value?.positive != null ? t(value.positive ? "lab.positive" : "lab.negative") : value?.text ?? "—")} {flagMark(flag)} {row.unit}
                          </td>
                          <td className="py-1.5 text-muted-foreground">{norm}</td>
                        </tr>
                      ),
                    )}
                  </tbody>
                </table>
              )}
            </Card>
          );
        })}
      </div>
      {printing && <PrintPortal><LabResultDocument result={printing} clinic={clinic.data} /></PrintPortal>}
    </section>
  );
}
