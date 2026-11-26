import { useTranslation } from "react-i18next";
import { formatDate } from "@medclinic/web-ui/lib/format";
import type { Payment, PrintSettings, TicketSlip } from "@/api/types";

/** Room slip (talon): the queue number large enough to read across a waiting room, then the room and its services. */
export function Slip({ slip, payment, clinic }: { slip: TicketSlip; payment: Payment; clinic: PrintSettings | undefined }) {
  const { t } = useTranslation();
  return (
    <div className="mx-auto w-[74mm] text-center font-mono text-[11px] leading-snug text-black">
      <div className="text-[12px] font-bold">{clinic?.name}</div>
      <div className="mt-1 text-[13px] font-semibold">{slip.queueName}</div>
      {slip.room && <div>{t("queues.room", { room: slip.room })}</div>}
      <div className="my-2 text-[44px] font-bold leading-none tracking-wide">{slip.label}</div>
      <div>{formatDate(slip.day)}</div>
      {payment.patient && <div className="mt-1 font-semibold">{payment.patient.fullName} ({payment.patient.cardNo})</div>}
      <div className="my-1.5 border-t border-dashed border-black" />
      <div className="text-left">
        {slip.lines.map((l, i) => (
          <div key={i} className="mb-0.5">
            <div>{l.serviceName}</div>
            {l.doctorName && <div className="text-[10px]">{l.doctorName}</div>}
          </div>
        ))}
      </div>
      <div className="mt-1.5 text-[10px]">{t("queues.slipHint")}</div>
    </div>
  );
}
