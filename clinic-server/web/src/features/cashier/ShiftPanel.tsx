import { Banknote, FileText, Lock, LogIn, MinusCircle, Undo2 } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { FormError, Spinner } from "@medclinic/web-ui/components/Common";
import { Field } from "@medclinic/web-ui/components/Field";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Card } from "@medclinic/web-ui/components/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogTitle } from "@medclinic/web-ui/components/ui/dialog";
import { Input, MoneyInput, Select, Textarea } from "@medclinic/web-ui/components/ui/input";
import { errorMessage, fieldErrors } from "@medclinic/web-ui/lib/errors";
import { formatDateTime } from "@medclinic/web-ui/lib/format";
import { formatAmount } from "@medclinic/web-ui/lib/money";
import { cn } from "@medclinic/web-ui/lib/utils";
import { useCurrentShift, useExpenseRecipients, usePendingRefunds, usePrintSettings, useShiftActions } from "@/api/hooks";
import type { Shift } from "@/api/types";
import { usePermissions } from "@/features/auth/permissions";
import { printDocument } from "@/features/print/print";
import { PrintPortal } from "@/features/print/PrintPortal";
import { ShiftReportSlip } from "@/features/print/ShiftReportSlip";

/** Prints an X/Z report on the receipt printer and keeps it in the print area until the print finishes. */
export function usePrintShift() {
  const clinic = usePrintSettings();
  const [shift, setShift] = React.useState<Shift | null>(null);
  React.useEffect(() => {
    if (!shift) return;
    const done = () => setShift(null);
    window.addEventListener("afterprint", done, { once: true });
    const frame = requestAnimationFrame(() => printDocument("receipt80"));
    return () => { cancelAnimationFrame(frame); window.removeEventListener("afterprint", done); };
  }, [shift]);
  return { print: setShift, portal: shift ? <PrintPortal><ShiftReportSlip shift={shift} clinic={clinic.data} /></PrintPortal> : null };
}

/** The cashier's shift: open it, follow the money, record expenses, give refunds back, close with a Z report. */
export function ShiftPanel() {
  const { t } = useTranslation();
  const { can } = usePermissions();
  const current = useCurrentShift();
  const { open } = useShiftActions();
  const [opening, setOpening] = React.useState<number | null>(null);
  const [expense, setExpense] = React.useState(false);
  const [closing, setClosing] = React.useState(false);
  const { print, portal } = usePrintShift();

  if (!can("SHIFT_MANAGE")) return null;
  if (current.isPending) return <Spinner />;
  const shift = current.data;

  if (!shift) {
    return (
      <>
      {portal}
      <Card className="mb-5 flex flex-wrap items-end gap-4 border-amber-300 bg-amber-50/60 p-5">
        <div className="flex-1">
          <div className="flex items-center gap-2 font-semibold"><Lock className="size-4" aria-hidden /> {t("shift.closedTitle")}</div>
          <p className="text-sm text-muted-foreground">{t("shift.closedHint")}</p>
        </div>
        <Field label={t("shift.openingCash")} hint={t("shift.openingHint")}>
          <MoneyInput value={opening} onValueChange={setOpening} suffix={t("common.sum")} className="w-48" />
        </Field>
        <Button size="lg" disabled={open.isPending} onClick={() => open.mutate(opening, { onSuccess: () => toast.success(t("shift.opened")), onError: (e) => toast.error(errorMessage(t, e)) })}>
          <LogIn /> {t("shift.open")}
        </Button>
      </Card>
      </>
    );
  }

  return (
    <>
      <Card className="mb-5 p-4">
        <div className="flex flex-wrap items-center gap-3">
          <div className="min-w-0 flex-1">
            <div className="font-semibold">{t("shift.number", { no: shift.shiftNo })} · {shift.cashierName}</div>
            <div className="text-sm text-muted-foreground">{t("shift.openedSince", { time: formatDateTime(shift.openedAt) })}</div>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" onClick={() => setExpense(true)}><MinusCircle /> {t("shift.expense")}</Button>
            <Button variant="ghost" onClick={() => print(shift)}><FileText /> {t("shift.xReport")}</Button>
            <Button variant="secondary" onClick={() => setClosing(true)}><Lock /> {t("shift.close")}</Button>
          </div>
        </div>
        <div className="mt-3 grid grid-cols-2 gap-3 border-t border-border pt-3 sm:grid-cols-5">
          <Stat label={t("paymentMethod.CASH")} value={shift.totals.cash} />
          <Stat label={t("paymentMethod.CARD")} value={shift.totals.card} />
          <Stat label={t("paymentMethod.TRANSFER")} value={shift.totals.transfer} />
          <Stat label={t("shift.expensesShort")} value={shift.totals.expenses + shift.totals.refunds} />
          <Stat label={t("shift.expectedCash")} value={shift.expectedCash} strong />
        </div>
      </Card>
      <PendingRefunds />
      {expense && <ExpenseDialog onClose={() => setExpense(false)} />}
      {closing && <CloseDialog shift={shift} onClose={() => setClosing(false)} onClosed={(z) => { setClosing(false); print(z); }} />}
      {portal}
    </>
  );
}

function Stat({ label, value, strong }: { label: string; value: number; strong?: boolean }) {
  return (
    <div>
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className={cn("tabular-nums", strong ? "text-lg font-semibold" : "font-medium")}>{formatAmount(value)}</div>
    </div>
  );
}

