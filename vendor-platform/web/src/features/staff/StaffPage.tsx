import { Pencil, Plus } from "lucide-react";
import * as React from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { useCreateStaff, useStaff, useStaffAction, useUpdateStaff } from "@/api/hooks";
import type { Staff, StaffWithPassword, VendorRole } from "@/api/types";
import { CopyButton, EmptyState, ErrorState, FormError, PageHeader, Pagination, SearchBox, Spinner } from "@/components/Common";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { Field } from "@/components/Field";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogTitle } from "@/components/ui/dialog";
import { Input, Select } from "@/components/ui/input";
import { Table, Td, Th, Tr } from "@/components/ui/table";
import { usePermissions } from "@/features/auth/permissions";
import { fieldErrors } from "@/lib/errors";
import { formatDateTime } from "@/lib/format";

type PendingAction = { staff: Staff; action: "activate" | "deactivate" | "reset-password" };

export function StaffPage() {
  const { t } = useTranslation();
  const { me } = usePermissions();
  const [search, setSearch] = React.useState("");
  const [includeInactive, setIncludeInactive] = React.useState(false);
  const [page, setPage] = React.useState(0);
  const { data, isPending, error, refetch } = useStaff({ q: search.trim() || undefined, includeInactive, page });
  const action = useStaffAction();
  const [editing, setEditing] = React.useState<Staff | "new" | null>(null);
  const [pending, setPending] = React.useState<PendingAction | null>(null);
  const [password, setPassword] = React.useState<StaffWithPassword | null>(null);

  return (
    <>
      <PageHeader
        title={t("staff.title")}
        actions={
          <Button size="lg" onClick={() => setEditing("new")}>
            <Plus /> {t("staff.new")}
          </Button>
        }
      />
      <div className="mb-4 flex flex-wrap items-center gap-4">
        <SearchBox value={search} onChange={(v) => { setSearch(v); setPage(0); }} placeholder={`${t("staff.fullName")} / ${t("staff.login")}`} />
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" className="size-4" checked={includeInactive} onChange={(e) => setIncludeInactive(e.target.checked)} />
          {t("staff.includeInactive")}
        </label>
      </div>
      <Card>
        {isPending ? (
          <Spinner />
        ) : error ? (
          <ErrorState error={error} onRetry={() => void refetch()} />
        ) : data.items.length === 0 ? (
          <EmptyState text={t("audit.empty")} />
        ) : (
          <>
            <Table>
              <thead>
                <tr>
                  <Th>{t("staff.fullName")}</Th>
                  <Th>{t("staff.role")}</Th>
                  <Th>{t("staff.lastLogin")}</Th>
                  <Th className="text-right">{t("common.actions")}</Th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((s) => {
                  const self = s.id === me?.id;
                  return (
                    <Tr key={s.id} className={s.active ? undefined : "opacity-60"}>
                      <Td>
                        <div className="font-medium">
                          {s.fullName} {self && <span className="text-xs text-muted-foreground">({t("staff.you")})</span>}
                        </div>
                        <div className="font-mono text-xs text-muted-foreground">{s.login}</div>
                      </Td>
                      <Td>
                        <div className="flex flex-wrap gap-1">
                          <Badge tone={s.role === "OWNER" ? "blue" : "gray"}>{t(`role.${s.role}`)}</Badge>
                          {!s.active && <Badge tone="red">{t("staff.inactive")}</Badge>}
                          {s.mustChangePassword && s.active && <Badge tone="amber">{t("staff.temporaryPassword")}</Badge>}
                        </div>
                      </Td>
                      <Td>{formatDateTime(s.lastLoginAt)}</Td>
                      <Td>
                        <div className="flex flex-wrap justify-end gap-2">
                          {s.active && (
                            <Button size="sm" variant="secondary" onClick={() => setEditing(s)} aria-label={t("common.edit")}>
                              <Pencil />
                            </Button>
                          )}
                          {!self && s.active && (
                            <Button size="sm" variant="secondary" onClick={() => setPending({ staff: s, action: "reset-password" })}>
                              {t("staff.resetPassword")}
                            </Button>
                          )}
                          {!self &&
                            (s.active ? (
                              <Button size="sm" variant="danger" onClick={() => setPending({ staff: s, action: "deactivate" })}>
                                {t("staff.deactivate")}
                              </Button>
                            ) : (
                              <Button size="sm" onClick={() => setPending({ staff: s, action: "activate" })}>
                                {t("staff.activate")}
                              </Button>
                            ))}
                        </div>
                      </Td>
                    </Tr>
                  );
                })}
              </tbody>
            </Table>
            <Pagination page={page} totalPages={data.totalPages} onPage={setPage} />
          </>
        )}
      </Card>

      <StaffFormDialog
        staff={editing === "new" ? undefined : (editing ?? undefined)}
        open={editing !== null}
        onOpenChange={(o) => !o && setEditing(null)}
        onCreated={setPassword}
      />
      <ConfirmDialog
        open={pending !== null}
        onOpenChange={(o) => !o && setPending(null)}
        title={
          pending
            ? pending.action === "deactivate"
              ? t("staff.confirmDeactivate", { name: pending.staff.fullName })
              : pending.action === "reset-password"
                ? t("staff.confirmReset", { name: pending.staff.fullName })
                : t("staff.activate")
            : ""
        }
        confirmLabel={pending ? t(pending.action === "reset-password" ? "staff.resetPassword" : `staff.${pending.action}`) : ""}
        danger={pending?.action !== "activate"}
        onConfirm={async () => {
          if (!pending) return;
          const result = await action.mutateAsync({ id: pending.staff.id, action: pending.action, version: pending.staff.version });
          if (pending.action === "reset-password") setPassword(result as StaffWithPassword);
          else toast.success(t("common.saved"));
        }}
      />
      <TemporaryPasswordDialog result={password} onClose={() => setPassword(null)} />
    </>
  );
}

