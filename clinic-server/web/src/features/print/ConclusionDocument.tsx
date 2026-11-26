import * as React from "react";
import { useTranslation } from "react-i18next";
import { formatDate, formatDateTime, formatPhone } from "@medclinic/web-ui/lib/format";
import type { Conclusion, Patient, PrintSettings } from "@/api/types";
import { ageOn } from "@/features/patients/age";

/**
 * A4 conclusion on the clinic's letterhead: fixed header and footer (filled in automatically), the doctor's free text
 * in between. Black on white, so it copies and faxes well.
 */
export function ConclusionDocument({ conclusion, patient, clinic }: { conclusion: Conclusion; patient: Patient; clinic: PrintSettings | undefined }) {
  const { t } = useTranslation();
  const [signature, setSignature] = React.useState(true);
  return (
    <article className="mx-auto max-w-[180mm] font-serif text-[12pt] leading-relaxed text-black">
      <header className="flex items-center gap-4 border-b-2 border-black pb-3">
        {clinic?.hasLogo && <img src="/api/v1/settings/logo" alt="" className="max-h-20 max-w-32 object-contain" />}
        <div className="flex-1">
          <div className="text-[16pt] font-bold">{clinic?.name}</div>
          {clinic?.legalName && <div className="text-[10pt]">{clinic.legalName}</div>}
          <div className="text-[10pt]">{[clinic?.address, clinic?.phone && formatPhone(clinic.phone)].filter(Boolean).join(" · ")}</div>
          {clinic?.letterhead && <div className="whitespace-pre-line text-[9pt]">{clinic.letterhead}</div>}
        </div>
      </header>

      <h1 className="mt-6 text-center text-[15pt] font-bold uppercase tracking-wide">{conclusion.title}</h1>

      <table className="mt-4 w-full text-[11pt]">
        <tbody>
          <Row label={t("conclusions.patient")} value={patient.fullName} />
          <Row label={t("patients.birthDate")} value={`${formatDate(patient.birthDate)} (${t("patients.age", { count: ageOn(patient.birthDate) })})`} />
          <Row label={t("patients.card")} value={String(patient.cardNo)} />
          <Row label={t("conclusions.date")} value={formatDateTime(conclusion.createdAt)} />
        </tbody>
      </table>

      <div className="mt-5 whitespace-pre-line">{conclusion.body}</div>

      {conclusion.notes.length > 0 && (
        <section className="mt-5 border-t border-black pt-2">
          <div className="font-bold">{t("conclusions.notes")}</div>
          {conclusion.notes.map((n) => (
            <p key={n.id} className="mt-1 whitespace-pre-line">
              <span className="text-[10pt]">{formatDateTime(n.createdAt)}, {n.authorName}: </span>
              {n.text}
            </p>
          ))}
        </section>
      )}

      <footer className="mt-12 flex items-end justify-between">
        <div>
          <div className="text-[10pt]">{t("conclusions.doctor")}</div>
          <div className="font-bold">{conclusion.authorName}</div>
        </div>
        {signature && (
          <img
            src={`/api/v1/conclusions/${conclusion.id}/signature`}
            alt=""
            className="max-h-16 max-w-48 object-contain"
            onError={() => setSignature(false)}
          />
        )}
      </footer>
    </article>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <tr>
      <td className="w-40 py-0.5 pr-3 align-top">{label}:</td>
      <td className="py-0.5 font-semibold">{value}</td>
    </tr>
  );
}
