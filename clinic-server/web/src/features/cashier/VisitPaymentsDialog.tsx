import { Printer } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { EmptyState, ErrorState, Spinner } from "@medclinic/web-ui/components/Common";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogTitle } from "@medclinic/web-ui/components/ui/dialog";
import { errorMessage } from "@medclinic/web-ui/lib/errors";
import { formatDateTime } from "@medclinic/web-ui/lib/format";
import { formatAmount } from "@medclinic/web-ui/lib/money";
import { toast } from "sonner";
import { useReprint, useVisitPayments } from "@/api/hooks";
import type { Payment, Visit } from "@/api/types";
import { ReceiptDialog } from "./ReceiptDialog";

/** Payments of one visit; a reprint is recorded on the server and printed marked "COPY". */
export function VisitPaymentsDialog({ visit, onClose }: { visit: Visit; onClose: () => void }) {
  const { t } = useTranslation();
  const { data, isPending, error, refetch } = useVisitPayments(visit.id);
  const reprint = useReprint();
  const [copy, setCopy] = React.useState<Payment | null>(null);

  if (copy) return <ReceiptDialog payment={copy} copy onClose={() => setCopy(null)} />;

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogTitle>{t("cashier.visitReceipts", { no: visit.visitNo })}</DialogTitle>
        <div className="mt-4">
          {isPending ? (
            <Spinner />
          ) : error ? (
            <ErrorState error={error} onRetry={() => void refetch()} />
          ) : data.length === 0 ? (
            <EmptyState text={t("cashier.noReceipts")} />
          ) : (
            <ul className="divide-y divide-border rounded-lg border border-border">
              {data.map((p) => (
                <li key={p.id} className="flex items-center gap-3 px-4 py-3">
                  <div className="min-w-0 flex-1">
                    <div className="font-medium">{t("cashier.receiptTitle", { no: p.receiptNo })}</div>
                    <div className="text-sm text-muted-foreground">
                      {formatDateTime(p.createdAt)} · {t(`paymentMethod.${p.method}`)} · {p.slips.map((s) => s.label).join(", ") || "—"}
                    </div>
                  </div>
                  <div className="font-semibold tabular-nums">{formatAmount(p.amount)}</div>
                  <Button
                    size="sm"
                    variant="secondary"
                    disabled={reprint.isPending}
                    onClick={() => reprint.mutate(p.id, { onSuccess: setCopy, onError: (e) => toast.error(errorMessage(t, e)) })}
                  >
                    <Printer /> {t("cashier.reprint")}
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </div>
        <DialogFooter>
          <Button type="button" variant="ghost" onClick={onClose}>{t("common.close")}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
