import { ArrowLeft, CheckCircle2, Printer, Wrench } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { ErrorState, FormError, PageHeader, Spinner } from "@medclinic/web-ui/components/Common";
import { Badge } from "@medclinic/web-ui/components/ui/badge";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Card } from "@medclinic/web-ui/components/ui/card";
import { Input, Select, Textarea } from "@medclinic/web-ui/components/ui/input";
import { fieldErrors } from "@medclinic/web-ui/lib/errors";
import { formatDate } from "@medclinic/web-ui/lib/format";
import { cn } from "@medclinic/web-ui/lib/utils";
import { useLabResult, useLabResultActions, usePrintSettings } from "@/api/hooks";
import type { LabFlag, LabResult, PhysioState, RowValue } from "@/api/types";
import { ageOn } from "@/features/patients/age";
import { LabResultDocument } from "@/features/print/LabResultDocument";
import { printDocument } from "@/features/print/print";
import { PrintPortal } from "@/features/print/PrintPortal";
import { ReasonDialog } from "@/features/visits/ReasonDialog";
import { flagMark, isAbnormal, isCritical, liveFlag } from "./flags";
import { parseDecimal } from "./LabFormEditor";

const STATES: PhysioState[] = ["NONE", "PREGNANCY_T1", "PREGNANCY_T2", "PREGNANCY_T3", "FOLLICULAR", "OVULATION", "LUTEAL", "POSTMENOPAUSE", "CUSTOM"];

type Draft = { state: PhysioState; customState: string; values: Record<string, RowValue>; texts: Record<string, string>; comment: string };

function draftOf(r: LabResult): Draft {
  const values: Record<string, RowValue> = {};
  const texts: Record<string, string> = {};
  for (const { row, value } of r.rows) {
    if (!value) continue;
    values[row.id] = value;
    if (row.kind === "NUMBER" && value.number != null) texts[row.id] = String(value.number);
  }
  return { state: r.state, customState: r.customState ?? "", values, texts, comment: r.comment ?? "" };
}

