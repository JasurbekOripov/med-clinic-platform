import { FileText } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { EmptyState, ErrorState, Spinner } from "@medclinic/web-ui/components/Common";
import { Badge } from "@medclinic/web-ui/components/ui/badge";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Card } from "@medclinic/web-ui/components/ui/card";
import { Input } from "@medclinic/web-ui/components/ui/input";
import { formatDateTime, todayInTashkent } from "@medclinic/web-ui/lib/format";
import { formatAmount } from "@medclinic/web-ui/lib/money";
import { cn } from "@medclinic/web-ui/lib/utils";
import { fetchShift, useDebts, useShifts } from "@/api/hooks";
import { usePrintShift } from "./ShiftPanel";

/** Services the Admin allowed before payment and still unpaid. */
export function DebtsList() {
  const { t } = useTranslation();
  const { data, isPending, error, refetch } = useDebts();
  if (isPending) return <Spinner />;
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />;
  if (data.length === 0) return <Card><EmptyState text={t("debts.none")} /></Card>;
  const total = data.reduce((s, d) => s + d.total, 0);
  return (
    <Card className="overflow-hidden">
      <div className="flex justify-between border-b border-border bg-muted/40 px-5 py-2.5 text-sm font-semibold">
        <span>{t("debts.count", { count: data.length })}</span>
        <span className="tabular-nums">{formatAmount(total)} {t("common.sum")}</span>
      </div>
      <ul className="divide-y divide-border">
        {data.map((d) => (
          <li key={d.visitId} className="flex items-center gap-4 px-5 py-3">
            <div className="min-w-0 flex-1">
              <div className="font-medium">{d.patient?.fullName ?? "—"}</div>
              <div className="text-sm text-muted-foreground">
                {t("patients.card")} {d.patient?.cardNo} · {t("visits.number", { no: d.visitNo })} · {formatDateTime(d.createdAt)} · {d.items.map((i) => i.serviceName).join(", ")}
              </div>
            </div>
            <div className="font-semibold tabular-nums">{formatAmount(d.total)}</div>
          </li>
        ))}
      </ul>
    </Card>
  );
}

/** Admin: every cashier's shifts for a period, with shortages and surpluses; reprint any X/Z report. */
export function ShiftsReport() {
  const { t } = useTranslation();
  const today = todayInTashkent();
  const [from, setFrom] = React.useState(today);
  const [to, setTo] = React.useState(today);
  const { data, isPending, error, refetch } = useShifts(from, to);
  const { print, portal } = usePrintShift();
  return (
    <>
      <div className="mb-4 flex flex-wrap items-center gap-2 text-sm">
        <Input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value || today)} className="w-44" aria-label={t("shift.from")} />
        <span>—</span>
        <Input type="date" value={to} min={from} onChange={(e) => setTo(e.target.value || today)} className="w-44" aria-label={t("shift.to")} />
      </div>
      {isPending ? (
        <Spinner />
      ) : error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : data.length === 0 ? (
        <Card><EmptyState text={t("shift.noneInPeriod")} /></Card>
      ) : (
        <Card className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-muted/50 text-left text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-4 py-2">{t("shift.shift")}</th>
                <th className="px-4 py-2">{t("shift.openedAt")}</th>
                <th className="px-4 py-2 text-right">{t("paymentMethod.CASH")}</th>
                <th className="px-4 py-2 text-right">{t("paymentMethod.CARD")}</th>
                <th className="px-4 py-2 text-right">{t("paymentMethod.TRANSFER")}</th>
                <th className="px-4 py-2 text-right">{t("shift.expensesShort")}</th>
                <th className="px-4 py-2 text-right">{t("shift.difference")}</th>
                <th className="px-4 py-2" />
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {data.map((s) => (
                <tr key={s.id}>
                  <td className="px-4 py-2"><div className="font-medium">№{s.shiftNo} · {s.cashierName}</div>{s.status === "OPEN" && <Badge tone="blue">{t("shift.openBadge")}</Badge>}</td>
                  <td className="px-4 py-2 text-muted-foreground">{formatDateTime(s.openedAt)}{s.closedAt && <> — {formatDateTime(s.closedAt)}</>}</td>
                  <td className="px-4 py-2 text-right tabular-nums">{formatAmount(s.totals.cash)}</td>
                  <td className="px-4 py-2 text-right tabular-nums">{formatAmount(s.totals.card)}</td>
                  <td className="px-4 py-2 text-right tabular-nums">{formatAmount(s.totals.transfer)}</td>
                  <td className="px-4 py-2 text-right tabular-nums">{formatAmount(s.totals.expenses + s.totals.refunds)}</td>
                  <td className={cn("px-4 py-2 text-right font-semibold tabular-nums", s.difference != null && s.difference < 0 && "text-red-700", s.difference != null && s.difference > 0 && "text-amber-700")}>
                    {s.difference == null ? "—" : formatAmount(s.difference)}
                  </td>
                  <td className="px-4 py-2 text-right">
                    <Button size="sm" variant="ghost" onClick={() => void fetchShift(s.id).then(print)}><FileText /> {t(s.status === "OPEN" ? "shift.xReport" : "shift.zReport")}</Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
      {portal}
    </>
  );
}
