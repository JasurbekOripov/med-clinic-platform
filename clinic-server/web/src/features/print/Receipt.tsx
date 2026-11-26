import type * as React from "react";
import { useTranslation } from "react-i18next";
import { formatDateTime, formatPhone } from "@medclinic/web-ui/lib/format";
import { formatAmount } from "@medclinic/web-ui/lib/money";
import type { Payment, PrintSettings } from "@/api/types";

/**
 * 80 mm cash receipt (≈ 42 characters per line on common thermal printers). Plain black text, no colours or
 * backgrounds, so it prints the same on thermal and laser printers.
 */
export function Receipt({ payment, clinic }: { payment: Payment; clinic: PrintSettings | undefined }) {
  const { t } = useTranslation();
  const discount = payment.lines.reduce((sum, l) => sum + l.discount, 0);
  return (
    <div className="mx-auto w-[74mm] font-mono text-[11px] leading-snug text-black">
      <div className="text-center">
        {clinic?.hasLogo && <img src="/api/v1/settings/logo" alt="" className="mx-auto mb-1 max-h-14 grayscale" />}
        <div className="text-[13px] font-bold">{clinic?.name}</div>
        {clinic?.legalName && <div>{clinic.legalName}</div>}
        {clinic?.address && <div>{clinic.address}</div>}
        {clinic?.phone && <div>{formatPhone(clinic.phone)}</div>}
        {clinic?.receiptHeader && <div className="mt-1 whitespace-pre-line">{clinic.receiptHeader}</div>}
      </div>
      <Rule />
      <Row left={`${t("receipt.number")} ${payment.receiptNo}`} right={formatDateTime(payment.createdAt)} />
      <Row left={t("receipt.visit")} right={`№ ${payment.visitNo}`} />
      {payment.patient && <Row left={t("receipt.patient")} right={`${payment.patient.fullName} (${payment.patient.cardNo})`} />}
      <Rule />
      {payment.lines.map((l, i) => (
        <div key={i} className="mb-1">
          <div className="font-semibold">{l.serviceName}</div>
          {l.doctorName && <div>{l.doctorName}</div>}
          <Row left={l.discount ? `${formatAmount(l.price)} − ${formatAmount(l.discount)}` : ""} right={formatAmount(l.amount)} />
        </div>
      ))}
      <Rule />
      {discount > 0 && <Row left={t("receipt.discount")} right={formatAmount(discount)} />}
      <Row left={<span className="text-[13px] font-bold">{t("receipt.total")}</span>} right={<span className="text-[13px] font-bold">{formatAmount(payment.amount)} {t("common.sum")}</span>} />
      <Row left={t("receipt.method")} right={t(`paymentMethod.${payment.method}`)} />
      <Row left={t("receipt.cashier")} right={payment.cashierName} />
      {clinic?.receiptFooter && (
        <>
          <Rule />
          <div className="whitespace-pre-line text-center">{clinic.receiptFooter}</div>
        </>
      )}
    </div>
  );
}

function Row({ left, right }: { left: React.ReactNode; right: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-2">
      <span className="min-w-0">{left}</span>
      <span className="shrink-0 text-right">{right}</span>
    </div>
  );
}

function Rule() {
  return <div className="my-1.5 border-t border-dashed border-black" />;
}
