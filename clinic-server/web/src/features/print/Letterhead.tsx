import * as React from "react";
import { useTranslation } from "react-i18next";
import { formatDate, formatPhone } from "@medclinic/web-ui/lib/format";
import type { PrintSettings } from "@/api/types";
import { ageOn } from "@/features/patients/age";

/** One accent colour for printed documents; it prints as dark grey on black-and-white printers. */
export const ACCENT = "#0f766e";

/** A4 page frame: header at the top, [children] in the middle, footer pushed to the bottom of the first page. */
export function A4Page({ children }: { children: React.ReactNode }) {
  return <article className="mx-auto flex min-h-[265mm] max-w-[180mm] flex-col font-serif text-[11.5pt] leading-relaxed text-black">{children}</article>;
}

export function LetterheadHeader({ clinic }: { clinic: PrintSettings | undefined }) {
  return (
    <>
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
    </>
  );
}

export function DocumentTitle({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <>
      <h1 className="mt-7 text-center font-sans text-[15pt] font-bold uppercase tracking-wider">{title}</h1>
      {subtitle && <div className="mt-1 text-center font-sans text-[9.5pt] text-neutral-700">{subtitle}</div>}
    </>
  );
}

type PatientFacts = { fullName: string; cardNo: number; birthDate: string; gender: string };

export function PatientBox({ patient, extra }: { patient: PatientFacts; extra?: { label: string; value: string } }) {
  const { t } = useTranslation();
  return (
    <section className="mt-5 grid grid-cols-[auto_1fr_auto_auto] gap-x-4 gap-y-1 rounded border border-neutral-400 px-4 py-3 font-sans text-[10.5pt]">
      <span className="text-neutral-600">{t("conclusions.patient")}:</span>
      <span className="font-bold">{patient.fullName}</span>
      <span className="text-neutral-600">{t("patients.card")}:</span>
      <span className="font-bold">{patient.cardNo}</span>
      <span className="text-neutral-600">{t("patients.birthDate")}:</span>
      <span className="font-semibold">{formatDate(patient.birthDate)} ({t("patients.age", { count: ageOn(patient.birthDate) })})</span>
      <span className="text-neutral-600">{t("patients.gender")}:</span>
      <span className="font-semibold">{t(`gender.${patient.gender}`)}</span>
      {extra && (
        <>
          <span className="text-neutral-600">{extra.label}:</span>
          <span className="col-span-3 font-semibold">{extra.value}</span>
        </>
      )}
    </section>
  );
}

/** Name of the responsible person with the signature image over a line, a place for the stamp, and the page footer. */
export function SignatureFooter({ role, name, signatureUrl, clinic }: { role: string; name: string | null | undefined; signatureUrl: string; clinic: PrintSettings | undefined }) {
  const { t } = useTranslation();
  const [signature, setSignature] = React.useState(true);
  return (
    <footer className="mt-10 break-inside-avoid">
      <div className="flex items-end gap-6 font-sans text-[10.5pt]">
        <div className="min-w-0">
          <div className="text-neutral-600">{role}</div>
          <div className="font-bold">{name}</div>
        </div>
        <div className="relative ml-auto h-[20mm] w-[55mm] border-b border-black">
          {signature && (
            <img src={signatureUrl} alt="" className="absolute bottom-0 left-1/2 max-h-[20mm] max-w-[55mm] -translate-x-1/2 object-contain" onError={() => setSignature(false)} />
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
  );
}
