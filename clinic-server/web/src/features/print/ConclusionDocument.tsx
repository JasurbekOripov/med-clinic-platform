import * as React from "react";
import { useTranslation } from "react-i18next";
import { formatDate, formatDateTime, formatPhone } from "@medclinic/web-ui/lib/format";
import type { Conclusion, Patient, PrintSettings } from "@/api/types";
import { ageOn } from "@/features/patients/age";

/** One accent colour for the letterhead; it prints as dark grey on black-and-white printers. */
const ACCENT = "#0f766e";

/**
 * A4 conclusion on the clinic's letterhead. Everything except the doctor's text is filled in automatically:
 * logo and clinic details (Settings), title, patient block, date, the doctor's name with signature image and a place
 * for the stamp. The signature block sits at the bottom of the page.
 */
export function ConclusionDocument({ conclusion, patient, clinic }: { conclusion: Conclusion; patient: Patient; clinic: PrintSettings | undefined }) {
  const { t } = useTranslation();
  const [signature, setSignature] = React.useState(true);
  return (
    <article className="mx-auto flex min-h-[265mm] max-w-[180mm] flex-col font-serif text-[11.5pt] leading-relaxed text-black">
      <header className="flex items-center gap-5 pb-3">
        {clinic?.hasLogo && <img src="/api/v1/settings/logo" alt="" className="h-[22mm] w-auto max-w-[45mm] object-contain" />}
        <div className="min-w-0 flex-1">
          <div className="font-sans text-[18pt] font-bold leading-tight" style={{ color: ACCENT }}>{clinic?.name}</div>
          {clinic?.legalName && <div className="font-sans text-[9.5pt] font-semibold">{clinic.legalName}</div>}
          {clinic?.letterhead && <div className="whitespace-pre-line font-sans text-[8.5pt] text-neutral-700">{clinic.letterhead}</div>}
        </div>
        <div className="max-w-[60mm] text-right font-sans text-[9pt] leading-snug">
          {clinic?.address && <div>{clinic.address}</div>}
          {clinic?.phone && <div className="font-semibold">{formatPhone(clinic.phone)}</div>}
        </div>
      </header>
      <div className="h-[1.2mm]" style={{ background: ACCENT }} />
      <div className="mt-[0.6mm] h-[0.3mm] bg-black" />

      <h1 className="mt-7 text-center font-sans text-[15pt] font-bold uppercase tracking-wider">{conclusion.title}</h1>
      <div className="mt-1 text-center font-sans text-[9.5pt] text-neutral-700">{formatDateTime(conclusion.createdAt)}</div>

      <section className="mt-5 grid grid-cols-[auto_1fr_auto_auto] gap-x-4 gap-y-1 rounded border border-neutral-400 px-4 py-3 font-sans text-[10.5pt]">
        <span className="text-neutral-600">{t("conclusions.patient")}:</span>
        <span className="font-bold">{patient.fullName}</span>
        <span className="text-neutral-600">{t("patients.card")}:</span>
        <span className="font-bold">{patient.cardNo}</span>
        <span className="text-neutral-600">{t("patients.birthDate")}:</span>
        <span className="font-semibold">{formatDate(patient.birthDate)} ({t("patients.age", { count: ageOn(patient.birthDate) })})</span>
        <span className="text-neutral-600">{t("patients.gender")}:</span>
        <span className="font-semibold">{t(`gender.${patient.gender}`)}</span>
      </section>

      <div className="mt-6 flex-1 whitespace-pre-line text-justify">{conclusion.body}</div>

      {conclusion.notes.length > 0 && (
        <section className="mt-6 border-t border-neutral-400 pt-3">
          <div className="font-sans text-[10pt] font-bold uppercase tracking-wide">{t("conclusions.notes")}</div>
          {conclusion.notes.map((n) => (
            <p key={n.id} className="mt-1.5 whitespace-pre-line">
              <span className="font-sans text-[9pt] text-neutral-700">{formatDateTime(n.createdAt)}, {n.authorName}: </span>
              {n.text}
            </p>
          ))}
        </section>
      )}

      <footer className="mt-10 break-inside-avoid">
        <div className="flex items-end gap-6 font-sans text-[10.5pt]">
          <div className="min-w-0">
            <div className="text-neutral-600">{t("conclusions.doctor")}</div>
            <div className="font-bold">{conclusion.authorName}</div>
          </div>
          <div className="relative ml-auto h-[20mm] w-[55mm] border-b border-black">
            {signature && (
              <img
                src={`/api/v1/conclusions/${conclusion.id}/signature`}
                alt=""
                className="absolute bottom-0 left-1/2 max-h-[20mm] max-w-[55mm] -translate-x-1/2 object-contain"
                onError={() => setSignature(false)}
              />
            )}
            <div className="absolute -bottom-5 w-full text-center text-[8pt] text-neutral-600">{t("conclusions.signature")}</div>
          </div>
          <div className="flex size-[24mm] shrink-0 items-center justify-center rounded-full border border-dashed border-neutral-500 text-[9pt] text-neutral-500">
            {t("conclusions.stamp")}
          </div>
        </div>
        <div className="mt-8 border-t border-neutral-300 pt-1.5 text-center font-sans text-[8pt] text-neutral-600">
          {[clinic?.name, clinic?.phone && formatPhone(clinic.phone)].filter(Boolean).join(" · ")}
        </div>
      </footer>
    </article>
  );
}
