import { ArrowDown, ArrowLeft, ArrowUp, Copy, Plus, Trash2, X } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { FormError, PageHeader } from "@medclinic/web-ui/components/Common";
import { Field } from "@medclinic/web-ui/components/Field";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Card } from "@medclinic/web-ui/components/ui/card";
import { Input, Select } from "@medclinic/web-ui/components/ui/input";
import { fieldErrors } from "@medclinic/web-ui/lib/errors";
import { cn } from "@medclinic/web-ui/lib/utils";
import { useLabTemplateMutations, useLabTemplates, useServiceGroups, useServices } from "@/api/hooks";
import type { FormRow, LabTemplate, PhysioState, ReferenceRange, RowKind } from "@/api/types";
import { uuid } from "./uuid";

const KINDS: RowKind[] = ["NUMBER", "CHOICE", "POS_NEG", "TEXT", "HEADER"];
const STATES: PhysioState[] = ["NONE", "PREGNANCY_T1", "PREGNANCY_T2", "PREGNANCY_T3", "FOLLICULAR", "OVULATION", "LUTEAL", "POSTMENOPAUSE", "CUSTOM"];

const newRange = (): ReferenceRange => ({ kind: "NORMAL", sex: "ANY", state: "NONE", ageFrom: null, ageTo: null, low: null, high: null, text: null });

function newRow(kind: RowKind): FormRow {
  return {
    id: uuid(),
    kind,
    name: "",
    unit: null,
    decimals: 1,
    options: kind === "CHOICE" ? [{ label: "", normal: true }, { label: "", normal: false }] : [],
    normalPositive: false,
    ranges: kind === "NUMBER" ? [newRange()] : [],
  };
}

/** A copy of [source] for "Copy form": new row ids, no services (a service can have only one form). */
export function copyOf(source: LabTemplate, suffix: string): Pick<LabTemplate, "name" | "rows" | "serviceIds"> {
  return { name: `${source.name} ${suffix}`, serviceIds: [], rows: source.rows.map((r) => ({ ...r, id: uuid() })) };
}

/**
 * The lab's form editor: rows of five kinds; number rows carry reference ranges qualified by sex, age and
 * physiological state, plus optional critical limits. Every value is entered by the lab; nothing is prefilled.
 */
