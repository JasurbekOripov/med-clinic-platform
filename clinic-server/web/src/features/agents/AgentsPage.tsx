import { Handshake, Pencil, Plus, Trash2 } from "lucide-react";
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
import { Input, MoneyInput, PhoneInput, Textarea } from "@medclinic/web-ui/components/ui/input";
import { fieldErrors } from "@medclinic/web-ui/lib/errors";
import { formatPhone } from "@medclinic/web-ui/lib/format";
import { formatAmount } from "@medclinic/web-ui/lib/money";
import { cn } from "@medclinic/web-ui/lib/utils";
import { useAgentMutations, useAgents } from "@/api/hooks";
import type { Agent } from "@/api/types";

/** Admin's list of referral agents; each has a fixed fee owed for every paid visit they referred. */
export function AgentsPage() {
  const { t } = useTranslation();
  const { data, isPending, error, refetch } = useAgents();
  const { remove } = useAgentMutations();
  const [editing, setEditing] = React.useState<Agent | "new" | null>(null);
  const [deleting, setDeleting] = React.useState<Agent | null>(null);

  return (
    <>
      <PageHeader
        title={t("agents.title")}
        subtitle={t("agents.subtitle")}
        actions={
          <Button size="lg" onClick={() => setEditing("new")}>
            <Plus /> {t("agents.new")}
          </Button>
        }
      />
      {isPending ? (
        <Spinner />
      ) : error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <Card>
          {data.length === 0 ? (
            <EmptyState text={t("agents.empty")} />
          ) : (
            <ul className="divide-y divide-border">
              {data.map((a) => (
                <li key={a.id} className={cn("flex items-center gap-4 px-5 py-3", !a.active && "opacity-60")}>
                  <div className="flex size-10 shrink-0 items-center justify-center rounded-full bg-accent-warm">
                    <Handshake className="size-5" aria-hidden />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="font-medium">
                      {a.name} {!a.active && <Badge tone="gray" className="ml-2">{t("catalog.inactive")}</Badge>}
                    </div>
                    {a.phone && <div className="text-sm text-muted-foreground">{formatPhone(a.phone)}</div>}
                  </div>
                  <div className="text-right">
                    <div className="font-semibold tabular-nums">
                      {formatAmount(a.fee)} <span className="text-sm font-normal text-muted-foreground">{t("common.sum")}</span>
                    </div>
                    <div className="text-xs text-muted-foreground">{t("agents.perVisit")}</div>
                  </div>
                  <Button size="icon" variant="ghost" aria-label={t("common.edit")} onClick={() => setEditing(a)}>
                    <Pencil />
                  </Button>
                  <Button size="icon" variant="ghost" aria-label={t("common.delete")} onClick={() => setDeleting(a)}>
                    <Trash2 />
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </Card>
      )}
      {editing && <AgentDialog agent={editing === "new" ? undefined : editing} onClose={() => setEditing(null)} />}
      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(o) => !o && setDeleting(null)}
        title={deleting ? t("agents.confirmDelete", { name: deleting.name }) : ""}
        description={t("agents.deleteHint")}
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

function AgentDialog({ agent, onClose }: { agent?: Agent; onClose: () => void }) {
  const { t } = useTranslation();
  const { save } = useAgentMutations();
  const [name, setName] = React.useState(agent?.name ?? "");
  const [phone, setPhone] = React.useState(agent?.phone ? formatPhone(agent.phone) : "");
  const [fee, setFee] = React.useState<number | null>(agent?.fee ?? null);
  const [notes, setNotes] = React.useState(agent?.notes ?? "");
  const [active, setActive] = React.useState(agent?.active ?? true);
  const server = fieldErrors(t, save.error);

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogTitle>{agent ? t("agents.edit") : t("agents.new")}</DialogTitle>
        <form
          className="mt-5 space-y-5"
          onSubmit={(e) => {
            e.preventDefault();
            save.mutate(
              {
                id: agent?.id,
                body: { name, phone: phone.trim() || undefined, fee: fee ?? undefined, notes: notes.trim() || undefined, active, version: agent?.version },
              },
              { onSuccess: () => { toast.success(t("common.saved")); onClose(); } },
            );
          }}
        >
          <Field label={t("agents.name")} error={server.name} hint={t("agents.nameHint")}>
            <Input autoFocus value={name} maxLength={200} onChange={(e) => setName(e.target.value)} />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label={t("common.phone")} optional error={server.phone}>
              <PhoneInput value={phone} onChange={(e) => setPhone(e.target.value)} />
            </Field>
            <Field label={t("agents.fee")} error={server.fee} hint={t("agents.feeHint")}>
              <MoneyInput value={fee} onValueChange={setFee} suffix={t("common.sum")} />
            </Field>
          </div>
          <Field label={t("patients.notes")} optional error={server.notes}>
            <Textarea value={notes} maxLength={1000} onChange={(e) => setNotes(e.target.value)} />
          </Field>
          <label className="flex cursor-pointer items-center gap-3 text-sm">
            <input type="checkbox" className="size-4 accent-primary" checked={active} onChange={(e) => setActive(e.target.checked)} />
            {t("agents.activeLabel")}
          </label>
          <FormError error={save.error} />
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={onClose}>{t("common.cancel")}</Button>
            <Button type="submit" disabled={save.isPending || !name.trim() || fee === null}>{t("common.save")}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
