import { Banknote, CreditCard, Landmark, Search, Wallet } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { EmptyState, ErrorState, FormError, PageHeader, Pagination, Spinner } from "@medclinic/web-ui/components/Common";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Card } from "@medclinic/web-ui/components/ui/card";
import { Input } from "@medclinic/web-ui/components/ui/input";
import { formatDateTime } from "@medclinic/web-ui/lib/format";
import { formatAmount } from "@medclinic/web-ui/lib/money";
import { cn } from "@medclinic/web-ui/lib/utils";
import { usePay, useUnpaidVisits } from "@/api/hooks";
import type { Payment, PaymentMethod, UnpaidVisit } from "@/api/types";
import { matchesSearch } from "@/features/catalog/search";
import { ReceiptDialog } from "./ReceiptDialog";

const METHODS: { value: PaymentMethod; icon: typeof Banknote }[] = [
  { value: "CASH", icon: Banknote },
  { value: "CARD", icon: CreditCard },
  { value: "TRANSFER", icon: Landmark },
];

/** Cashier's desk: unpaid visits on the left (refreshes by itself), the chosen visit's services and payment on the right. */
export function CashierPage() {
  const { t } = useTranslation();
  const [page, setPage] = React.useState(0);
  const [text, setText] = React.useState("");
  const [selectedId, setSelectedId] = React.useState<string | null>(null);
  const [receipt, setReceipt] = React.useState<Payment | null>(null);
  const { data, isPending, error, refetch } = useUnpaidVisits(page);

  const visible = (data?.items ?? []).filter((v) => matchesSearch(text, v.patient?.fullName, v.patient ? String(v.patient.cardNo) : null, String(v.visitNo)));
  const selected = data?.items.find((v) => v.visitId === selectedId) ?? null;

  return (
    <>
      <PageHeader title={t("cashier.title")} subtitle={t("cashier.subtitle")} />
      <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)]">
        <Card className="overflow-hidden">
          <div className="border-b border-border p-3">
            <div className="relative">
              <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
              <Input type="search" value={text} onChange={(e) => setText(e.target.value)} placeholder={t("cashier.searchPlaceholder")} className="pl-9" />
            </div>
          </div>
          {isPending ? (
            <Spinner />
          ) : error ? (
            <ErrorState error={error} onRetry={() => void refetch()} />
          ) : visible.length === 0 ? (
            <EmptyState text={t("cashier.nothingToPay")} />
          ) : (
            <>
              <ul className="divide-y divide-border" role="listbox" aria-label={t("cashier.title")}>
                {visible.map((v) => (
                  <li key={v.visitId} role="option" aria-selected={v.visitId === selectedId}>
                    <button
                      type="button"
                      onClick={() => setSelectedId(v.visitId)}
                      className={cn("flex w-full items-center gap-3 px-4 py-3 text-left transition-colors", v.visitId === selectedId ? "bg-primary/10" : "hover:bg-muted")}
                    >
                      <div className="min-w-0 flex-1">
                        <div className="truncate font-medium">{v.patient?.fullName ?? "—"}</div>
                        <div className="text-xs text-muted-foreground">
                          {t("patients.card")} {v.patient?.cardNo} · {t("visits.number", { no: v.visitNo })} · {formatDateTime(v.createdAt)}
                        </div>
                      </div>
                      <div className="text-right font-semibold tabular-nums">{formatAmount(v.total)}</div>
                    </button>
                  </li>
                ))}
              </ul>
              <Pagination page={page} totalPages={data.totalPages} onPage={setPage} />
            </>
          )}
        </Card>

        {selected ? (
          <PaymentPanel key={selected.visitId} visit={selected} onPaid={(p) => { setReceipt(p); setSelectedId(null); }} />
        ) : (
          <Card className="hidden lg:block">
            <EmptyState text={t("cashier.chooseVisit")} />
          </Card>
        )}
      </div>
      {receipt && <ReceiptDialog payment={receipt} onClose={() => setReceipt(null)} />}
    </>
  );
}

function PaymentPanel({ visit, onPaid }: { visit: UnpaidVisit; onPaid: (p: Payment) => void }) {
  const { t } = useTranslation();
  const pay = usePay();
  const [chosen, setChosen] = React.useState<Set<string>>(() => new Set(visit.items.map((i) => i.id)));
  const [method, setMethod] = React.useState<PaymentMethod>("CASH");

  // The list refreshes in the background: forget services that are no longer unpaid (paid elsewhere, cancelled).
  React.useEffect(() => {
    setChosen((c) => new Set([...c].filter((id) => visit.items.some((i) => i.id === id))));
  }, [visit.items]);

  const items = visit.items.filter((i) => chosen.has(i.id));
  const amount = items.reduce((s, i) => s + i.amount, 0);
  const toggle = (id: string, on: boolean) => setChosen((c) => { const n = new Set(c); if (on) n.add(id); else n.delete(id); return n; });

  return (
    <Card className="p-5">
      <div className="mb-4">
        <div className="text-lg font-semibold">{visit.patient?.fullName}</div>
        <div className="text-sm text-muted-foreground">
          {t("patients.card")} {visit.patient?.cardNo} · {t("visits.number", { no: visit.visitNo })}
        </div>
      </div>
      <ul className="mb-5 divide-y divide-border rounded-lg border border-border">
        {visit.items.map((i) => (
          <li key={i.id}>
            <label className="flex cursor-pointer items-center gap-3 px-4 py-3">
              <input type="checkbox" className="size-4 accent-primary" checked={chosen.has(i.id)} onChange={(e) => toggle(i.id, e.target.checked)} />
              <span className="min-w-0 flex-1">
                <span className="block font-medium">{i.serviceName}</span>
                {i.doctorName && <span className="block text-sm text-muted-foreground">{i.doctorName}</span>}
              </span>
              {i.allowed && <span className="text-xs font-medium text-sky-700">{t("visits.allowed")}</span>}
              <span className="text-right tabular-nums">
                <span className="block font-semibold">{formatAmount(i.amount)}</span>
                {i.discount > 0 && <span className="block text-xs text-muted-foreground line-through">{formatAmount(i.price)}</span>}
              </span>
            </label>
          </li>
        ))}
      </ul>

      <div className="mb-5 grid grid-cols-3 gap-2" role="radiogroup" aria-label={t("cashier.method")}>
        {METHODS.map(({ value, icon: Icon }) => (
          <button
            key={value}
            type="button"
            role="radio"
            aria-checked={method === value}
            onClick={() => setMethod(value)}
            className={cn(
              "flex flex-col items-center gap-1 rounded-xl border px-3 py-3 text-sm font-medium transition-colors",
              method === value ? "border-primary bg-primary/10 text-primary" : "border-border hover:bg-muted",
            )}
          >
            <Icon className="size-5" aria-hidden />
            {t(`paymentMethod.${value}`)}
          </button>
        ))}
      </div>

      <div className="mb-4 flex items-baseline justify-between rounded-xl bg-muted px-4 py-4">
        <span className="text-muted-foreground">{t("cashier.toReceive")}</span>
        <span className="text-3xl font-semibold tabular-nums">
          {formatAmount(amount)} <span className="text-base font-normal text-muted-foreground">{t("common.sum")}</span>
        </span>
      </div>
      <FormError error={pay.error} />
      <Button
        size="lg"
        className="w-full"
        disabled={items.length === 0 || pay.isPending}
        onClick={() =>
          pay.mutate({ visitId: visit.visitId, itemIds: items.map((i) => i.id), method, amount }, { onSuccess: onPaid })
        }
      >
        <Wallet /> {t("cashier.accept")}
      </Button>
    </Card>
  );
}