export function LabFormEditor({
  template,
  initial,
  onClose,
}: {
  template?: LabTemplate;
  initial?: Pick<LabTemplate, "name" | "rows" | "serviceIds">;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const { save } = useLabTemplateMutations();
  const all = useLabTemplates();
  const services = useServices();
  const groups = useServiceGroups();
  const source = template ?? initial;
  const [name, setName] = React.useState(source?.name ?? "");
  const [serviceIds, setServiceIds] = React.useState<Set<string>>(new Set(source?.serviceIds ?? []));
  const [rows, setRows] = React.useState<FormRow[]>(source?.rows ?? [newRow("NUMBER")]);
  const [active, setActive] = React.useState(template?.active ?? true);
  const errors = fieldErrors(t, save.error);
  const err = (path: string) => errors[path];

  const setRow = (i: number, patch: Partial<FormRow>) => setRows((rs) => rs.map((r, j) => (j === i ? { ...r, ...patch } : r)));
  const move = (i: number, d: -1 | 1) =>
    setRows((rs) => {
      const j = i + d;
      if (j < 0 || j >= rs.length) return rs;
      const next = [...rs];
      [next[i], next[j]] = [next[j]!, next[i]!];
      return next;
    });

  const takenBy = new Map((all.data ?? []).filter((x) => x.id !== template?.id).flatMap((x) => x.serviceIds.map((s) => [s, x.name] as const)));
  const labServices = (services.data ?? []).filter((s) => !s.perDoctor && (s.active || serviceIds.has(s.id)));
  const groupName = new Map((groups.data ?? []).map((g) => [g.id, g.name]));

  const submit = () =>
    save.mutate(
      { id: template?.id, body: { name, serviceIds: [...serviceIds], rows, active, version: template?.version } },
      { onSuccess: () => { toast.success(t("common.saved")); onClose(); } },
    );

  return (
    <>
      <button type="button" onClick={onClose} className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" /> {t("lab.forms")}
      </button>
      <PageHeader
        title={template ? t("lab.editForm") : t("lab.newForm")}
        subtitle={template ? t("lab.versionNote", { version: template.versionNo }) : t("lab.formHint")}
        actions={<Button size="lg" onClick={submit} disabled={save.isPending}>{t("common.save")}</Button>}
      />
      <FormError error={save.error} />

      <Card className="mb-5 grid gap-5 p-5 md:grid-cols-2">
        <div className="space-y-4">
          <Field label={t("lab.formName")} error={err("name")} hint={t("lab.formNameHint")}>
            <Input value={name} maxLength={200} onChange={(e) => setName(e.target.value)} />
          </Field>
          <label className="flex cursor-pointer items-center gap-3 text-sm">
            <input type="checkbox" className="size-4 accent-primary" checked={active} onChange={(e) => setActive(e.target.checked)} />
            {t("lab.activeLabel")}
          </label>
        </div>
        <div>
          <div className="mb-1.5 text-sm font-medium">{t("lab.services")}</div>
          <div className="max-h-48 overflow-y-auto rounded-lg border border-border">
            {labServices.length === 0 ? (
              <p className="px-3 py-2 text-sm text-muted-foreground">{t("queues.noServices")}</p>
            ) : (
              labServices.map((s) => {
                const other = takenBy.get(s.id);
                return (
                  <label key={s.id} className={cn("flex items-center gap-3 px-3 py-1.5 text-sm", other ? "opacity-60" : "cursor-pointer")}>
                    <input
                      type="checkbox"
                      className="size-4 accent-primary"
                      disabled={Boolean(other)}
                      checked={serviceIds.has(s.id)}
                      onChange={(e) => setServiceIds((c) => { const n = new Set(c); if (e.target.checked) n.add(s.id); else n.delete(s.id); return n; })}
                    />
                    <span className="flex-1">{s.name} <span className="text-xs text-muted-foreground">· {groupName.get(s.groupId)}</span></span>
                    {other && <span className="text-xs text-muted-foreground">{other}</span>}
                  </label>
                );
              })
            )}
          </div>
          {err("serviceIds") && <p className="mt-1 text-xs font-medium text-danger">{err("serviceIds")}</p>}
        </div>
      </Card>

      {err("rows") && <p className="mb-3 text-sm font-medium text-danger">{err("rows")}</p>}
      <div className="space-y-3">
        {rows.map((row, i) => (
          <Card key={row.id} className={cn("p-4", row.kind === "HEADER" && "bg-muted/40")}>
            <div className="flex flex-wrap items-start gap-3">
              <span className="mt-2 w-6 text-right text-sm text-muted-foreground tabular-nums">{i + 1}.</span>
              <Select
                aria-label={t("lab.rowKind")}
                value={row.kind}
                onChange={(e) => {
                  const kind = e.target.value as RowKind;
                  setRow(i, { kind, ranges: kind === "NUMBER" ? (row.ranges.length ? row.ranges : [newRange()]) : row.ranges.filter((r) => r.text), options: kind === "CHOICE" && row.options.length < 2 ? newRow("CHOICE").options : row.options });
                }}
                className="w-44"
              >
                {KINDS.map((k) => <option key={k} value={k}>{t(`lab.kind.${k}`)}</option>)}
              </Select>
              <div className="min-w-48 flex-1">
                <Input
                  aria-label={t("lab.rowName")}
                  placeholder={row.kind === "HEADER" ? t("lab.headerPlaceholder") : t("lab.rowName")}
                  value={row.name}
                  maxLength={200}
                  aria-invalid={err(`rows[${i}].name`) ? true : undefined}
                  onChange={(e) => setRow(i, { name: e.target.value })}
                  className={cn(row.kind === "HEADER" && "font-semibold")}
                />
                {err(`rows[${i}].name`) && <p className="mt-1 text-xs font-medium text-danger">{err(`rows[${i}].name`)}</p>}
              </div>
              {row.kind === "NUMBER" && (
                <>
                  <Input aria-label={t("lab.unit")} placeholder={t("lab.unit")} value={row.unit ?? ""} maxLength={30} onChange={(e) => setRow(i, { unit: e.target.value || null })} className="w-28" />
                  <Select aria-label={t("lab.decimals")} value={row.decimals} onChange={(e) => setRow(i, { decimals: Number(e.target.value) })} className="w-28" title={t("lab.decimals")}>
                    {[0, 1, 2, 3, 4].map((d) => <option key={d} value={d}>{d === 0 ? "0" : `0.${"0".repeat(d)}`}</option>)}
                  </Select>
                </>
              )}
              <div className="ml-auto flex">
                <Button size="icon" variant="ghost" aria-label={t("lab.moveUp")} onClick={() => move(i, -1)} disabled={i === 0}><ArrowUp /></Button>
                <Button size="icon" variant="ghost" aria-label={t("lab.moveDown")} onClick={() => move(i, 1)} disabled={i === rows.length - 1}><ArrowDown /></Button>
                <Button size="icon" variant="ghost" aria-label={t("common.delete")} onClick={() => setRows((rs) => rs.filter((_, j) => j !== i))}><Trash2 /></Button>
              </div>
            </div>

            {row.kind === "CHOICE" && (
              <div className="ml-9 mt-3 space-y-2">
                {row.options.map((o, k) => (
                  <div key={k} className="flex items-center gap-3">
                    <Input
                      aria-label={t("lab.option")}
                      placeholder={t("lab.option")}
                      value={o.label}
                      maxLength={100}
                      onChange={(e) => setRow(i, { options: row.options.map((x, m) => (m === k ? { ...x, label: e.target.value } : x)) })}
                      className="max-w-72"
                    />
                    <label className="flex items-center gap-2 text-sm">
                      <input type="checkbox" className="size-4 accent-primary" checked={o.normal} onChange={(e) => setRow(i, { options: row.options.map((x, m) => (m === k ? { ...x, normal: e.target.checked } : x)) })} />
                      {t("lab.normalAnswer")}
                    </label>
                    <Button size="icon" variant="ghost" aria-label={t("common.remove")} onClick={() => setRow(i, { options: row.options.filter((_, m) => m !== k) })}><X /></Button>
                  </div>
                ))}
                <Button size="sm" variant="ghost" onClick={() => setRow(i, { options: [...row.options, { label: "", normal: false }] })}><Plus /> {t("lab.addOption")}</Button>
                {err(`rows[${i}].options`) && <p className="text-xs font-medium text-danger">{err(`rows[${i}].options`)}</p>}
              </div>
            )}

            {row.kind === "POS_NEG" && (
              <div className="ml-9 mt-3 flex items-center gap-3 text-sm">
                <span className="text-muted-foreground">{t("lab.normalAnswer")}:</span>
                <Select value={row.normalPositive ? "1" : "0"} onChange={(e) => setRow(i, { normalPositive: e.target.value === "1" })} className="w-44">
                  <option value="0">{t("lab.negative")}</option>
                  <option value="1">{t("lab.positive")}</option>
                </Select>
              </div>
            )}

            {row.kind === "NUMBER" && <RangesTable rowIndex={i} ranges={row.ranges} onChange={(ranges) => setRow(i, { ranges })} err={err} />}
          </Card>
        ))}
      </div>

      <div className="mt-4 flex flex-wrap gap-2">
        {KINDS.map((k) => (
          <Button key={k} variant="secondary" size="sm" onClick={() => setRows((rs) => [...rs, newRow(k)])}><Plus /> {t(`lab.kind.${k}`)}</Button>
        ))}
      </div>
      <div className="mt-6 flex justify-end">
        <Button size="lg" onClick={submit} disabled={save.isPending}>{t("common.save")}</Button>
      </div>
    </>
  );
}

function RangesTable({ rowIndex, ranges, onChange, err }: { rowIndex: number; ranges: ReferenceRange[]; onChange: (r: ReferenceRange[]) => void; err: (p: string) => string | undefined }) {
  const { t } = useTranslation();
  const set = (j: number, patch: Partial<ReferenceRange>) => onChange(ranges.map((r, k) => (k === j ? { ...r, ...patch } : r)));
  const at = (j: number, f?: string) => `rows[${rowIndex}].ranges[${j}]${f ? `.${f}` : ""}`;

  return (
    <div className="ml-9 mt-3">
      <div className="mb-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t("lab.ranges")}</div>
      {ranges.length === 0 && <p className="text-sm text-muted-foreground">{t("lab.noRanges")}</p>}
      <div className="space-y-2">
        {ranges.map((r, j) => {
          const problem = err(at(j)) ?? err(at(j, "high")) ?? err(at(j, "ageTo")) ?? err(at(j, "ageFrom")) ?? err(at(j, "state")) ?? err(at(j, "customState"));
          return (
            <div key={j} className={cn("rounded-lg border p-2", r.kind === "CRITICAL" ? "border-red-200 bg-red-50/50" : "border-border", problem && "border-danger")}>
              <div className="flex flex-wrap items-center gap-2 text-sm">
                <Select aria-label={t("lab.rangeKind")} value={r.kind} onChange={(e) => set(j, { kind: e.target.value as ReferenceRange["kind"] })} className="h-9 w-36">
                  <option value="NORMAL">{t("lab.rangeNormal")}</option>
                  <option value="CRITICAL">{t("lab.rangeCritical")}</option>
                </Select>
                <Select aria-label={t("patients.gender")} value={r.sex} onChange={(e) => set(j, { sex: e.target.value as ReferenceRange["sex"] })} className="h-9 w-32">
                  <option value="ANY">{t("lab.sexAny")}</option>
                  <option value="MALE">{t("gender.MALE")}</option>
                  <option value="FEMALE">{t("gender.FEMALE")}</option>
                </Select>
                <AgeInput label={t("lab.ageFrom")} value={r.ageFrom ?? null} onChange={(ageFrom) => set(j, { ageFrom })} />
                <AgeInput label={t("lab.ageTo")} value={r.ageTo ?? null} onChange={(ageTo) => set(j, { ageTo })} />
                <Select aria-label={t("lab.state")} value={r.state} onChange={(e) => set(j, { state: e.target.value as PhysioState })} className="h-9 w-56">
                  {STATES.map((s) => <option key={s} value={s}>{t(`lab.stateName.${s}`)}</option>)}
                </Select>
                {r.state === "CUSTOM" && (
                  <Input aria-label={t("lab.customState")} placeholder={t("lab.customState")} value={r.customState ?? ""} maxLength={60} onChange={(e) => set(j, { customState: e.target.value })} className="h-9 w-36" />
                )}
                <span className="flex items-center gap-1">
                  <NumberCell label={t("lab.low")} value={r.low ?? null} onChange={(low) => set(j, { low })} />
                  <span className="text-muted-foreground">–</span>
                  <NumberCell label={t("lab.high")} value={r.high ?? null} onChange={(high) => set(j, { high })} />
                </span>
                <Input aria-label={t("lab.normText")} placeholder={t("lab.normText")} value={r.text ?? ""} maxLength={60} onChange={(e) => set(j, { text: e.target.value || null })} className="h-9 w-32" />
                <Button size="icon" variant="ghost" aria-label={t("lab.copyRange")} title={t("lab.copyRange")} onClick={() => onChange([...ranges.slice(0, j + 1), { ...r }, ...ranges.slice(j + 1)])}><Copy /></Button>
                <Button size="icon" variant="ghost" aria-label={t("common.remove")} onClick={() => onChange(ranges.filter((_, k) => k !== j))}><X /></Button>
              </div>
              {problem && <p className="mt-1 text-xs font-medium text-danger">{problem}</p>}
            </div>
          );
        })}
      </div>
      <Button size="sm" variant="ghost" className="mt-1" onClick={() => onChange([...ranges, newRange()])}><Plus /> {t("lab.addRange")}</Button>
    </div>
  );
}

function AgeInput({ label, value, onChange }: { label: string; value: { value: number; unit: "DAYS" | "MONTHS" | "YEARS" } | null; onChange: (v: { value: number; unit: "DAYS" | "MONTHS" | "YEARS" } | null) => void }) {
  const { t } = useTranslation();
  return (
    <span className="flex items-center gap-1" title={label}>
      <Input
        aria-label={label}
        placeholder={label}
        inputMode="numeric"
        value={value?.value ?? ""}
        onChange={(e) => {
          const digits = e.target.value.replace(/\D/g, "").slice(0, 4);
          onChange(digits === "" ? null : { value: Number(digits), unit: value?.unit ?? "YEARS" });
        }}
        className="h-9 w-28 text-right tabular-nums"
      />
      <Select aria-label={`${label} — ${t("lab.ageUnit")}`} value={value?.unit ?? "YEARS"} disabled={!value} onChange={(e) => value && onChange({ ...value, unit: e.target.value as "DAYS" | "MONTHS" | "YEARS" })} className="h-9 w-24">
        <option value="DAYS">{t("lab.days")}</option>
        <option value="MONTHS">{t("lab.months")}</option>
        <option value="YEARS">{t("lab.years")}</option>
      </Select>
    </span>
  );
}

/** Decimal input that keeps what is being typed ("4." or "0,5") and follows outside changes (copy, delete). */
function NumberCell({ label, value, onChange }: { label: string; value: number | null; onChange: (v: number | null) => void }) {
  const [text, setText] = React.useState(value === null ? "" : String(value));
  React.useEffect(() => {
    if (parseDecimal(text) !== value) setText(value === null ? "" : String(value));
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only outside changes of the value matter
  }, [value]);
  return (
    <Input
      aria-label={label}
      placeholder={label}
      inputMode="decimal"
      value={text}
      onChange={(e) => {
        const next = e.target.value.replace(/[^0-9.,-]/g, "").slice(0, 16);
        setText(next);
        onChange(parseDecimal(next));
      }}
      className="h-9 w-24 text-right tabular-nums"
    />
  );
}

/** "0,5" and "0.5" both mean one half (comma is common on Uzbek and Russian keyboards); anything else is empty. */
export function parseDecimal(v: string): number | null {
  const clean = v.replace(",", ".").trim();
  if (clean === "" || clean === "-" || clean === ".") return null;
  const n = Number(clean);
  return Number.isFinite(n) ? n : null;
}
