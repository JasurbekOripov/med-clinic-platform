import { Printer, Ticket as TicketIcon } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogTitle } from "@medclinic/web-ui/components/ui/dialog";
import { usePrintSettings } from "@/api/hooks";
import type { Payment } from "@/api/types";
import { printDocument } from "@/features/print/print";
import { PrintPortal } from "@/features/print/PrintPortal";
import { Receipt } from "@/features/print/Receipt";
import { Slip } from "@/features/print/Slip";

/**
 * After payment: one slip per room (queue number) and one money receipt, printed in one go; each is its own page so
 * thermal printers cut between them. Enter prints (the print button has focus).
 */
export function ReceiptDialog({ payment, onClose }: { payment: Payment; onClose: () => void }) {
  const { t } = useTranslation();
  const clinic = usePrintSettings();
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogTitle>{t("cashier.receiptTitle", { no: payment.receiptNo })}</DialogTitle>
        {payment.slips.length > 0 && (
          <div className="mt-4 flex flex-wrap gap-2" aria-label={t("queues.slips")}>
            {payment.slips.map((s) => (
              <span key={s.id} className="inline-flex items-center gap-2 rounded-lg bg-primary/10 px-3 py-2 text-sm">
                <TicketIcon className="size-4 text-primary" aria-hidden />
                <span className="text-lg font-bold">{s.label}</span>
                <span className="text-muted-foreground">{s.queueName}{s.room ? ` · ${t("queues.room", { room: s.room })}` : ""}</span>
              </span>
            ))}
          </div>
        )}
        <div className="mt-4 max-h-[50vh] overflow-y-auto rounded-lg border border-border bg-white p-4">
          <Receipt payment={payment} clinic={clinic.data} />
        </div>
        <DialogFooter>
          <Button type="button" variant="ghost" onClick={onClose}>{t("common.close")}</Button>
          <Button type="button" size="lg" autoFocus onClick={() => printDocument("receipt80")} disabled={clinic.isPending}>
            <Printer /> {payment.slips.length > 0 ? t("cashier.printAll", { count: payment.slips.length + 1 }) : t("common.print")}
          </Button>
        </DialogFooter>
        <PrintPortal>
          {payment.slips.map((s) => (
            <div key={s.id} className="print-page">
              <Slip slip={s} payment={payment} clinic={clinic.data} />
            </div>
          ))}
          <div className="print-page">
            <Receipt payment={payment} clinic={clinic.data} />
          </div>
        </PrintPortal>
      </DialogContent>
    </Dialog>
  );
}
