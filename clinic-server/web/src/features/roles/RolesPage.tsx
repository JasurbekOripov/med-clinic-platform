import { Lock, Pencil, Plus, Trash2 } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { ErrorState, FormError, PageHeader, Spinner } from "@medclinic/web-ui/components/Common";
import { ConfirmDialog } from "@medclinic/web-ui/components/ConfirmDialog";
import { Field } from "@medclinic/web-ui/components/Field";
import { Badge } from "@medclinic/web-ui/components/ui/badge";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Card } from "@medclinic/web-ui/components/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogTitle } from "@medclinic/web-ui/components/ui/dialog";
import { Input } from "@medclinic/web-ui/components/ui/input";
import { fieldErrors } from "@medclinic/web-ui/lib/errors";
import { useDeleteRole, useRoles, useSaveRole } from "@/api/hooks";
import type { Role } from "@/api/types";
import { usePermissions } from "@/features/auth/permissions";
import { PermissionPicker } from "@/features/staff/PermissionPicker";
import { roleText } from "./RoleLabel";

export function RolesPage() {
  const { t } = useTranslation();
  const { can } = usePermissions();
  const manage = can("ROLE_MANAGE");
  const { data, isPending, error, refetch } = useRoles();
  const remove = useDeleteRole();
  const [editing, setEditing] = React.useState<Role | "new" | null>(null);
  const [deleting, setDeleting] = React.useState<Role | null>(null);

  return (
    <>
      <PageHeader
        title={t("roles.title")}
        subtitle={t("roles.subtitle")}
        actions={
          manage && (
            <Button size="lg" onClick={() => setEditing("new")}>
              <Plus /> {t("roles.new")}
            </Button>
          )
        }
      />
      {isPending ? (
        <Spinner />
      ) : error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {data.map((role) => (
            <Card key={role.id} className="flex flex-col p-5">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="flex items-center gap-2 text-lg font-semibold">
                    {role.system && <Lock className="size-4 text-muted-foreground" aria-label={t("roles.system")} />}
                    {roleText(t, role)}
                  </div>
                  <div className="mt-1 text-sm text-muted-foreground">
                    {t("roles.staffCount", { count: role.activeStaff })} · {t("roles.count", { count: role.permissions.length })}
                  </div>
                </div>
                {manage && !role.system && (
                  <div className="flex gap-1">
                    <Button size="icon" variant="ghost" aria-label={t("common.edit")} onClick={() => setEditing(role)}>
                      <Pencil />
                    </Button>
                    {!role.template && (
                      <Button size="icon" variant="ghost" aria-label={t("common.delete")} onClick={() => setDeleting(role)}>
                        <Trash2 />
                      </Button>
                    )}
                  </div>
                )}
              </div>
              <div className="mt-4 flex flex-wrap gap-1.5">
                {role.system ? (
                  <span className="text-sm text-muted-foreground">{t("roles.systemHint")}</span>
                ) : (
                  role.permissions.map((p) => (
                    <Badge key={p} tone="gray">
                      {t(`permission.${p}`, { defaultValue: p })}
                    </Badge>
                  ))
                )}
              </div>
            </Card>
          ))}
        </div>
      )}
      {editing && <RoleDialog role={editing === "new" ? undefined : editing} onClose={() => setEditing(null)} />}
      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(o) => !o && setDeleting(null)}
        title={deleting ? t("roles.confirmDelete", { name: roleText(t, deleting) }) : ""}
        confirmLabel={t("common.delete")}
        danger
        onConfirm={async () => {
          if (!deleting) return;
          await remove.mutateAsync({ id: deleting.id, version: deleting.version });
          toast.success(t("common.saved"));
        }}
      />
    </>
  );
}

function RoleDialog({ role, onClose }: { role?: Role; onClose: () => void }) {
  const { t } = useTranslation();
  const save = useSaveRole();
  const [name, setName] = React.useState(role?.name ?? "");
  const [selected, setSelected] = React.useState<Set<string>>(new Set(role?.permissions ?? []));
  const server = fieldErrors(t, save.error);
  const isTemplate = Boolean(role?.template);

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent wide className="max-w-3xl">
        <DialogTitle>{role ? t("roles.edit") : t("roles.new")}</DialogTitle>
        <form
          className="mt-5 space-y-5"
          onSubmit={(e) => {
            e.preventDefault();
            save.mutate(
              { id: role?.id, body: { name: isTemplate ? null : name, permissions: [...selected], version: role?.version } },
              { onSuccess: () => { toast.success(t("common.saved")); onClose(); } },
            );
          }}
        >
          {isTemplate ? (
            <div className="text-lg font-medium">{roleText(t, role!)}</div>
          ) : (
            <Field label={t("roles.name")} error={server.name}>
              <Input autoFocus value={name} maxLength={100} onChange={(e) => setName(e.target.value)} />
            </Field>
          )}
          <div>
            <div className="mb-3 text-sm font-medium">{t("roles.permissions")}</div>
            <PermissionPicker
              selected={selected}
              onToggle={(p, checked) => {
                const next = new Set(selected);
                if (checked) next.add(p);
                else next.delete(p);
                setSelected(next);
              }}
            />
          </div>
          {Object.keys(server).length === 0 && <FormError error={save.error} />}
          <DialogFooter>
            <Button variant="secondary" onClick={onClose}>
              {t("common.cancel")}
            </Button>
            <Button type="submit" loading={save.isPending}>
              {t("common.save")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
