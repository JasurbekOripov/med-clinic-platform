import { CheckCircle2, CircleDashed, PencilLine } from "lucide-react";
import { useTranslation } from "react-i18next";
import { EmptyState, ErrorState, Spinner } from "@medclinic/web-ui/components/Common";
import { Badge } from "@medclinic/web-ui/components/ui/badge";
import { Card } from "@medclinic/web-ui/components/ui/card";
import { formatDateTime } from "@medclinic/web-ui/lib/format";
import { cn } from "@medclinic/web-ui/lib/utils";
import { useLabWork } from "@/api/hooks";
import type { LabWorkItem } from "@/api/types";

const ICON = { WAITING: CircleDashed, DRAFT: PencilLine, READY: CheckCircle2 } as const;
const TONE = { WAITING: "gray", DRAFT: "amber", READY: "green" } as const;

/** Paid (or Admin-allowed) tests: still to do first (any day), then today's finished ones. Refreshes by itself. */
export function WorkList({ onOpen }: { onOpen: (id: string) => void }) {
  const { t } = useTranslation();
  const { data, isPending, error, refetch } = useLabWork();
  if (isPending) return <Spinner />;
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />;
  if (data.length === 0) return <Card><EmptyState text={t("lab.noWork")} /></Card>;
  const open = data.filter((w) => w.status !== "READY");
  const done = data.filter((w) => w.status === "READY");
  return (
    <div className="space-y-5">
      <Section title={t("lab.toDo", { count: open.length })} items={open} onOpen={onOpen} />
      {done.length > 0 && <Section title={t("lab.doneToday", { count: done.length })} items={done} onOpen={onOpen} />}
    </div>
  );
}

function Section({ title, items, onOpen }: { title: string; items: LabWorkItem[]; onOpen: (id: string) => void }) {
  const { t } = useTranslation();
  if (items.length === 0) return null;
  return (
    <Card className="overflow-hidden">
      <div className="border-b border-border bg-muted/40 px-5 py-2.5 text-sm font-semibold">{title}</div>
      <ul className="divide-y divide-border">
        {items.map((w) => {
          const Icon = ICON[w.status];
          return (
            <li key={w.id}>
              <button type="button" onClick={() => onOpen(w.id)} className={cn("flex w-full items-center gap-4 px-5 py-3 text-left hover:bg-muted", w.status === "READY" && "opacity-70")}>
                <Icon className={cn("size-5 shrink-0", w.status === "READY" ? "text-emerald-600" : w.status === "DRAFT" ? "text-amber-600" : "text-muted-foreground")} aria-hidden />
                <div className="min-w-0 flex-1">
                  <div className="truncate font-medium">{w.patient?.fullName ?? "—"}</div>
                  <div className="text-sm text-muted-foreground">{w.serviceName} · {t("patients.card")} {w.patient?.cardNo} · {formatDateTime(w.createdAt)}</div>
                </div>
                <Badge tone={TONE[w.status]}>{t(`lab.status.${w.status}`)}</Badge>
              </button>
            </li>
          );
        })}
      </ul>
    </Card>
  );
}