function StaffFormDialog({
  staff,
  open,
  onOpenChange,
  onCreated,
}: {
  staff?: Staff;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreated: (result: StaffWithPassword) => void;
}) {
  const { t } = useTranslation();
  const create = useCreateStaff();
  const update = useUpdateStaff();
  const mutation = staff ? update : create;
  const { register, handleSubmit, reset, formState } = useForm<{ login: string; fullName: string; role: VendorRole }>();

  React.useEffect(() => {
    if (open) {
      reset({ login: staff?.login ?? "", fullName: staff?.fullName ?? "", role: staff?.role ?? "SUPPORT" });
      create.reset();
      update.reset();
    }
  }, [open, staff]);

  const server = fieldErrors(t, mutation.error);
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogTitle>{staff ? t("staff.edit") : t("staff.new")}</DialogTitle>
        <form
          className="mt-5 space-y-4"
          onSubmit={handleSubmit((v) => {
            if (staff) {
              update.mutate(
                { id: staff.id, body: { fullName: v.fullName, role: v.role, version: staff.version } },
                { onSuccess: () => { toast.success(t("common.saved")); onOpenChange(false); } },
              );
            } else {
              create.mutate(v, { onSuccess: (result) => { onOpenChange(false); onCreated(result); } });
            }
          })}
        >
          {!staff && (
            <Field label={t("staff.login")} hint={t("staff.loginHint")} error={server.login ?? (formState.errors.login && t("errors.validation.not_blank"))}>
              <Input autoFocus autoCapitalize="none" spellCheck={false} {...register("login", { required: true })} />
            </Field>
          )}
          <Field label={t("staff.fullName")} error={server.fullName ?? (formState.errors.fullName && t("errors.validation.not_blank"))}>
            <Input autoFocus={!!staff} {...register("fullName", { required: true })} />
          </Field>
          <Field label={t("staff.role")} error={server.role}>
            <Select {...register("role")}>
              <option value="SUPPORT">{t("role.SUPPORT")}</option>
              <option value="OWNER">{t("role.OWNER")}</option>
            </Select>
          </Field>
          {Object.keys(server).length === 0 && <FormError error={mutation.error} />}
          <DialogFooter>
            <Button variant="secondary" onClick={() => onOpenChange(false)}>
              {t("common.cancel")}
            </Button>
            <Button type="submit" loading={mutation.isPending}>
              {staff ? t("common.save") : t("common.create")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function TemporaryPasswordDialog({ result, onClose }: { result: StaffWithPassword | null; onClose: () => void }) {
  const { t } = useTranslation();
  return (
    <Dialog open={result !== null} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogTitle>{t("staff.temporaryPassword")}</DialogTitle>
        <DialogDescription>{result && t("staff.temporaryPasswordHint", { name: result.staff.fullName })}</DialogDescription>
        {result && (
          <div className="mt-5 rounded-xl border-2 border-dashed border-primary/40 bg-primary/5 p-5 text-center">
            <div className="text-sm text-muted-foreground">{result.staff.login}</div>
            <div className="mt-1 select-all font-mono text-2xl font-bold tracking-wider" data-testid="temporary-password">
              {result.temporaryPassword}
            </div>
            <CopyButton value={result.temporaryPassword} className="mt-3" />
          </div>
        )}
        <DialogFooter>
          <Button onClick={onClose}>{t("common.close")}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