/** Refunds approved by the Admin: the cashier hands the cash back and confirms. */
function PendingRefunds() {
  const { t } = useTranslation();
  const { data } = usePendingRefunds(true);
  const { payOut } = useShiftActions();
  if (!data || data.length === 0) return null;
  return (
    <Card className="mb-5 overflow-hidden border-red-200">
      <div className="flex items-center gap-2 border-b border-red-200 bg-red-50 px-5 py-2.5 text-sm font-semibold text-red-800">
        <Undo2 className="size-4" aria-hidden /> {t("refunds.pendingTitle", { count: data.length })}
      </div>
      <ul className="divide-y divide-border">
        {data.map((r) => (
          <li key={r.id} className="flex flex-wrap items-center gap-4 px-5 py-3">
            <div className="min-w-0 flex-1">
              <div className="font-medium">{r.patient?.fullName} · {r.serviceName}</div>
              <div className="text-sm text-muted-foreground">{t("refunds.approvedBy", { name: r.approvedByName, time: formatDateTime(r.approvedAt) })} · {r.reason}</div>
            </div>
            <div className="text-lg font-semibold tabular-nums">{formatAmount(r.amount)} <span className="text-sm font-normal text-muted-foreground">{t("common.sum")}</span></div>
            <Button
              disabled={payOut.isPending}
              onClick={() => payOut.mutate({ id: r.id, version: r.version }, { onSuccess: () => toast.success(t("refunds.paidOut")), onError: (e) => toast.error(errorMessage(t, e)) })}
            >
              <Banknote /> {t("refunds.payOut")}
            </Button>
          </li>
        ))}
      </ul>
    </Card>
  );
}

const OTHER = "__other__";

function ExpenseDialog({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation();
  const { expense } = useShiftActions();
  const staff = useExpenseRecipients();
  const [who, setWho] = React.useState("");
  const [name, setName] = React.useState("");
  const [amount, setAmount] = React.useState<number | null>(null);
  const [comment, setComment] = React.useState("");
  const errors = fieldErrors(t, expense.error);
  const valid = (who && (who !== OTHER || name.trim())) && amount !== null && amount > 0 && comment.trim();
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogTitle>{t("shift.expenseTitle")}</DialogTitle>
        <form
          className="mt-5 space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            expense.mutate(
              { recipientStaffId: who !== OTHER ? who : undefined, recipientName: who === OTHER ? name : undefined, amount, comment },
              { onSuccess: () => { toast.success(t("common.saved")); onClose(); } },
            );
          }}
        >
          <Field label={t("shift.recipient")} error={errors.recipientStaffId ?? errors.recipientName}>
            <Select value={who} onChange={(e) => setWho(e.target.value)}>
              <option value="">—</option>
              {(staff.data ?? []).map((s) => <option key={s.id} value={s.id}>{s.fullName}</option>)}
              <option value={OTHER}>{t("shift.recipientOther")}</option>
            </Select>
          </Field>
          {who === OTHER && (
            <Field label={t("shift.recipientName")}>
              <Input autoFocus value={name} maxLength={200} onChange={(e) => setName(e.target.value)} />
            </Field>
          )}
          <Field label={t("shift.amount")} error={errors.amount}>
            <MoneyInput value={amount} onValueChange={setAmount} suffix={t("common.sum")} />
          </Field>
          <Field label={t("shift.comment")} error={errors.comment} hint={t("shift.commentHint")}>
            <Textarea value={comment} maxLength={1000} onChange={(e) => setComment(e.target.value)} />
          </Field>
          <FormError error={expense.error} />
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={onClose}>{t("common.cancel")}</Button>
            <Button type="submit" disabled={!valid || expense.isPending}>{t("common.save")}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function CloseDialog({ shift, onClose, onClosed }: { shift: Shift; onClose: () => void; onClosed: (z: Shift) => void }) {
  const { t } = useTranslation();
  const { close } = useShiftActions();
  const [counted, setCounted] = React.useState<number | null>(null);
  const [note, setNote] = React.useState("");
  const difference = counted === null ? null : counted - shift.expectedCash;
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogTitle>{t("shift.closeTitle", { no: shift.shiftNo })}</DialogTitle>
        <form
          className="mt-5 space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            // mutateAsync: closing removes the open shift (and this dialog) on refresh; the Z report must still print.
            void close.mutateAsync({ id: shift.id, countedCash: counted, note: note || undefined, version: shift.version }).then(onClosed, () => undefined);
          }}
        >
          <div className="flex items-baseline justify-between rounded-lg bg-muted px-4 py-3">
            <span className="text-sm text-muted-foreground">{t("shift.expectedCash")}</span>
            <span className="text-xl font-semibold tabular-nums">{formatAmount(shift.expectedCash)} {t("common.sum")}</span>
          </div>
          <Field label={t("shift.countedCash")} hint={t("shift.countedHint")}>
            <MoneyInput autoFocus value={counted} onValueChange={setCounted} suffix={t("common.sum")} />
          </Field>
          {difference !== null && (
            <div className={cn("rounded-lg px-4 py-2 text-sm font-medium", difference === 0 ? "bg-emerald-50 text-emerald-800" : difference < 0 ? "bg-red-50 text-red-800" : "bg-amber-50 text-amber-800")}>
              {difference === 0 ? t("shift.exact") : difference < 0 ? t("shift.shortage", { amount: formatAmount(-difference) }) : t("shift.surplus", { amount: formatAmount(difference) })}
            </div>
          )}
          <Field label={t("shift.closeNote")} optional>
            <Textarea value={note} maxLength={500} onChange={(e) => setNote(e.target.value)} />
          </Field>
          <FormError error={close.error} />
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={onClose}>{t("common.cancel")}</Button>
            <Button type="submit" size="lg" disabled={counted === null || close.isPending}><Lock /> {t("shift.closeAndPrint")}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
