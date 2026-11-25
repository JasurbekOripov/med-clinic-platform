import { Download, FilePlus2, RefreshCw, ShieldX } from "lucide-react";
import * as React from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { downloadFile } from "@medclinic/web-ui/api/client";
import { useIssueLicense, useLicenseCatalog, useLicenses, useRevokeLicense } from "@/api/hooks";
import type { Clinic, License } from "@/api/types";
import { EmptyState, ErrorState, FormError, Spinner } from "@medclinic/web-ui/components/Common";
import { ConfirmDialog } from "@medclinic/web-ui/components/ConfirmDialog";
import { Field } from "@medclinic/web-ui/components/Field";
import { LicenseStatusBadge } from "@/components/StatusBadge";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Card, CardHeader, CardTitle } from "@medclinic/web-ui/components/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogTitle } from "@medclinic/web-ui/components/ui/dialog";
import { Input, Textarea } from "@medclinic/web-ui/components/ui/input";
import { Table, Td, Th, Tr } from "@medclinic/web-ui/components/ui/table";
import { usePermissions } from "@/features/auth/permissions";
import { errorMessage, fieldErrors } from "@medclinic/web-ui/lib/errors";
import { addYears, formatDate, formatDateTime, todayInTashkent } from "@medclinic/web-ui/lib/format";

export function LicensesPanel({ clinic }: { clinic: Clinic }) {
  const { t } = useTranslation();
  const { can } = usePermissions();
  const { data, isPending, error, refetch } = useLicenses(clinic.id);
  const revoke = useRevokeLicense(clinic.id);
  const [form, setForm] = React.useState<{ renew?: License } | null>(null);
  const [revoking, setRevoking] = React.useState<License | null>(null);
  const [reason, setReason] = React.useState("");

  const current = data?.find((l) => l.status !== "REVOKED" && l.status !== "SUPERSEDED");
  const canManage = can("LICENSE_MANAGE") && clinic.status !== "ARCHIVED";

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("clinics.tabLicenses")}</CardTitle>
        {canManage && !current && (
          <Button onClick={() => setForm({})}>
            <FilePlus2 /> {t("licenses.issue")}
          </Button>
        )}
      </CardHeader>
      {isPending ? (
        <Spinner />
      ) : error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : data.length === 0 ? (
        <EmptyState text={t("licenses.empty")} />
      ) : (
        <Table>
          <thead>
            <tr>
              <Th>{t("licenses.serial")}</Th>
              <Th>{t("licenses.plan")}</Th>
              <Th>{t("licenses.period")}</Th>
              <Th>{t("licenses.modules")}</Th>
              <Th>{t("common.status")}</Th>
              <Th className="text-right">{t("common.actions")}</Th>
            </tr>
          </thead>
          <tbody>
            {data.map((l) => (
              <Tr key={l.id} className={l.id === current?.id ? "bg-primary/5" : undefined}>
                <Td className="font-mono">{l.serial}</Td>
                <Td>
                  <div className="font-medium">{l.plan}</div>
                  <div className="text-xs text-muted-foreground">
                    {t("licenses.maxUsers")}: {l.maxUsers} · {t("licenses.maxBranches")}: {l.maxBranches}
                  </div>
                </Td>
                <Td className="whitespace-nowrap">
                  {formatDate(l.startsOn)} – {formatDate(l.endsOn)}
                  <div className="text-xs text-muted-foreground">
                    {t("licenses.graceEndsAt")}: {formatDateTime(l.graceEndsAt)}
                  </div>
                </Td>
                <Td className="max-w-xs text-xs">{l.modules.map((m) => t(`module.${m}`, { defaultValue: m })).join(", ")}</Td>
                <Td>
                  <LicenseStatusBadge status={l.status} />
                  {l.revokeReason && <div className="mt-1 text-xs text-muted-foreground">{l.revokeReason}</div>}
                </Td>
                <Td>
                  <div className="flex flex-wrap justify-end gap-2">
                    <Button
                      size="sm"
                      variant="secondary"
                      onClick={() =>
                        downloadFile(`/api/v1/licenses/${l.id}/file`, `license-${clinic.code}-${l.serial}.lic`).catch((e: unknown) =>
                          toast.error(errorMessage(t, e)),
                        )
                      }
                    >
                      <Download /> {t("licenses.download")}
                    </Button>
                    {canManage && l.id === current?.id && (
                      <>
                        <Button size="sm" onClick={() => setForm({ renew: l })}>
                          <RefreshCw /> {t("licenses.renew")}
                        </Button>
                        <Button
                          size="sm"
                          variant="danger"
                          onClick={() => {
                            setReason("");
                            setRevoking(l);
                          }}
                        >
                          <ShieldX /> {t("licenses.revoke")}
                        </Button>
                      </>
                    )}
                  </div>
                </Td>
              </Tr>
            ))}
          </tbody>
        </Table>
      )}
      {form && <LicenseFormDialog clinicId={clinic.id} renew={form.renew} open onOpenChange={(o) => !o && setForm(null)} />}
      <ConfirmDialog
        open={revoking !== null}
        onOpenChange={(o) => !o && setRevoking(null)}
        title={revoking ? t("licenses.revokeTitle", { serial: revoking.serial }) : ""}
        description={t("licenses.revokeHint")}
        confirmLabel={t("licenses.revoke")}
        danger
        onConfirm={async () => {
          if (!revoking) return;
          await revoke.mutateAsync({ id: revoking.id, version: revoking.version, reason });
          toast.success(t("licenses.revoked"));
        }}
      >
        <Field label={t("common.reason")} className="mt-4">
          <Textarea autoFocus rows={3} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
      </ConfirmDialog>
    </Card>
  );
}

