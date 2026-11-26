import type * as React from "react";
import { useTranslation } from "react-i18next";
import { formatDateTime } from "@medclinic/web-ui/lib/format";
import { formatAmount } from "@medclinic/web-ui/lib/money";
import type { PrintSettings, Shift } from "@/api/types";

/** X report (shift open) or Z report (closed) on the 80 mm receipt printer. */
export function ShiftReportSlip({ shift, clinic }: { shift: Shift; clinic: PrintSettings | undefined }) {
  const { t } = useTranslation();
  const z = shift.status === "CLOSED";
  const s = shift.totals;
  return (
    <div className="mx-auto w-[74mm] font-mono text-[11px] leading-snug text-black">
      <div className="text-center">
        <div className="text-[13px] font-bold">{clinic?.name}</div>
        <div className="mt-1 text-[13px] font-bold">{t(z ? "shift.zReport" : "shift.xReport")}</div>
        <div>{t("shift.number", { no: shift.shiftNo })} · {shift.cashierName}</div>
      </div>
      <Rule />
      <Row left={t("shift.openedAt")} right={formatDateTime(shift.openedAt)} />
      {z && <Row left={t("shift.closedAt")} right={formatDateTime(shift.closedAt)} />}
      {!z && <Row left={t("shift.printedAt")} right={formatDateTime(new Date().toISOString())} />}
      <Rule />
      <Row left={t("shift.payments", { count: s.payments })} right="" />
      <Row left={`  ${t("paymentMethod.CASH")}`} right={formatAmount(s.cash)} />
      <Row left={`  ${t("paymentMethod.CARD")}`} right={formatAmount(s.card)} />
      <Row left={`  ${t("paymentMethod.TRANSFER")}`} right={formatAmount(s.transfer)} />
      <Row left={t("shift.allPayments")} right={formatAmount(s.cash + s.card + s.transfer)} />
      <Rule />
      <Row left={t("shift.openingCash")} right={formatAmount(shift.openingCash)} />
      <Row left={`+ ${t("shift.cashIn")}`} right={formatAmount(s.cash)} />
      <Row left={`− ${t("shift.refunds", { count: s.refundCount })}`} right={formatAmount(s.refunds)} />
      <Row left={`− ${t("shift.expenses", { count: s.expenseCount })}`} right={formatAmount(s.expenses)} />
      <Row left={<b>{t("shift.expectedCash")}</b>} right={<b>{formatAmount(shift.expectedCash)}</b>} />
      {z && (
        <>
          <Row left={t("shift.countedCash")} right={formatAmount(shift.countedCash)} />
          <Row left={<b>{t("shift.difference")}</b>} right={<b>{formatAmount(shift.difference)}</b>} />
          {shift.closeNote && <div className="mt-1 whitespace-pre-line">{shift.closeNote}</div>}
        </>
      )}
      {shift.expenses.length > 0 && (
        <>
          <Rule />
          <div className="font-bold">{t("shift.expensesTitle")}</div>
          {shift.expenses.map((e) => (
            <div key={e.id} className="mb-0.5">
              <Row left={e.recipientName} right={formatAmount(e.amount)} />
              <div className="text-[10px]">{e.comment}</div>
            </div>
          ))}
        </>
      )}
      {shift.refunds.length > 0 && (
        <>
          <Rule />
          <div className="font-bold">{t("shift.refundsTitle")}</div>
          {shift.refunds.map((r) => <Row key={r.id} left={`${r.serviceName}${r.patient ? ` (${r.patient.cardNo})` : ""}`} right={formatAmount(r.amount)} />)}
        </>
      )}
      <Rule />
      <div className="mt-4 flex justify-between"><span>{t("shift.cashierSign")}</span><span>__________</span></div>
    </div>
  );
}

function Row({ left, right }: { left: React.ReactNode; right: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-2">
      <span className="min-w-0">{left}</span>
      <span className="shrink-0 text-right tabular-nums">{right}</span>
    </div>
  );
}

function Rule() {
  return <div className="my-1.5 border-t border-dashed border-black" />;
}
