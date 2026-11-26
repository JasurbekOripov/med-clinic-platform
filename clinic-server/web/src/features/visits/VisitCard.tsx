import { FilePen, HandHeart, Handshake, Percent, Plus, Receipt, Undo2, XCircle } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { Badge, type BadgeTone } from "@medclinic/web-ui/components/ui/badge";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Card } from "@medclinic/web-ui/components/ui/card";
import { Select } from "@medclinic/web-ui/components/ui/input";
import { errorMessage } from "@medclinic/web-ui/lib/errors";
import { formatDateTime } from "@medclinic/web-ui/lib/format";
import { formatAmount } from "@medclinic/web-ui/lib/money";
import { cn } from "@medclinic/web-ui/lib/utils";
import { useAllowUnpaid, useApproveRefund, useCancelVisitService, useDiscountTypes, useUpdateVisit } from "@/api/hooks";
import type { Visit, VisitItem, VisitItemStatus } from "@/api/types";
import { usePermissions } from "@/features/auth/permissions";
import { VisitPaymentsDialog } from "@/features/cashier/VisitPaymentsDialog";
import { OrderServicesDialog } from "./OrderServicesDialog";
import { ReasonDialog } from "./ReasonDialog";

const STATUS_TONE: Record<VisitItemStatus, BadgeTone> = { UNPAID: "amber", PAID: "green", CANCELLED: "gray", REFUNDED: "red" };

/** What the patient page passes down so doctors can write a conclusion for a service. */
export type ConclusionAction = { concluded: Set<string>; meId: string | undefined; write: (item: VisitItem) => void };

export function VisitCard({ visit, conclusions }: { visit: Visit; conclusions?: ConclusionAction }) {
  const { t } = useTranslation();
  const { can } = usePermissions();
  const cancel = useCancelVisitService();
  const allow = useAllowUnpaid();
  const [adding, setAdding] = React.useState(false);
  const [cancelling, setCancelling] = React.useState<VisitItem | null>(null);
  const [allowing, setAllowing] = React.useState(false);
  const [receipts, setReceipts] = React.useState(false);
  const [refunding, setRefunding] = React.useState<VisitItem | null>(null);
  const approveRefund = useApproveRefund();
  const unpaid = visit.items.filter((i) => i.status === "UNPAID");
  const allowable = unpaid.filter((i) => !i.allowed);

  return (
    <Card className="overflow-hidden">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 border-b border-border bg-muted/40 px-5 py-3">
        <div className="flex-1">
          <span className="font-semibold">{t("visits.number", { no: visit.visitNo })}</span>
          <span className="ml-2 text-sm text-muted-foreground">{formatDateTime(visit.createdAt)}</span>
        </div>
        {visit.agent && (
          <span className="inline-flex items-center gap-1 text-sm text-muted-foreground">
            <Handshake className="size-4" aria-hidden /> {visit.agent.name}
          </span>
        )}
        <DiscountControl visit={visit} />
        {can("VISIT_CREATE") && (
          <Button size="sm" variant="secondary" onClick={() => setAdding(true)}>
            <Plus /> {t("visits.addService")}
          </Button>
        )}
        {visit.paid > 0 && (can("PAYMENT_ACCEPT") || can("VISIT_CREATE")) && (
          <Button size="sm" variant="ghost" onClick={() => setReceipts(true)}>
            <Receipt /> {t("cashier.receipts")}
          </Button>
        )}
        {can("EMERGENCY_ALLOW") && allowable.length > 0 && (
          <Button size="sm" variant="ghost" onClick={() => setAllowing(true)}>
            <HandHeart /> {t("visits.allow")}
          </Button>
        )}
      </div>

      <ul className="divide-y divide-border">
        {visit.items.map((i) => (
          <li key={i.id} className={cn("flex items-center gap-4 px-5 py-3", i.status === "CANCELLED" && "opacity-60")}>
            <div className="min-w-0 flex-1">
              <div className={cn("font-medium", i.status === "CANCELLED" && "line-through")}>{i.serviceName}</div>
              <div className="text-sm text-muted-foreground">
                {[i.doctorName, i.groupName].filter(Boolean).join(" · ")}
                {i.cancelReason && ` · ${t("visits.cancelledBecause", { reason: i.cancelReason })}`}
              </div>
            </div>
            {conclusions && canConclude(i, conclusions) && (
              <Button size="sm" onClick={() => conclusions.write(i)}>
                <FilePen /> {t("conclusions.write")}
              </Button>
            )}
            {i.allowed && i.status === "UNPAID" && (
              <Badge tone="blue" title={i.allowReason ?? undefined}>{t("visits.allowed")}</Badge>
            )}
            <Badge tone={STATUS_TONE[i.status]}>{t(`visits.status.${i.status}`)}</Badge>
            <div className="w-32 text-right tabular-nums">
              <div className="font-semibold">{formatAmount(i.amount)}</div>
              {i.discount > 0 && <div className="text-xs text-muted-foreground line-through">{formatAmount(i.price)}</div>}
            </div>
            {can("REFUND") && i.status === "PAID" && (
              <Button size="sm" variant="ghost" className="text-danger" onClick={() => setRefunding(i)}>
                <Undo2 /> {t("refunds.refund")}
              </Button>
            )}
            {can("VISIT_EDIT_UNPAID") && (
              <Button
                size="icon"
                variant="ghost"
                aria-label={t("visits.cancelService")}
                disabled={i.status !== "UNPAID"}
                title={i.status === "PAID" ? t("visits.paidCannotCancel") : undefined}
                onClick={() => setCancelling(i)}
                className={cn(i.status !== "UNPAID" && "invisible")}
              >
                <XCircle />
              </Button>
            )}
          </li>
        ))}
      </ul>

      <div className="flex flex-wrap justify-end gap-x-8 gap-y-1 border-t border-border px-5 py-3 text-sm">
        <Total label={t("visits.total")} value={visit.total} />
        <Total label={t("visits.paid")} value={visit.paid} />
        <Total label={t("visits.unpaid")} value={visit.unpaid} strong={visit.unpaid > 0} />
      </div>

      {refunding && (
        <ReasonDialog
          title={t("refunds.confirm", { name: refunding.serviceName, amount: formatAmount(refunding.amount) })}
          description={t("refunds.hint")}
          confirmLabel={t("refunds.refund")}
          danger
          onClose={() => setRefunding(null)}
          onConfirm={(reason) => approveRefund.mutateAsync({ visitItemId: refunding.id, reason }).then(() => toast.success(t("refunds.approved")))}
        />
      )}
      {receipts && <VisitPaymentsDialog visit={visit} onClose={() => setReceipts(false)} />}
      {adding && <OrderServicesDialog patientId={visit.patientId} visit={visit} onClose={() => setAdding(false)} />}
      {cancelling && (
        <ReasonDialog
          title={t("visits.confirmCancel", { name: cancelling.serviceName })}
          confirmLabel={t("visits.cancelService")}
          danger
          onClose={() => setCancelling(null)}
          onConfirm={(reason) =>
            cancel.mutateAsync({ id: visit.id, itemId: cancelling.id, reason, version: visit.version }).then(() => toast.success(t("visits.saved")))
          }
        />
      )}
      {allowing && (
        <ReasonDialog
          title={t("visits.allowTitle")}
          description={t("visits.allowHint", { amount: formatAmount(allowable.reduce((s, i) => s + i.amount, 0)) })}
          confirmLabel={t("visits.allow")}
          onClose={() => setAllowing(false)}
          onConfirm={(reason) =>
            allow.mutateAsync({ id: visit.id, itemIds: allowable.map((i) => i.id), reason, version: visit.version }).then(() => toast.success(t("visits.saved")))
          }
        />
      )}
    </Card>
  );
}