interface Values {
  plan: string;
  modules: string[];
  maxUsers: number;
  maxBranches: number;
  startsOn: string;
  endsOn: string;
  expiryGraceDays: number;
}

function LicenseFormDialog({
  clinicId,
  renew,
  open,
  onOpenChange,
}: {
  clinicId: string;
  renew?: License;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useTranslation();
  const catalog = useLicenseCatalog();
  const issue = useIssueLicense(clinicId);
  const today = todayInTashkent();
  // A renewal continues the day after the current license ends (or today if it already ended).
  const renewStart = renew && renew.endsOn >= today ? nextDay(renew.endsOn) : today;
  const start = renew ? renewStart : today;
  const { register, handleSubmit, setValue, watch } = useForm<Values>({
    defaultValues: {
      plan: renew?.plan ?? "STANDARD",
      modules: renew?.modules ?? [],
      maxUsers: renew?.maxUsers ?? 20,
      maxBranches: renew?.maxBranches ?? 1,
      startsOn: start,
      endsOn: addYears(start, 1),
      expiryGraceDays: renew?.expiryGraceDays ?? catalog.data?.defaultExpiryGraceDays ?? 14,
    },
  });
  const server = fieldErrors(t, issue.error);
  const selected = watch("modules");
  const allModules = catalog.data?.modules ?? [];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent wide>
        <DialogTitle>{renew ? t("licenses.renewTitle", { serial: renew.serial }) : t("licenses.issueTitle")}</DialogTitle>
        <form
          className="mt-5 grid gap-4 sm:grid-cols-2"
          onSubmit={handleSubmit((v) =>
            issue.mutate(
              {
                renewId: renew?.id,
                body: {
                  plan: v.plan,
                  modules: v.modules,
                  maxUsers: Number(v.maxUsers),
                  maxBranches: Number(v.maxBranches),
                  startsOn: v.startsOn,
                  endsOn: v.endsOn,
                  expiryGraceDays: Number(v.expiryGraceDays),
                  version: renew?.version,
                },
              },
              {
                onSuccess: () => {
                  toast.success(renew ? t("licenses.renewed") : t("licenses.issued"));
                  onOpenChange(false);
                },
              },
            ),
          )}
        >
          <Field label={t("licenses.plan")} error={server.plan} className="sm:col-span-2">
            <Input autoFocus className="uppercase" {...register("plan")} />
          </Field>
          <fieldset className="sm:col-span-2">
            <div className="mb-1.5 flex items-center justify-between">
              <legend className="text-sm font-medium">{t("licenses.modules")}</legend>
              <Button variant="link" size="sm" onClick={() => setValue("modules", allModules)}>
                {t("licenses.selectAll")}
              </Button>
            </div>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
              {allModules.map((m) => (
                <label key={m} className="flex cursor-pointer items-center gap-2 rounded-lg border border-border px-3 py-2 text-sm has-[:checked]:border-primary has-[:checked]:bg-primary/5">
                  <input type="checkbox" value={m} className="size-4 accent-[var(--color-primary)]" {...register("modules")} />
                  {t(`module.${m}`, { defaultValue: m })}
                </label>
              ))}
            </div>
            {(server.modules || (issue.isError && selected.length === 0)) && (
              <p className="mt-1 text-xs font-medium text-danger">{server.modules ?? t("errors.validation.not_empty")}</p>
            )}
          </fieldset>
          <Field label={t("licenses.maxUsers")} error={server.maxUsers}>
            <Input type="number" min={1} {...register("maxUsers")} />
          </Field>
          <Field label={t("licenses.maxBranches")} error={server.maxBranches}>
            <Input type="number" min={1} {...register("maxBranches")} />
          </Field>
          <Field label={t("licenses.startsOn")} error={server.startsOn}>
            <Input type="date" {...register("startsOn")} />
          </Field>
          <Field label={t("licenses.endsOn")} error={server.endsOn}>
            <Input type="date" {...register("endsOn")} />
          </Field>
          <Field label={t("licenses.graceDays")} error={server.expiryGraceDays}>
            <Input type="number" min={0} max={catalog.data?.maxExpiryGraceDays ?? 90} {...register("expiryGraceDays")} />
          </Field>
          <div className="sm:col-span-2">
            {Object.keys(server).length === 0 && <FormError error={issue.error} />}
            <DialogFooter>
              <Button variant="secondary" onClick={() => onOpenChange(false)}>
                {t("common.cancel")}
              </Button>
              <Button type="submit" loading={issue.isPending}>
                {renew ? t("licenses.renew") : t("licenses.issue")}
              </Button>
            </DialogFooter>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function nextDay(isoDate: string): string {
  const d = new Date(`${isoDate}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + 1);
  return d.toISOString().slice(0, 10);
}
