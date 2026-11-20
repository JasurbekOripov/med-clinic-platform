import { ArrowLeft, Pencil } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate, useParams } from "react-router";
import { toast } from "sonner";
import { useClinic, useClinicStatus, useDeleteClinic } from "@/api/hooks";
import { ErrorState, Segmented, Spinner } from "@/components/Common";
import { TenantStatusBadge } from "@/components/StatusBadge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { usePermissions } from "@/features/auth/permissions";
import { AuditTable } from "@/features/audit/AuditPage";
import { formatDateTime, formatPhone } from "@/lib/format";
import { BranchesPanel } from "./BranchesPanel";
import { ClinicFormDialog } from "./ClinicFormDialog";
import { LicensesPanel } from "./LicensesPanel";
import { StatusActions } from "./StatusActions";

type Tab = "branches" | "licenses" | "history";

export function ClinicDetailPage() {
  const { id = "" } = useParams();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { can } = usePermissions();
  const { data: clinic, isPending, error, refetch } = useClinic(id);
  const changeStatus = useClinicStatus();
  const remove = useDeleteClinic();
  const [editing, setEditing] = React.useState(false);
  const [tab, setTab] = React.useState<Tab>("branches");

  if (isPending) return <Spinner />;
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />;

  const tabs: { value: Tab; label: string }[] = [
    { value: "branches", label: t("clinics.tabBranches") },
    ...(can("LICENSE_VIEW") ? [{ value: "licenses" as const, label: t("clinics.tabLicenses") }] : []),
    ...(can("AUDIT_VIEW") ? [{ value: "history" as const, label: t("clinics.tabHistory") }] : []),
  ];

  return (
    <>
      <Link to="/clinics" className="mb-3 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" /> {t("clinics.title")}
      </Link>
      <Card className="mb-6">
        <CardContent className="flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="text-2xl font-semibold tracking-tight">{clinic.name}</h1>
              <TenantStatusBadge status={clinic.status} />
              <span className="font-mono text-sm text-muted-foreground">{clinic.code}</span>
            </div>
            <dl className="mt-3 grid gap-x-8 gap-y-1 text-sm sm:grid-cols-2">
              <Info label={t("clinics.legalName")} value={clinic.legalName} />
              <Info label={t("clinics.ownerName")} value={`${clinic.ownerName} · ${formatPhone(clinic.phone)}`} />
              <Info label={t("clinics.region")} value={t(`region.${clinic.region}`)} />
              <Info label={t("common.address")} value={clinic.address} />
              <Info label={t("common.createdAt")} value={formatDateTime(clinic.createdAt)} />
            </dl>
            {clinic.notes && <p className="mt-3 max-w-2xl whitespace-pre-line text-sm text-muted-foreground">{clinic.notes}</p>}
          </div>
          <div className="flex flex-wrap gap-2">
            {can("CLINIC_EDIT") && clinic.status !== "ARCHIVED" && (
              <Button variant="secondary" onClick={() => setEditing(true)}>
                <Pencil /> {t("common.edit")}
              </Button>
            )}
            {can("CLINIC_STATUS") && (
              <StatusActions
                kind="clinics"
                name={clinic.name}
                status={clinic.status}
                onStatus={(status) => changeStatus.mutateAsync({ id: clinic.id, status, version: clinic.version })}
                onDelete={async () => {
                  await remove.mutateAsync({ id: clinic.id, version: clinic.version });
                  toast.success(t("clinics.deleted"));
                  navigate("/clinics", { replace: true });
                }}
              />
            )}
          </div>
        </CardContent>
      </Card>

      <div className="mb-4">
        <Segmented value={tab} onChange={setTab} options={tabs} />
      </div>
      {tab === "branches" && <BranchesPanel clinic={clinic} />}
      {tab === "licenses" && <LicensesPanel clinic={clinic} />}
      {tab === "history" && (
        <Card>
          <AuditTable filter={{ clinicId: clinic.id }} />
        </Card>
      )}
      <ClinicFormDialog open={editing} onOpenChange={setEditing} clinic={clinic} />
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
