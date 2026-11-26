import { useTranslation } from "react-i18next";
import { formatDateTime } from "@medclinic/web-ui/lib/format";
import type { Conclusion, Patient, PrintSettings } from "@/api/types";
import { A4Page, DocumentTitle, LetterheadHeader, PatientBox, SignatureFooter } from "./Letterhead";

/**
 * A4 conclusion on the clinic's letterhead. Everything except the doctor's text is filled in automatically:
 * logo and clinic details (Settings), title, patient block, date, the doctor's name with signature image and a place
 * for the stamp.
 */
export function ConclusionDocument({ conclusion, patient, clinic }: { conclusion: Conclusion; patient: Patient; clinic: PrintSettings | undefined }) {
  const { t } = useTranslation();
  return (
    <A4Page>
      <LetterheadHeader clinic={clinic} />
      <DocumentTitle title={conclusion.title} subtitle={formatDateTime(conclusion.createdAt)} />
      <PatientBox patient={patient} />
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
      <SignatureFooter role={t("conclusions.doctor")} name={conclusion.authorName} signatureUrl={`/api/v1/conclusions/${conclusion.id}/signature`} clinic={clinic} />
    </A4Page>
  );
}
