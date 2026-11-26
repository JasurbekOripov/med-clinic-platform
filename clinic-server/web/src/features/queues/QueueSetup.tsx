import { ArrowLeft, Pencil, Plus, Stethoscope, Trash2 } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { EmptyState, ErrorState, FormError, PageHeader, Spinner } from "@medclinic/web-ui/components/Common";
import { ConfirmDialog } from "@medclinic/web-ui/components/ConfirmDialog";
import { Field } from "@medclinic/web-ui/components/Field";
import { Badge } from "@medclinic/web-ui/components/ui/badge";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Card } from "@medclinic/web-ui/components/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogTitle } from "@medclinic/web-ui/components/ui/dialog";
import { Input, Select } from "@medclinic/web-ui/components/ui/input";
import { fieldErrors } from "@medclinic/web-ui/lib/errors";
import { cn } from "@medclinic/web-ui/lib/utils";
import { useDoctors, useQueueMutations, useQueues, useServiceGroups, useServices } from "@/api/hooks";
import type { Queue } from "@/api/types";

/** Admin: which rooms exist, their letters, and which services or which doctor each room serves. */
export function QueueSetup({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation();
  const queues = useQueues();
  const services = useServices();
  const { remove } = useQueueMutations();
  const [editing, setEditing] = React.useState<Queue | "new" | null>(null);
  const [deleting, setDeleting] = React.useState<Queue | null>(null);
  const serviceName = new Map((services.data ?? []).map((s) => [s.id, s.name]));

  return (
    <>
      <button type="button" onClick={onClose} className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" /> {t("queues.title")}
      </button>
      <PageHeader
        title={t("queues.setupTitle")}
        subtitle={t("queues.setupSubtitle")}
        actions={
          <Button size="lg" onClick={() => setEditing("new")}>
            <Plus /> {t("queues.new")}
          </Button>
        }
      />
      {queues.isPending ? (
        <Spinner />
      ) : queues.error ? (
        <ErrorState error={queues.error} onRetry={() => void queues.refetch()} />
      ) : queues.data.length === 0 ? (
        <Card><EmptyState text={t("queues.none")} /></Card>
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {queues.data.map((q) => (
            <Card key={q.id} className={cn("p-5", !q.active && "opacity-60")}>
              <div className="flex items-start gap-3">
                <div className="flex size-12 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-xl font-bold text-primary">{q.prefix}</div>
                <div className="min-w-0 flex-1">
                  <div className="font-semibold">
                    {q.name} {!q.active && <Badge tone="gray" className="ml-1">{t("catalog.inactive")}</Badge>}
                  </div>
                  <div className="text-sm text-muted-foreground">
                    {[q.room && t("queues.room", { room: q.room }), q.doctorName].filter(Boolean).join(" · ") || "—"}
                  </div>
                </div>
                <Button size="icon" variant="ghost" aria-label={t("common.edit")} onClick={() => setEditing(q)}><Pencil /></Button>
                <Button size="icon" variant="ghost" aria-label={t("common.delete")} onClick={() => setDeleting(q)}><Trash2 /></Button>
              </div>
              <div className="mt-3 flex flex-wrap gap-1.5">
                {q.doctorId && (
                  <Badge tone="blue"><Stethoscope className="mr-1 size-3" aria-hidden />{t("queues.doctorServices")}</Badge>
                )}
                {q.serviceIds.map((id) => (
                  <Badge key={id} tone="gray">{serviceName.get(id) ?? "—"}</Badge>
                ))}
              </div>
            </Card>
          ))}
        </div>
      )}
      {editing && <QueueDialog queue={editing === "new" ? undefined : editing} all={queues.data ?? []} onClose={() => setEditing(null)} />}
      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(o) => !o && setDeleting(null)}
        title={deleting ? t("queues.confirmDelete", { name: deleting.name }) : ""}
        confirmLabel={t("common.delete")}
        danger
        onConfirm={async () => {
          if (!deleting) return;
          await remove.mutateAsync({ id: deleting.id, version: deleting.version });
          toast.success(t("common.saved"));
        }}
      />
    </>
  );
}