/** Reception changes the visit's single discount while something is unpaid; paid services keep theirs. */
function DiscountControl({ visit }: { visit: Visit }) {
  const { can } = usePermissions();
  if (can("VISIT_EDIT_UNPAID") && visit.unpaid > 0) return <DiscountSelect visit={visit} />;
  return visit.discount ? (
    <span className="inline-flex items-center gap-1 text-sm text-muted-foreground">
      <Percent className="size-4" aria-hidden /> {visit.discount.name} — {visit.discount.percent}%
    </span>
  ) : null;
}

/** Separate component so the discount list is only loaded for people who may change it. */
function DiscountSelect({ visit }: { visit: Visit }) {
  const { t } = useTranslation();
  const discounts = useDiscountTypes();
  const update = useUpdateVisit();
  const options = (discounts.data ?? []).filter((d) => d.active || d.id === visit.discount?.typeId);
  return (
    <Select
      aria-label={t("visits.discount")}
      value={visit.discount?.typeId ?? ""}
      disabled={update.isPending}
      className="h-8 w-56 text-sm"
      onChange={(e) =>
        update.mutate(
          { id: visit.id, discountTypeId: e.target.value || undefined, agentId: visit.agent?.id, version: visit.version },
          { onSuccess: () => toast.success(t("visits.saved")), onError: (err) => toast.error(errorMessage(t, err)) },
        )
      }
    >
      <option value="">{t("visits.noDiscount")}</option>
      {visit.discount && !options.some((d) => d.id === visit.discount!.typeId) && (
        <option value={visit.discount.typeId}>{visit.discount.name} — {visit.discount.percent}%</option>
      )}
      {options.map((d) => (
        <option key={d.id} value={d.id}>{d.name} — {d.percent}%</option>
      ))}
    </Select>
  );
}

function Total({ label, value, strong }: { label: string; value: number; strong?: boolean }) {
  const { t } = useTranslation();
  return (
    <div className="text-right">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className={cn("tabular-nums", strong ? "text-base font-semibold text-amber-700" : "font-medium")}>
        {formatAmount(value)} {t("common.sum")}
      </div>
    </div>
  );
}

/** Paid (or Admin-allowed) service, not yet concluded, ordered with this doctor or with no doctor (the server checks too). */
function canConclude(i: VisitItem, c: ConclusionAction): boolean {
  const workable = i.status === "PAID" || (i.status === "UNPAID" && i.allowed);
  return workable && !c.concluded.has(i.id) && (!i.doctorId || i.doctorId === c.meId);
}
