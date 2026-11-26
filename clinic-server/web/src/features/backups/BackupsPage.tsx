import { CheckCircle2, DatabaseBackup, HardDrive, Loader2, PlayCircle, XCircle } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { ErrorState, PageHeader, Spinner } from "@medclinic/web-ui/components/Common";
import { Badge } from "@medclinic/web-ui/components/ui/badge";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Card } from "@medclinic/web-ui/components/ui/card";
import { Input } from "@medclinic/web-ui/components/ui/input";
import { errorMessage } from "@medclinic/web-ui/lib/errors";
import { formatBytes, formatDateTime } from "@medclinic/web-ui/lib/format";
import { cn } from "@medclinic/web-ui/lib/utils";
import { useBackupActions, useBackups } from "@/api/hooks";

/** Admin: nightly backups of the whole database, their history, "back up now" and how long copies are kept. */
export function BackupsPage() {
  const { t } = useTranslation();
  const { data, isPending, error, refetch } = useBackups();
  const { run, retention } = useBackupActions();
  const [days, setDays] = React.useState("");
  React.useEffect(() => {
    if (data && days === "") setDays(String(data.retentionDays));
  }, [data, days]);

  if (isPending) return <Spinner />;
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />;
  const last = data.lastSuccess;

  return (
    <>
      <PageHeader
        title={t("backup.title")}
        subtitle={t("backup.subtitle")}
        actions={
          <Button size="lg" disabled={data.running || run.isPending} onClick={() => run.mutate(undefined, { onSuccess: () => toast.success(t("backup.started")), onError: (e) => toast.error(errorMessage(t, e)) })}>
            {data.running ? <Loader2 className="animate-spin" /> : <PlayCircle />} {data.running ? t("backup.running") : t("backup.runNow")}
          </Button>
        }
      />
      <div className="mb-5 grid gap-4 md:grid-cols-3">
        <Card className={cn("p-5", data.stale ? "border-amber-300 bg-amber-50/60" : "border-emerald-200 bg-emerald-50/50")}>
          <div className="flex items-center gap-2 text-sm font-medium">
            {data.stale ? <XCircle className="size-4 text-amber-700" aria-hidden /> : <CheckCircle2 className="size-4 text-emerald-700" aria-hidden />}
            {t("backup.lastSuccess")}
          </div>
          <div className="mt-1 text-lg font-semibold">{last ? formatDateTime(last.startedAt) : t("backup.never")}</div>
          {last && <div className="text-sm text-muted-foreground">{formatBytes(last.sizeBytes)} · {last.fileName}</div>}
          {data.stale && <p className="mt-2 text-sm text-amber-900">{t("backup.staleHint")}</p>}
        </Card>
        <Card className="p-5">
          <div className="flex items-center gap-2 text-sm font-medium"><HardDrive className="size-4" aria-hidden /> {t("backup.where")}</div>
          <div className="mt-1 break-all font-mono text-sm">{data.directory}</div>
          <div className="text-sm text-muted-foreground">{t("backup.freeSpace", { size: data.freeSpaceBytes == null ? "—" : formatBytes(data.freeSpaceBytes) })}</div>
          {!data.enabled && <Badge tone="amber" className="mt-2">{t("backup.disabled")}</Badge>}
        </Card>
        <Card className="p-5">
          <div className="flex items-center gap-2 text-sm font-medium"><DatabaseBackup className="size-4" aria-hidden /> {t("backup.retention")}</div>
          <div className="mt-2 flex items-center gap-2">
            <Input aria-label={t("backup.retention")} inputMode="numeric" value={days} onChange={(e) => setDays(e.target.value.replace(/\D/g, "").slice(0, 3))} className="w-24 text-right" />
            <span className="text-sm text-muted-foreground">{t("backup.days")}</span>
            <Button
              size="sm"
              variant="secondary"
              disabled={retention.isPending || days === String(data.retentionDays) || days === ""}
              onClick={() => retention.mutate(Number(days), { onSuccess: () => toast.success(t("common.saved")), onError: (e) => toast.error(errorMessage(t, e)) })}
            >
              {t("common.save")}
            </Button>
          </div>
          <p className="mt-2 text-xs text-muted-foreground">{t("backup.retentionHint")}</p>
        </Card>
      </div>

      <Card className="overflow-x-auto">
        <div className="border-b border-border bg-muted/40 px-5 py-2.5 text-sm font-semibold">{t("backup.history")}</div>
        {data.runs.length === 0 ? (
          <p className="px-5 py-4 text-sm text-muted-foreground">{t("backup.noRuns")}</p>
        ) : (
          <table className="w-full text-sm">
            <tbody className="divide-y divide-border">
              {data.runs.map((r) => (
                <tr key={r.id} className="align-top">
                  <td className="px-5 py-2.5 whitespace-nowrap">{formatDateTime(r.startedAt)}</td>
                  <td className="px-3 py-2.5"><Badge tone={r.status === "SUCCESS" ? "green" : r.status === "FAILED" ? "red" : "blue"}>{t(`backup.status.${r.status}`)}</Badge></td>
                  <td className="px-3 py-2.5 text-muted-foreground">{t(`backup.trigger.${r.trigger}`)}</td>
                  <td className="px-3 py-2.5">
                    {r.fileName && <div className="font-mono text-xs">{r.fileName}</div>}
                    {r.error && <div className="text-xs text-danger">{r.error}</div>}
                  </td>
                  <td className="px-5 py-2.5 text-right tabular-nums">{r.sizeBytes != null ? formatBytes(r.sizeBytes) : ""}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
      <p className="mt-4 text-sm text-muted-foreground">{t("backup.restoreNote")}</p>
    </>
  );
}
