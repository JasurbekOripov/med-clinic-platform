import { Check, RotateCcw, Settings2 } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { toast } from "sonner";
import { EmptyState, ErrorState, PageHeader, Segmented, Spinner } from "@medclinic/web-ui/components/Common";
import { Badge } from "@medclinic/web-ui/components/ui/badge";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Card } from "@medclinic/web-ui/components/ui/card";
import { errorMessage } from "@medclinic/web-ui/lib/errors";
import { cn } from "@medclinic/web-ui/lib/utils";
import { useMe, useQueueTickets, useQueues, useTicketDone } from "@/api/hooks";
import type { Ticket } from "@/api/types";
import { usePermissions } from "@/features/auth/permissions";
import { safeStorage as storage } from "@medclinic/web-ui/lib/storage";
import { QueueSetup } from "./QueueSetup";

const LAST_QUEUE = "clinic.queue.last";

/**
 * Today's paid patients of one room in number order. Doctors call patients by the number on the paper slip, so this
 * is a list to follow (and optionally tick "seen"), not a calling system.
 */
export function QueuePage() {
  const { t } = useTranslation();
  const { can } = usePermissions();
  const me = useMe();
  const queues = useQueues();
  const [setup, setSetup] = React.useState(false);
  const [chosen, setChosen] = React.useState<string | null>(() => storage.get(LAST_QUEUE));
  const active = (queues.data ?? []).filter((q) => q.active);
  // Default: the last queue used on this PC, then the signed-in doctor's own queue; other staff (lab, reception)
  // start on the first room queue rather than some doctor's list.
  const queueId =
    (chosen && active.some((q) => q.id === chosen) ? chosen : undefined) ??
    active.find((q) => q.doctorId === me.data?.id)?.id ??
    active.find((q) => !q.doctorId)?.id ??
    active[0]?.id;
  const choose = (id: string) => {
    setChosen(id);
    storage.set(LAST_QUEUE, id);
  };

  if (setup) return <QueueSetup onClose={() => setSetup(false)} />;

  return (
    <>
      <PageHeader
        title={t("queues.title")}
        subtitle={t("queues.subtitle")}
        actions={
          can("SETTINGS_MANAGE") && (
            <Button variant="secondary" onClick={() => setSetup(true)}>
              <Settings2 /> {t("queues.setup")}
            </Button>
          )
        }
      />
      {queues.isPending ? (
        <Spinner />
      ) : queues.error ? (
        <ErrorState error={queues.error} onRetry={() => void queues.refetch()} />
      ) : active.length === 0 ? (
        <Card>
          <EmptyState text={t("queues.none")} action={can("SETTINGS_MANAGE") ? <p className="text-sm">{t("queues.noneHint")}</p> : undefined} />
        </Card>
      ) : (
        <>
          <div className="mb-5">
            <Segmented value={queueId ?? ""} onChange={choose} options={active.map((q) => ({ value: q.id, label: `${q.prefix} · ${q.name}` }))} />
          </div>
          {queueId && <TicketList queueId={queueId} />}
        </>
      )}
    </>
  );
}

function TicketList({ queueId }: { queueId: string }) {
  const { t } = useTranslation();
  const { data, isPending, error, refetch } = useQueueTickets(queueId);
  const done = useTicketDone();
  if (isPending) return <Spinner />;
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />;
  if (data.length === 0) return <Card><EmptyState text={t("queues.emptyToday")} /></Card>;

  const waiting = data.filter((x) => x.status === "WAITING");
  const toggle = (ticket: Ticket) =>
    done.mutate(
      { id: ticket.id, done: ticket.status === "WAITING", version: ticket.version },
      { onError: (e) => toast.error(errorMessage(t, e)) },
    );

  return (
    <Card>
      <div className="flex gap-6 border-b border-border px-5 py-3 text-sm text-muted-foreground">
        <span>{t("queues.waiting", { count: waiting.length })}</span>
        <span>{t("queues.seen", { count: data.length - waiting.length })}</span>
      </div>
      <ul className="divide-y divide-border">
        {data.map((x) => (
          <li key={x.id} className={cn("flex items-center gap-4 px-5 py-3", x.status === "DONE" && "opacity-55")}>
            <div className={cn("w-24 shrink-0 text-2xl font-bold tabular-nums", x === waiting[0] && "text-primary")}>{x.label}</div>
            <div className="min-w-0 flex-1">
              {x.patient ? (
                <Link to={`/patients/${x.patient.id}`} className="font-medium hover:underline">
                  {x.patient.fullName}
                </Link>
              ) : (
                <span>—</span>
              )}
              <div className="truncate text-sm text-muted-foreground">{x.lines.map((l) => l.serviceName).join(", ")}</div>
            </div>
            {x === waiting[0] && <Badge tone="blue">{t("queues.next")}</Badge>}
            <Button size="sm" variant={x.status === "WAITING" ? "secondary" : "ghost"} disabled={done.isPending} onClick={() => toggle(x)}>
              {x.status === "WAITING" ? <><Check /> {t("queues.markSeen")}</> : <><RotateCcw /> {t("queues.undoSeen")}</>}
            </Button>
          </li>
        ))}
      </ul>
    </Card>
  );
}