function QueueDialog({ queue, all, onClose }: { queue?: Queue; all: Queue[]; onClose: () => void }) {
  const { t } = useTranslation();
  const { save } = useQueueMutations();
  const doctors = useDoctors();
  const services = useServices();
  const groups = useServiceGroups();
  const [name, setName] = React.useState(queue?.name ?? "");
  const [prefix, setPrefix] = React.useState(queue?.prefix ?? "");
  const [room, setRoom] = React.useState(queue?.room ?? "");
  const [doctorId, setDoctorId] = React.useState(queue?.doctorId ?? "");
  const [serviceIds, setServiceIds] = React.useState<Set<string>>(new Set(queue?.serviceIds ?? []));
  const [active, setActive] = React.useState(queue?.active ?? true);
  const server = fieldErrors(t, save.error);

  // A service belongs to one queue; consultations follow the chosen doctor, so they are not listed here.
  const takenBy = new Map(all.filter((q) => q.id !== queue?.id).flatMap((q) => q.serviceIds.map((id) => [id, q.name] as const)));
  const doctorsWithQueue = new Set(all.filter((q) => q.id !== queue?.id && q.doctorId).map((q) => q.doctorId));
  const listed = (services.data ?? []).filter((s) => !s.perDoctor && (s.active || serviceIds.has(s.id)));
  const byGroup = (groups.data ?? []).map((g) => ({ group: g, items: listed.filter((s) => s.groupId === g.id) })).filter((x) => x.items.length > 0);

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent wide className="max-w-2xl">
        <DialogTitle>{queue ? t("queues.edit") : t("queues.new")}</DialogTitle>
        <form
          className="mt-5 space-y-5"
          onSubmit={(e) => {
            e.preventDefault();
            save.mutate(
              {
                id: queue?.id,
                body: { name, prefix, room: room.trim() || undefined, doctorId: doctorId || undefined, serviceIds: [...serviceIds], active, version: queue?.version },
              },
              { onSuccess: () => { toast.success(t("common.saved")); onClose(); } },
            );
          }}
        >
          <div className="grid gap-4 sm:grid-cols-[1fr_6rem_8rem]">
            <Field label={t("queues.name")} error={server.name} hint={t("queues.nameHint")}>
              <Input autoFocus value={name} maxLength={100} onChange={(e) => setName(e.target.value)} />
            </Field>
            <Field label={t("queues.prefix")} error={server.prefix}>
              <Input
                value={prefix}
                maxLength={2}
                onChange={(e) => setPrefix(e.target.value.toUpperCase().replace(/[^A-Z]/g, ""))}
                className="text-center text-lg font-bold uppercase"
              />
            </Field>
            <Field label={t("queues.roomLabel")} optional error={server.room}>
              <Input value={room} maxLength={50} onChange={(e) => setRoom(e.target.value)} />
            </Field>
          </div>
          <Field label={t("queues.doctor")} optional error={server.doctorId} hint={t("queues.doctorHint")}>
            <Select value={doctorId} onChange={(e) => setDoctorId(e.target.value)}>
              <option value="">{t("queues.noDoctor")}</option>
              {(doctors.data ?? []).map((d) => (
                <option key={d.id} value={d.id} disabled={doctorsWithQueue.has(d.id)}>{d.fullName}</option>
              ))}
              {queue?.doctorId && !(doctors.data ?? []).some((d) => d.id === queue.doctorId) && (
                <option value={queue.doctorId}>{queue.doctorName ?? t("catalog.formerDoctor")}</option>
              )}
            </Select>
          </Field>
          <div>
            <div className="mb-2 text-sm font-medium">{t("queues.services")}</div>
            <div className="max-h-64 overflow-y-auto rounded-lg border border-border">
              {byGroup.length === 0 ? (
                <p className="px-4 py-3 text-sm text-muted-foreground">{t("queues.noServices")}</p>
              ) : (
                byGroup.map(({ group, items }) => (
                  <div key={group.id}>
                    <div className="bg-muted/50 px-4 py-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{group.name}</div>
                    {items.map((s) => {
                      const other = takenBy.get(s.id);
                      return (
                        <label key={s.id} className={cn("flex items-center gap-3 px-4 py-2 text-sm", other ? "opacity-60" : "cursor-pointer")}>
                          <input
                            type="checkbox"
                            className="size-4 accent-primary"
                            disabled={Boolean(other)}
                            checked={serviceIds.has(s.id)}
                            onChange={(e) =>
                              setServiceIds((c) => {
                                const n = new Set(c);
                                if (e.target.checked) n.add(s.id);
                                else n.delete(s.id);
                                return n;
                              })
                            }
                          />
                          <span className="flex-1">{s.name}</span>
                          {other && <span className="text-xs text-muted-foreground">{other}</span>}
                        </label>
                      );
                    })}
                  </div>
                ))
              )}
            </div>
            {server.serviceIds && <p className="mt-1 text-xs font-medium text-danger">{server.serviceIds}</p>}
          </div>
          <label className="flex cursor-pointer items-center gap-3 text-sm">
            <input type="checkbox" className="size-4 accent-primary" checked={active} onChange={(e) => setActive(e.target.checked)} />
            {t("queues.activeLabel")}
          </label>
          <FormError error={save.error} />
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={onClose}>{t("common.cancel")}</Button>
            <Button type="submit" disabled={save.isPending || !name.trim() || !prefix || (!doctorId && serviceIds.size === 0)}>{t("common.save")}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
