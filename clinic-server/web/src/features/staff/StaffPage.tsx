import { Pencil, Plus } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { EmptyState, ErrorState, PageHeader, Pagination, SearchBox, Spinner } from "@medclinic/web-ui/components/Common";
import { ConfirmDialog } from "@medclinic/web-ui/components/ConfirmDialog";
import { Badge } from "@medclinic/web-ui/components/ui/badge";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Card } from "@medclinic/web-ui/components/ui/card";
import { Select } from "@medclinic/web-ui/components/ui/input";
import { Table, Td, Th, Tr } from "@medclinic/web-ui/components/ui/table";
import { formatDateTime, formatPhone } from "@medclinic/web-ui/lib/format";
import { useRoles, useStaff, useStaffAction } from "@/api/hooks";
import type { Staff, StaffWithPassword } from "@/api/types";
import { usePermissions } from "@/features/auth/permissions";
import { roleText } from "@/features/roles/RoleLabel";
import { StaffDialog } from "./StaffDialog";
import { TemporaryPasswordDialog } from "./TemporaryPasswordDialog";

type Pending = { staff: Staff; action: "activate" | "deactivate" | "reset-password" };

export function StaffPage() {
  const { t } = useTranslation();
  const { me, can } = usePermissions();
  const manage = can("STAFF_MANAGE");
  const [search, setSearch] = React.useState("");
  const [q, setQ] = React.useState("");
  const [roleId, setRoleId] = React.useState("");
  const [includeInactive, setIncludeInactive] = React.useState(false);
  const [page, setPage] = React.useState(0);
  const roles = useRoles();
  const { data, isPending, error, refetch } = useStaff({ q: q || undefined, roleId: roleId || undefined, includeInactive, page });
  const action = useStaffAction();
  const [editing, setEditing] = React.useState<Staff | "new" | null>(null);
  const [pending, setPending] = React.useState<Pending | null>(null);
  const [password, setPassword] = React.useState<StaffWithPassword | null>(null);

  React.useEffect(() => {
    const id = setTimeout(() => {
      setQ(search.trim());
      setPage(0);
    }, 250);
    return () => clearTimeout(id);
  }, [search]);

  const roleById = new Map((roles.data ?? []).map((r) => [r.id, r]));

  return (
    <>
      <PageHeader
        title={t("staff.title")}
        subtitle={data && t("common.total", { count: data.totalItems })}
        actions={
          manage && (
            <Button size="lg" onClick={() => setEditing("new")}>
              <Plus /> {t("staff.new")}
            </Button>
          )
        }
      />
      <div className="mb-5 flex flex-wrap items-center gap-3">
        <SearchBox value={search} onChange={setSearch} placeholder={t("staff.searchPlaceholder")} />
        <Select value={roleId} onChange={(e) => { setRoleId(e.target.value); setPage(0); }} className="h-11 w-56" aria-label={t("staff.role")}>
          <option value="">{t("staff.allRoles")}</option>
          {roles.data?.map((r) => (
            <option key={r.id} value={r.id}>
              {roleText(t, r)}
            </option>
          ))}
        </Select>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" className="size-4 accent-[var(--color-primary)]" checked={includeInactive} onChange={(e) => setIncludeInactive(e.target.checked)} />
          {t("staff.includeInactive")}
        </label>
      </div>
      <Card>
        {isPending ? (
          <Spinner />
        ) : error ? (
          <ErrorState error={error} onRetry={() => void refetch()} />
        ) : data.items.length === 0 ? (
          <EmptyState text={t("staff.empty")} />
        ) : (
          <>
            <Table>
              <thead>
                <tr>
                  <Th>{t("staff.fullName")}</Th>
                  <Th>{t("staff.role")}</Th>
                  <Th>{t("common.phone")}</Th>
                  <Th>{t("staff.lastLogin")}</Th>
                  {manage && <Th className="text-right">{t("common.actions")}</Th>}
                </tr>
              </thead>
              <tbody>
                {data.items.map((s) => {
                  const self = s.id === me?.id;
                  const role = roleById.get(s.roleId);
                  return (
                    <Tr key={s.id} className={s.active ? undefined : "opacity-55"}>
                      <Td>
                        <div className="font-medium">
                          {s.fullName} {self && <span className="text-xs text-muted-foreground">({t("staff.you")})</span>}
                        </div>
                        <div className="text-xs text-muted-foreground">
                          <span className="font-mono">{s.login}</span>
                          {s.position && ` · ${s.position}`}
                        </div>
                      </Td>
                      <Td>
                        <div className="flex flex-wrap gap-1">
                          <Badge tone={role?.system ? "blue" : "gray"}>{role ? roleText(t, role) : "—"}</Badge>
                          {s.granted.length + s.denied.length > 0 && <Badge tone="amber">±{s.granted.length + s.denied.length}</Badge>}
                          {!s.active && <Badge tone="red">{t("staff.inactive")}</Badge>}
                          {s.active && s.mustChangePassword && <Badge tone="amber">{t("staff.temporaryPassword")}</Badge>}
                        </div>
                      </Td>
                      <Td className="whitespace-nowrap">{s.phone ? formatPhone(s.phone) : "—"}</Td>
                      <Td className="whitespace-nowrap">{formatDateTime(s.lastLoginAt)}</Td>
                      {manage && (
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
                                <Button size="sm" variant="ghost" className="text-danger" onClick={() => setPending({ staff: s, action: "deactivate" })}>
                                  {t("staff.deactivate")}
                                </Button>
                              ) : (
                                <Button size="sm" onClick={() => setPending({ staff: s, action: "activate" })}>
                                  {t("staff.activate")}
                                </Button>
                              ))}
                          </div>
                        </Td>
                      )}
                    </Tr>
                  );
                })}
              </tbody>
            </Table>
            <Pagination page={page} totalPages={data.totalPages} onPage={setPage} />
          </>
        )}
      </Card>

      <StaffDialog
        staff={editing === "new" ? undefined : (editing ?? undefined)}
        roles={roles.data ?? []}
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
