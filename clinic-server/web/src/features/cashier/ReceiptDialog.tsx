import { Printer } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogTitle } from "@medclinic/web-ui/components/ui/dialog";
import { usePrintSettings } from "@/api/hooks";
import type { Payment } from "@/api/types";
import { printDocument } from "@/features/print/print";
import { PrintPortal } from "@/features/print/PrintPortal";
import { Receipt } from "@/features/print/Receipt";

/** Shows the receipt as it will print; Enter prints (the print button has focus). */
export function ReceiptDialog({ payment, onClose }: { payment: Payment; onClose: () => void }) {
  const { t } = useTranslation();
  const clinic = usePrintSettings();
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogTitle>{t("cashier.receiptTitle", { no: payment.receiptNo })}</DialogTitle>
        <div className="mt-4 max-h-[60vh] overflow-y-auto rounded-lg border border-border bg-white p-4">
          <Receipt payment={payment} clinic={clinic.data} />
        </div>
        <DialogFooter>
          <Button type="button" variant="ghost" onClick={onClose}>{t("common.close")}</Button>
          <Button type="button" size="lg" autoFocus onClick={() => printDocument("receipt80")} disabled={clinic.isPending}>
            <Printer /> {t("common.print")}
          </Button>
        </DialogFooter>
        <PrintPortal>
          <Receipt payment={payment} clinic={clinic.data} />
        </PrintPortal>
      </DialogContent>
    </Dialog>
  );
}
