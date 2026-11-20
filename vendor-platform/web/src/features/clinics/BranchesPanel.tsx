import { Monitor, Pencil, Plug, Plus } from "lucide-react";
import * as React from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { useBranches, useBranchStatus, useDeleteBranch, useSaveBranch } from "@/api/hooks";
import type { Branch, Clinic } from "@/api/types";
import { EmptyState, ErrorState, FormError, Spinner } from "@/components/Common";
import { Field } from "@/components/Field";
import { TenantStatusBadge } from "@/components/StatusBadge";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Table, Td, Th, Tr } from "@/components/ui/table";
import { usePermissions } from "@/features/auth/permissions";
import { fieldErrors } from "@/lib/errors";
import { formatPhone } from "@/lib/format";
import { DevicesDialog, PairingDialog } from "./PairingDialog";
import { StatusActions } from "./StatusActions";

export function BranchesPanel({ clinic }: { clinic: Clinic }) {
  const { t } = useTranslation();
  const { can } = usePermissions();
  const { data, isPending, error, refetch } = useBranches(clinic.id);
  const changeStatus = useBranchStatus(clinic.id);
  const remove = useDeleteBranch(clinic.id);
  const [editing, setEditing] = React.useState<Branch | "new" | null>(null);
  const [pairing, setPairing] = React.useState<Branch | null>(null);
  const [devices, setDevices] = React.useState<Branch | null>(null);
  const clinicArchived = clinic.status === "ARCHIVED";

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("clinics.tabBranches")}</CardTitle>
        {can("CLINIC_EDIT") && !clinicArchived && (
          <Button onClick={() => setEditing("new")}>
            <Plus /> {t("branches.new")}
          </Button>
        )}
      </CardHeader>
      {isPending ? (
        <Spinner />
      ) : error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : data.length === 0 ? (
        <EmptyState text={t("branches.empty")} />
      ) : (
        <Table>
          <thead>
            <tr>
              <Th>{t("clinics.code")}</Th>
              <Th>{t("common.name")}</Th>
              <Th>{t("common.status")}</Th>
              <Th className="text-right">{t("common.actions")}</Th>
            </tr>
          </thead>
          <tbody>
            {data.map((b) => (
              <Tr key={b.id}>
                <Td className="font-mono text-xs">{b.code}</Td>
                <Td>
                  <div className="font-medium">{b.name}</div>
                  <div className="text-xs text-muted-foreground">{[b.address, b.phone && formatPhone(b.phone)].filter(Boolean).join(" · ")}</div>
                </Td>
                <Td>
                  <TenantStatusBadge status={b.status} />
                </Td>
                <Td>
                  <div className="flex flex-wrap justify-end gap-2">
                    {can("DEVICE_PAIR") && b.status === "ACTIVE" && clinic.status === "ACTIVE" && (
                      <Button size="sm" onClick={() => setPairing(b)}>
                        <Plug /> {t("branches.connect")}
                      </Button>
                    )}
                    {can("MONITORING_VIEW") && (
                      <Button size="sm" variant="secondary" onClick={() => setDevices(b)}>
                        <Monitor /> {t("branches.devices")}
                      </Button>
                    )}
                    {can("CLINIC_EDIT") && b.status !== "ARCHIVED" && !clinicArchived && (
                      <Button size="sm" variant="secondary" onClick={() => setEditing(b)} aria-label={t("common.edit")}>
                        <Pencil />
                      </Button>
                    )}
                    {can("CLINIC_STATUS") && !clinicArchived && (
                      <StatusActions
                        kind="branches"
                        size="sm"
                        name={b.name}
                        status={b.status}
                        onStatus={(status) => changeStatus.mutateAsync({ id: b.id, status, version: b.version })}
                        onDelete={() => remove.mutateAsync({ id: b.id, version: b.version })}
                      />
                    )}
                  </div>
                </Td>
              </Tr>
            ))}
          </tbody>
        </Table>
      )}
      <BranchFormDialog clinicId={clinic.id} branch={editing === "new" ? undefined : (editing ?? undefined)} open={editing !== null} onOpenChange={(o) => !o && setEditing(null)} />
      {pairing && <PairingDialog branch={pairing} open onOpenChange={(o) => !o && setPairing(null)} />}
      {devices && <DevicesDialog branch={devices} open onOpenChange={(o) => !o && setDevices(null)} />}
    </Card>
  );
}

function BranchFormDialog({
  clinicId,
  branch,
  open,
  onOpenChange,
}: {
  clinicId: string;
  branch?: Branch;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useTranslation();
  const save = useSaveBranch(clinicId);
  const { register, handleSubmit, reset, formState } = useForm<{ name: string; address: string; phone: string }>();

  React.useEffect(() => {
    if (open) {
      reset({ name: branch?.name ?? "", address: branch?.address ?? "", phone: branch?.phone ?? "" });
      save.reset();
    }
  }, [open, branch]);

  const server = fieldErrors(t, save.error);
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogTitle>{branch ? t("branches.edit") : t("branches.new")}</DialogTitle>
        <form
          className="mt-5 space-y-4"
          onSubmit={handleSubmit((v) =>
            save.mutate(
              { id: branch?.id, body: { name: v.name, address: v.address || null, phone: v.phone || null, version: branch?.version } },
              {
                onSuccess: () => {
                  toast.success(t("common.saved"));
                  onOpenChange(false);
                },
              },
            ),
          )}
        >
          <Field label={t("common.name")} error={server.name ?? (formState.errors.name && t("errors.validation.not_blank"))}>
            <Input autoFocus {...register("name", { required: true })} />
          </Field>
          <Field label={t("common.address")} error={server.address} optional>
            <Input {...register("address")} />
          </Field>
          <Field label={t("common.phone")} error={server.phone} optional>
            <Input type="tel" inputMode="tel" placeholder="+998 90 123 45 67" {...register("phone")} />
          </Field>
          {Object.keys(server).length === 0 && <FormError error={save.error} />}
          <DialogFooter>
            <Button variant="secondary" onClick={() => onOpenChange(false)}>
              {t("common.cancel")}
            </Button>
            <Button type="submit" loading={save.isPending}>
              {branch ? t("common.save") : t("common.create")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
