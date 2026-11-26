import { ArrowLeft, Pencil, Trash2, UserRound } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate, useParams } from "react-router";
import { toast } from "sonner";
import { ErrorState, Spinner } from "@medclinic/web-ui/components/Common";
import { ConfirmDialog } from "@medclinic/web-ui/components/ConfirmDialog";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Card, CardContent } from "@medclinic/web-ui/components/ui/card";
import { formatDate, formatDateTime, formatPhone } from "@medclinic/web-ui/lib/format";
import { cn } from "@medclinic/web-ui/lib/utils";
import { useDeletePatient, useMe, usePatient, usePatientConclusions } from "@/api/hooks";
import type { Patient, VisitItem } from "@/api/types";
import { usePermissions } from "@/features/auth/permissions";
import { ConclusionEditor } from "@/features/conclusions/ConclusionEditor";
import { PatientConclusions, usePrintConclusion } from "@/features/conclusions/PatientConclusions";
import { PatientVisits } from "@/features/visits/PatientVisits";
import { ageOn } from "./age";
import { PatientDialog } from "./PatientDialog";

export function PatientDetailPage() {
  const { id = "" } = useParams();
  const { data, isPending, error, refetch } = usePatient(id);
  if (isPending) return <Spinner />;
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />;
  return <PatientBody p={data} />;
}

function PatientBody({ p }: { p: Patient }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { can } = usePermissions();
  const me = useMe();
  const remove = useDeletePatient();
  const [editing, setEditing] = React.useState(false);
  const [deleting, setDeleting] = React.useState(false);
  const [writing, setWriting] = React.useState<VisitItem | null>(null);
  const canReadConclusions = can("CONSULTATION_WRITE") || can("MEDICAL_HISTORY_VIEW") || can("RESULTS_HANDOVER");
  const conclusions = usePatientConclusions(p.id, canReadConclusions);
  const concluded = React.useMemo(() => new Set((conclusions.data ?? []).map((c) => c.visitItemId)), [conclusions.data]);
  const { print, portal } = usePrintConclusion(p);

  return (
    <>
      <Link to="/patients" className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" /> {t("patients.title")}
      </Link>
      <Card className="mb-6">
        <CardContent className="flex flex-wrap items-start gap-5 p-6">
          <div className={cn("flex size-16 shrink-0 items-center justify-center rounded-2xl", p.gender === "FEMALE" ? "bg-rose-100 text-rose-700" : "bg-sky-100 text-sky-700")}>
            <UserRound className="size-8" aria-hidden />
          </div>
          <div className="min-w-0 flex-1">
            <h1 className="text-2xl font-semibold">{p.fullName}</h1>
            <div className="mt-1 text-muted-foreground">
              {t("patients.card")} <span className="font-mono font-semibold text-foreground">{p.cardNo}</span> · {t(`gender.${p.gender}`)} ·{" "}
              {formatDate(p.birthDate)} ({t("patients.age", { count: ageOn(p.birthDate) })})
            </div>
            <dl className="mt-4 grid gap-x-10 gap-y-2 text-sm sm:grid-cols-2">
              <Info label={t("common.phone")} value={formatPhone(p.phone)} />
              <Info label={t("common.address")} value={p.address} />
              <Info label={t("common.createdAt")} value={formatDateTime(p.createdAt)} />
            </dl>
            {p.notes && <p className="mt-4 whitespace-pre-line rounded-lg bg-muted px-3 py-2 text-sm">{p.notes}</p>}
          </div>
          <div className="flex gap-2">
            {can("PATIENT_EDIT") && (
              <Button variant="secondary" onClick={() => setEditing(true)}>
                <Pencil /> {t("common.edit")}
              </Button>
            )}
            {can("PATIENT_DELETE") && (
              <Button variant="ghost" className="text-danger" onClick={() => setDeleting(true)} aria-label={t("patients.delete")}>
                <Trash2 />
              </Button>
            )}
          </div>
        </CardContent>
      </Card>
      {canReadConclusions && <PatientConclusions patient={p} query={conclusions} />}
      <PatientVisits
        patientId={p.id}
        conclusions={can("CONSULTATION_WRITE") ? { concluded, meId: me.data?.id, write: setWriting } : undefined}
      />
      {writing && (
        <ConclusionEditor
          visitItemId={writing.id}
          defaultTitle={writing.serviceName}
          onClose={() => setWriting(null)}
          onSaved={(c) => {
            setWriting(null);
            print(c);
          }}
        />
      )}
      {portal}
      <PatientDialog patient={p} open={editing} onOpenChange={setEditing} />
      <ConfirmDialog
        open={deleting}
        onOpenChange={setDeleting}
        title={t("patients.confirmDelete", { name: p.fullName })}
        confirmLabel={t("patients.delete")}
        danger
        onConfirm={async () => {
          await remove.mutateAsync({ id: p.id, version: p.version });
          toast.success(t("patients.deleted"));
          navigate("/patients", { replace: true });
        }}
      />
    </>
  );
}

function Info({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div className="flex gap-2">
      <dt className="text-muted-foreground">{label}:</dt>
      <dd className="font-medium">{value || "—"}</dd>
    </div>
  );
}
