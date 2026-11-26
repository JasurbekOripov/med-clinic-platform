import { FileText, MessageSquarePlus, Pencil, Printer } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { ErrorState, Spinner } from "@medclinic/web-ui/components/Common";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Card } from "@medclinic/web-ui/components/ui/card";
import { formatDateTime } from "@medclinic/web-ui/lib/format";
import { useAddConclusionNote, usePrintSettings } from "@/api/hooks";
import type { Conclusion, Patient } from "@/api/types";
import { usePermissions } from "@/features/auth/permissions";
import { ConclusionDocument } from "@/features/print/ConclusionDocument";
import { printDocument } from "@/features/print/print";
import { PrintPortal } from "@/features/print/PrintPortal";
import { ReasonDialog } from "@/features/visits/ReasonDialog";
import { ConclusionEditor } from "./ConclusionEditor";

/** Conclusions printed on the clinic's letterhead; print state is shared so only one document is in the print area. */
export function usePrintConclusion(patient: Patient) {
  const clinic = usePrintSettings();
  const [printing, setPrinting] = React.useState<Conclusion | null>(null);
  React.useEffect(() => {
    if (!printing) return;
    // Keep the document in the print area until the browser reports the print finished.
    const done = () => setPrinting(null);
    window.addEventListener("afterprint", done, { once: true });
    // Wait one frame so the document (and its images) are in the print area.
    const id = requestAnimationFrame(() => printDocument("a4"));
    return () => {
      cancelAnimationFrame(id);
      window.removeEventListener("afterprint", done);
    };
  }, [printing]);
  const portal = printing ? (
    <PrintPortal>
      <ConclusionDocument conclusion={printing} patient={patient} clinic={clinic.data} />
    </PrintPortal>
  ) : null;
  return { print: setPrinting, portal, ready: !clinic.isPending };
}

export function PatientConclusions({ patient, query }: { patient: Patient; query: { data?: Conclusion[]; isPending: boolean; error: unknown; refetch: () => unknown } }) {
  const { t } = useTranslation();
  const { can } = usePermissions();
  const note = useAddConclusionNote();
  const { print, portal, ready } = usePrintConclusion(patient);
  const [editing, setEditing] = React.useState<Conclusion | null>(null);
  const [noting, setNoting] = React.useState<Conclusion | null>(null);
  const [open, setOpen] = React.useState<string | null>(null);

  if (query.isPending) return <Spinner />;
  if (query.error) return <ErrorState error={query.error} onRetry={() => void query.refetch()} />;
  const list = query.data ?? [];
  if (list.length === 0) return null;

  return (
    <section className="mb-6">
      <h2 className="mb-3 flex items-center gap-2 text-lg font-semibold">
        <FileText className="size-5 text-primary" aria-hidden /> {t("conclusions.history")}
      </h2>
      <div className="space-y-3">
        {list.map((c) => {
          const expanded = open === c.id;
          return (
            <Card key={c.id} className="p-5">
              <div className="flex flex-wrap items-start gap-3">
                <button type="button" className="min-w-0 flex-1 text-left" onClick={() => setOpen(expanded ? null : c.id)} aria-expanded={expanded}>
                  <div className="font-semibold">{c.title}</div>
                  <div className="text-sm text-muted-foreground">{c.authorName} · {formatDateTime(c.createdAt)}</div>
                </button>
                {c.editable && (
                  <Button size="sm" variant="secondary" onClick={() => setEditing(c)}><Pencil /> {t("common.edit")}</Button>
                )}
                {!c.editable && can("CONSULTATION_WRITE") && (
                  <Button size="sm" variant="ghost" onClick={() => setNoting(c)}><MessageSquarePlus /> {t("conclusions.addNote")}</Button>
                )}
                <Button size="sm" variant="ghost" disabled={!ready} onClick={() => print(c)}><Printer /> {t("common.print")}</Button>
              </div>
              <div className={expanded ? "mt-3 whitespace-pre-line font-serif leading-relaxed" : "mt-2 line-clamp-2 whitespace-pre-line text-sm text-muted-foreground"}>
                {c.body}
              </div>
              {expanded &&
                c.notes.map((n) => (
                  <div key={n.id} className="mt-3 rounded-lg bg-muted px-3 py-2 text-sm">
                    <div className="text-xs text-muted-foreground">{formatDateTime(n.createdAt)} · {n.authorName}</div>
                    <div className="whitespace-pre-line">{n.text}</div>
                  </div>
                ))}
              {!expanded && c.notes.length > 0 && <div className="mt-1 text-xs text-muted-foreground">{t("conclusions.notesCount", { count: c.notes.length })}</div>}
            </Card>
          );
        })}
      </div>
      {editing && <ConclusionEditor conclusion={editing} onSaved={() => setEditing(null)} onClose={() => setEditing(null)} />}
      {noting && (
        <ReasonDialog
          title={t("conclusions.addNoteTitle", { title: noting.title })}
          description={t("conclusions.addNoteHint")}
          label={t("conclusions.note")}
          confirmLabel={t("conclusions.addNote")}
          onClose={() => setNoting(null)}
          onConfirm={(text) => note.mutateAsync({ id: noting.id, text }).then(() => toast.success(t("common.saved")))}
        />
      )}
      {portal}
    </section>
  );
}