/** The lab worker's screen for one test: values with live ↑/↓ against the norms chosen for this patient, then "Tayyor". */
export function ResultEntry({ id, onClose }: { id: string; onClose: () => void }) {
  const { t } = useTranslation();
  const [draft, setDraft] = React.useState<Draft | null>(null);
  const result = useLabResult(id, draft?.state, draft?.state === "CUSTOM" ? draft.customState : undefined);
  const { save, ready, correct } = useLabResultActions();
  const clinic = usePrintSettings();
  const [correcting, setCorrecting] = React.useState(false);
  const [askReason, setAskReason] = React.useState(false);
  const [printing, setPrinting] = React.useState(false);
  const r = result.data;

  React.useEffect(() => {
    if (r && draft === null) setDraft(draftOf(r));
  }, [r, draft]);

  React.useEffect(() => {
    if (!printing) return;
    const done = () => setPrinting(false);
    window.addEventListener("afterprint", done, { once: true });
    const frame = requestAnimationFrame(() => printDocument("a4"));
    return () => { cancelAnimationFrame(frame); window.removeEventListener("afterprint", done); };
  }, [printing]);

  if (result.isPending || !draft || !r) return <Spinner />;
  if (result.error) return <ErrorState error={result.error} onRetry={() => void result.refetch()} />;

  const isReady = r.status === "READY";
  const editable = !isReady || correcting;
  const errors = fieldErrors(t, save.error ?? correct.error);
  const body = { state: draft.state, customState: draft.state === "CUSTOM" ? draft.customState : undefined, values: draft.values, comment: draft.comment || undefined };
  const setValue = (rowId: string, v: RowValue | null) =>
    setDraft((d) => {
      if (!d) return d;
      const values = { ...d.values };
      if (v === null) delete values[rowId];
      else values[rowId] = v;
      return { ...d, values };
    });

  const saveDraft = () => save.mutateAsync({ id, ...body, version: r.version });
  const markReady = async () => {
    const saved = await saveDraft();
    await ready.mutateAsync({ id, version: saved.version });
    toast.success(t("lab.readyDone"));
    onClose();
  };

  return (
    <>
      <button type="button" onClick={onClose} className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" /> {t("lab.work")}
      </button>
      <PageHeader
        title={r.templateName ?? r.serviceName}
        subtitle={r.patient ? `${r.patient.fullName} · ${t("patients.card")} ${r.patient.cardNo} · ${t(`gender.${r.patient.gender}`)} · ${formatDate(r.patient.birthDate)} (${t("patients.age", { count: ageOn(r.patient.birthDate) })})` : undefined}
        actions={
          <div className="flex items-center gap-2">
            <Badge tone={isReady ? "green" : r.status === "DRAFT" ? "amber" : "gray"}>{t(`lab.status.${r.status}`)}</Badge>
            {isReady && !correcting && (
              <>
                <Button variant="secondary" onClick={() => setCorrecting(true)}><Wrench /> {t("lab.correct")}</Button>
                <Button onClick={() => setPrinting(true)} disabled={clinic.isPending}><Printer /> {t("common.print")}</Button>
              </>
            )}
          </div>
        }
      />

      {r.usesStates && (
        <Card className="mb-4 flex flex-wrap items-center gap-3 p-4">
          <span className="text-sm font-medium">{t("lab.patientState")}</span>
          <Select aria-label={t("lab.state")} value={draft.state} disabled={!editable} onChange={(e) => setDraft({ ...draft, state: e.target.value as PhysioState })} className="w-64">
            {STATES.filter((s) => s !== "CUSTOM" || r.customStates.length > 0).map((s) => <option key={s} value={s}>{t(s === "NONE" ? "lab.stateNone" : `lab.stateName.${s}`)}</option>)}
          </Select>
          {draft.state === "CUSTOM" && (
            <Select aria-label={t("lab.customState")} value={draft.customState} disabled={!editable} onChange={(e) => setDraft({ ...draft, customState: e.target.value })} className="w-48">
              <option value="">—</option>
              {r.customStates.map((c) => <option key={c} value={c}>{c}</option>)}
            </Select>
          )}
          <span className="text-xs text-muted-foreground">{t("lab.stateHint")}</span>
        </Card>
      )}

      <Card className="overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-muted/50 text-left text-xs uppercase tracking-wide text-muted-foreground">
            <tr>
              <th className="px-4 py-2">{t("lab.indicator")}</th>
              <th className="px-4 py-2">{t("lab.result")}</th>
              <th className="px-4 py-2">{t("lab.unit")}</th>
              <th className="px-4 py-2">{t("lab.norm")}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {r.rows.map((rr) => {
              const { row } = rr;
              if (row.kind === "HEADER") return <tr key={row.id}><td colSpan={4} className="bg-muted/30 px-4 py-2 font-semibold">{row.name}</td></tr>;
              const v = draft.values[row.id];
              const flag: LabFlag | null =
                row.kind === "NUMBER" ? liveFlag(rr, v?.number ?? null)
                : row.kind === "CHOICE" ? (v?.choice ? (row.options.find((o) => o.label === v.choice)?.normal ? "NORMAL" : "ABNORMAL") : null)
                : row.kind === "POS_NEG" ? (v?.positive == null ? null : v.positive === row.normalPositive ? "NORMAL" : "ABNORMAL")
                : null;
              const error = errors[`values.${row.id}`];
              return (
                <tr key={row.id} className={cn(isCritical(flag) ? "bg-red-50" : isAbnormal(flag) && "bg-amber-50/60")}>
                  <td className="px-4 py-2 font-medium">{row.name}</td>
                  <td className="px-4 py-2">
                    <div className="flex items-center gap-2">
                      {row.kind === "NUMBER" && (
                        <Input
                          aria-label={row.name}
                          inputMode="decimal"
                          disabled={!editable}
                          value={draft.texts[row.id] ?? ""}
                          onChange={(e) => {
                            const text = e.target.value.replace(/[^0-9.,-]/g, "").slice(0, 16);
                            setDraft((d) => d && { ...d, texts: { ...d.texts, [row.id]: text } });
                            const n = parseDecimal(text);
                            setValue(row.id, n === null ? null : { number: n });
                          }}
                          className={cn("h-9 w-32 text-right tabular-nums", isAbnormal(flag) && "font-bold")}
                        />
                      )}
                      {row.kind === "CHOICE" && (
                        <Select aria-label={row.name} disabled={!editable} value={v?.choice ?? ""} onChange={(e) => setValue(row.id, e.target.value ? { choice: e.target.value } : null)} className="h-9 w-48">
                          <option value="">—</option>
                          {row.options.map((o) => <option key={o.label} value={o.label}>{o.label}</option>)}
                        </Select>
                      )}
                      {row.kind === "POS_NEG" && (
                        <Select aria-label={row.name} disabled={!editable} value={v?.positive == null ? "" : v.positive ? "1" : "0"} onChange={(e) => setValue(row.id, e.target.value === "" ? null : { positive: e.target.value === "1" })} className="h-9 w-48">
                          <option value="">—</option>
                          <option value="0">{t("lab.negative")}</option>
                          <option value="1">{t("lab.positive")}</option>
                        </Select>
                      )}
                      {row.kind === "TEXT" && (
                        <Input aria-label={row.name} disabled={!editable} value={v?.text ?? ""} maxLength={2000} onChange={(e) => setValue(row.id, e.target.value ? { text: e.target.value } : null)} className="h-9 min-w-72" />
                      )}
                      {flag && flag !== "NORMAL" && flag !== "NO_RANGE" && (
                        <span className={cn("text-base font-bold", isCritical(flag) ? "text-red-700" : "text-amber-700")} title={t(`lab.flag.${flag}`)}>
                          {flagMark(flag)}
                        </span>
                      )}
                      {isCritical(flag) && <Badge tone="red">{t("lab.flag.critical")}</Badge>}
                    </div>
                    {error && <p className="mt-1 text-xs font-medium text-danger">{error}</p>}
                  </td>
                  <td className="px-4 py-2 text-muted-foreground">{row.unit}</td>
                  <td className="px-4 py-2 text-muted-foreground">
                    {row.kind === "POS_NEG" ? t(row.normalPositive ? "lab.positive" : "lab.negative") : rr.norm ?? (row.kind === "NUMBER" ? <span className="text-amber-700">{t("lab.noNorm")}</span> : "")}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        <div className="border-t border-border p-4">
          <label className="mb-1.5 block text-sm font-medium">{t("lab.comment")}</label>
          <Textarea disabled={!editable} value={draft.comment} maxLength={2000} onChange={(e) => setDraft({ ...draft, comment: e.target.value })} />
        </div>
      </Card>

      {r.correctedAt && <p className="mt-3 text-sm text-muted-foreground">{t("lab.correctedNote", { date: formatDate(r.correctedAt), reason: r.correctionReason })}</p>}
      <FormError error={save.error ?? ready.error ?? correct.error} />
      {!isReady && (
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" disabled={save.isPending} onClick={() => saveDraft().then(() => toast.success(t("common.saved"))).catch(() => undefined)}>{t("lab.saveDraft")}</Button>
          <Button size="lg" disabled={save.isPending || ready.isPending || Object.keys(draft.values).length === 0} onClick={() => void markReady().catch(() => undefined)}>
            <CheckCircle2 /> {t("lab.ready")}
          </Button>
        </div>
      )}
      {correcting && (
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="ghost" onClick={() => { setCorrecting(false); setDraft(draftOf(r)); }}>{t("common.cancel")}</Button>
          <Button size="lg" onClick={() => setAskReason(true)}><Wrench /> {t("lab.saveCorrection")}</Button>
        </div>
      )}
      {askReason && (
        <ReasonDialog
          title={t("lab.correctTitle")}
          description={t("lab.correctHint")}
          confirmLabel={t("lab.correct")}
          onClose={() => setAskReason(false)}
          onConfirm={(reason) => correct.mutateAsync({ id, ...body, reason, version: r.version }).then(() => { toast.success(t("common.saved")); setCorrecting(false); })}
        />
      )}
      {printing && <PrintPortal><LabResultDocument result={r} clinic={clinic.data} /></PrintPortal>}
    </>
  );
}
