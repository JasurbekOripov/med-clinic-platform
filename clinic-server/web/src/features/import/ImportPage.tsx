import { CheckCircle2, Download, FileUp, Printer, RotateCcw } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { FormError, PageHeader } from "@medclinic/web-ui/components/Common";
import { Badge } from "@medclinic/web-ui/components/ui/badge";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Card, CardContent } from "@medclinic/web-ui/components/ui/card";
import { Table, Td, Th, Tr } from "@medclinic/web-ui/components/ui/table";
import { downloadFile } from "@medclinic/web-ui/api/client";
import { fieldErrors } from "@medclinic/web-ui/lib/errors";
import { cn } from "@medclinic/web-ui/lib/utils";
import { useImportCommit, useImportPreview } from "@/api/hooks";
import type { ImportPreview, ImportResult, ImportRow } from "@/api/types";

const COLUMNS = ["fullName", "login", "role", "position", "phone"] as const;

/** Template → upload → preview (nothing saved) → confirm → one-time passwords. */
export function ImportPage() {
  const { t, i18n } = useTranslation();
  const preview = useImportPreview();
  const commit = useImportCommit();
  const [data, setData] = React.useState<ImportPreview | null>(null);
  const [result, setResult] = React.useState<ImportResult | null>(null);
  const input = React.useRef<HTMLInputElement>(null);
  const uploadError = fieldErrors(t, preview.error).file;

  const startOver = () => {
    setData(null);
    setResult(null);
    preview.reset();
    commit.reset();
  };

  return (
    <>
      <PageHeader title={t("import.title")} subtitle={t("import.subtitle")} />
      {result ? (
        <ResultView result={result} onStartOver={startOver} />
      ) : (
        <div className="space-y-6">
          <div className="grid gap-4 md:grid-cols-2">
            <StepCard number={1} title={t("import.step1")}>
              <Button variant="secondary" onClick={() => void downloadFile(`/api/v1/imports/STAFF/template?lang=${i18n.language}`, "staff-template.xlsx")}>
                <Download /> {t("import.downloadTemplate")}
              </Button>
            </StepCard>
            <StepCard number={2} title={t("import.step2")}>
              <input
                ref={input}
                type="file"
                accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                className="hidden"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) preview.mutate(file, { onSuccess: setData });
                  e.target.value = "";
                }}
              />
              <Button loading={preview.isPending} onClick={() => input.current?.click()}>
                <FileUp /> {t("import.chooseFile")}
              </Button>
              {uploadError ? <p className="mt-3 text-sm font-medium text-danger" role="alert">{uploadError}</p> : <FormError error={preview.error} />}
            </StepCard>
          </div>

          {data && (
            <Card>
              <CardContent>
                <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
                  <div className="flex items-center gap-2">
                    <span className="flex size-7 items-center justify-center rounded-full bg-primary/15 text-sm font-semibold text-primary">3</span>
                    <h2 className="font-semibold">{t("import.step3")}</h2>
                  </div>
                  <div className="flex gap-2">
                    <Badge tone="green">{t("import.rowsValid", { count: data.validRows })}</Badge>
                    {data.invalidRows > 0 && <Badge tone="red">{t("import.rowsInvalid", { count: data.invalidRows })}</Badge>}
                  </div>
                </div>
                <PreviewTable rows={data.rows} />
                <FormError error={commit.error} />
                <div className="mt-5 flex justify-end gap-2">
                  <Button variant="secondary" onClick={startOver}>
                    <RotateCcw /> {t("import.startOver")}
                  </Button>
                  <Button size="lg" disabled={data.validRows === 0} loading={commit.isPending} onClick={() => commit.mutate(data.batchId, { onSuccess: setResult })}>
                    {t("import.confirm", { count: data.validRows })}
                  </Button>
                </div>
              </CardContent>
            </Card>
          )}
        </div>
      )}
    </>
  );
}

function StepCard({ number, title, children }: { number: number; title: string; children: React.ReactNode }) {
  return (
    <Card className="p-6">
      <div className="mb-4 flex items-center gap-2">
        <span className="flex size-7 items-center justify-center rounded-full bg-primary/15 text-sm font-semibold text-primary">{number}</span>
        <h2 className="font-semibold">{title}</h2>
      </div>
      {children}
    </Card>
  );
}

function PreviewTable({ rows }: { rows: ImportRow[] }) {
  const { t } = useTranslation();
  return (
    <Table>
      <thead>
        <tr>
          <Th>{t("import.row")}</Th>
          {COLUMNS.map((c) => (
            <Th key={c}>{t(`import.column.${c}`)}</Th>
          ))}
          <Th>{t("import.problems")}</Th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r) => {
          const bad = new Set(r.errors.map((e) => e.column));
          return (
            <Tr key={r.rowNumber} className={r.errors.length ? "bg-red-50/70" : undefined}>
              <Td className="text-muted-foreground">{r.rowNumber}</Td>
              {COLUMNS.map((c) => (
                <Td key={c} className={cn(bad.has(c) && "font-medium text-danger")}>
                  {r.values[c] ?? "—"}
                </Td>
              ))}
              <Td className="text-sm">
                {r.errors.map((e, i) => (
                  <div key={i} className="text-danger">
                    {e.column && `${t(`import.column.${e.column}`)}: `}
                    {t(`errors.${e.code}`, { ...(e.params ?? {}), defaultValue: t("errors.validation.invalid") })}
                  </div>
                ))}
                {r.warnings.map((w, i) => (
                  <div key={`w${i}`} className="text-amber-700">
                    {t(`errors.${w.code}`, { defaultValue: w.code })}
                  </div>
                ))}
              </Td>
            </Tr>
          );
        })}
      </tbody>
    </Table>
  );
}

function ResultView({ result, onStartOver }: { result: ImportResult; onStartOver: () => void }) {
  const { t } = useTranslation();
  return (
    <Card>
      <CardContent>
        <div className="mb-2 flex items-center gap-2 text-lg font-semibold text-emerald-700">
          <CheckCircle2 className="size-6" aria-hidden /> {t("import.resultTitle")}
        </div>
        <p className="mb-5 text-sm text-muted-foreground">{t("import.resultHint")}</p>
        <Table>
          <thead>
            <tr>
              <Th>{t("import.column.fullName")}</Th>
              <Th>{t("import.column.login")}</Th>
              <Th>{t("staff.temporaryPassword")}</Th>
            </tr>
          </thead>
          <tbody>
            {result.imported.map((item) => (
              <Tr key={item.rowNumber}>
                <Td className="font-medium">{item.label}</Td>
                <Td className="font-mono">{item.details.login}</Td>
                <Td className="select-all font-mono text-lg font-semibold">{item.details.temporaryPassword}</Td>
              </Tr>
            ))}
          </tbody>
        </Table>
        {result.skipped.length > 0 && <p className="mt-4 text-sm text-amber-700">{t("import.skipped", { count: result.skipped.length })}</p>}
        <div className="mt-6 flex justify-end gap-2 print:hidden">
          <Button variant="secondary" onClick={() => window.print()}>
            <Printer /> {t("common.print")}
          </Button>
          <Button onClick={onStartOver}>{t("import.startOver")}</Button>
        </div>
      </CardContent>
    </Card>
  );
}
