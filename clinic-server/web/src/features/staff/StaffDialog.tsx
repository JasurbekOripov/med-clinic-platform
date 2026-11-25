import { ChevronDown, ImageUp, Trash2 } from "lucide-react";
import * as React from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { FormError } from "@medclinic/web-ui/components/Common";
import { Field } from "@medclinic/web-ui/components/Field";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogTitle } from "@medclinic/web-ui/components/ui/dialog";
import { Input, PhoneInput, Select } from "@medclinic/web-ui/components/ui/input";
import { fieldErrors, errorMessage } from "@medclinic/web-ui/lib/errors";
import { formatPhoneInput, isEmptyPhone, isValidUzPhone } from "@medclinic/web-ui/lib/phone";
import { useSaveStaff, useSignatureUpload } from "@/api/hooks";
import type { Role, Staff, StaffWithPassword } from "@/api/types";
import { roleText } from "@/features/roles/RoleLabel";
import { PermissionPicker } from "./PermissionPicker";

interface Values {
  login: string;
  fullName: string;
  position: string;
  phone: string;
  roleId: string;
}

export function StaffDialog({
  staff,
  roles,
  open,
  onOpenChange,
  onCreated,
}: {
  staff?: Staff;
  roles: Role[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreated: (result: StaffWithPassword) => void;
}) {
  const { t } = useTranslation();
  const save = useSaveStaff();
  const assignable = roles.filter((r) => r.assignable || r.id === staff?.roleId);
  const { register, handleSubmit, reset, watch, formState } = useForm<Values>();
  const [granted, setGranted] = React.useState<Set<string>>(new Set());
  const [denied, setDenied] = React.useState<Set<string>>(new Set());
  const [showPermissions, setShowPermissions] = React.useState(false);

  React.useEffect(() => {
    if (!open) return;
    reset({
      login: staff?.login ?? "",
      fullName: staff?.fullName ?? "",
      position: staff?.position ?? "",
      phone: staff?.phone ? formatPhoneInput(staff.phone) : "",
      roleId: staff?.roleId ?? assignable.find((r) => r.template === "RECEPTION")?.id ?? assignable[0]?.id ?? "",
    });
    setGranted(new Set(staff?.granted ?? []));
    setDenied(new Set(staff?.denied ?? []));
    setShowPermissions((staff?.granted.length ?? 0) + (staff?.denied.length ?? 0) > 0);
    save.reset();
  }, [open, staff]);

  const role = roles.find((r) => r.id === watch("roleId"));
  const rolePermissions = new Set(role?.permissions ?? []);
  const effective = new Set([...rolePermissions, ...granted].filter((p) => !denied.has(p)));
  const server = fieldErrors(t, save.error);
  const required = (f: keyof Values) => server[f] ?? (formState.errors[f] ? t("errors.validation.not_blank") : undefined);

  const toggle = (p: string, checked: boolean) => {
    const g = new Set(granted);
    const d = new Set(denied);
    if (rolePermissions.has(p)) {
      if (checked) d.delete(p);
      else d.add(p);
    } else if (checked) g.add(p);
    else g.delete(p);
    setGranted(g);
    setDenied(d);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent wide className="max-w-3xl">
        <DialogTitle>{staff ? t("staff.edit") : t("staff.new")}</DialogTitle>
        <form
          className="mt-5 grid gap-4 sm:grid-cols-2"
          onSubmit={handleSubmit((v) => {
            const keep = (set: Set<string>) => (role?.system ? [] : [...set]);
            const body = {
              login: staff ? undefined : v.login,
              fullName: v.fullName,
              position: v.position || null,
              phone: isEmptyPhone(v.phone) ? null : v.phone,
              roleId: v.roleId,
              granted: keep(new Set([...granted].filter((p) => !rolePermissions.has(p)))),
              denied: keep(new Set([...denied].filter((p) => rolePermissions.has(p)))),
              version: staff?.version,
            };
            save.mutate(
              { id: staff?.id, body },
              {
                onSuccess: (result) => {
                  onOpenChange(false);
                  if (staff) toast.success(t("common.saved"));
                  else onCreated(result as StaffWithPassword);
                },
              },
            );
          })}
        >
          <Field label={t("staff.fullName")} error={required("fullName")} className="sm:col-span-2">
            <Input autoFocus {...register("fullName", { required: true })} />
          </Field>
          {!staff && (
            <Field label={t("staff.login")} hint={t("staff.loginHint")} error={server.login ?? (formState.errors.login && t("errors.validation.login_format"))}>
              <Input autoCapitalize="none" spellCheck={false} {...register("login", { required: true, pattern: /^[a-zA-Z0-9][a-zA-Z0-9._-]{2,49}$/ })} />
            </Field>
          )}
          <Field label={t("staff.role")} error={server.roleId}>
            <Select {...register("roleId", { required: true })}>
              {assignable.map((r) => (
                <option key={r.id} value={r.id}>
                  {roleText(t, r)}
                </option>
              ))}
            </Select>
          </Field>
          <Field label={t("staff.position")} optional error={server.position}>
            <Input {...register("position")} />
          </Field>
          <Field label={t("common.phone")} optional error={server.phone ?? (formState.errors.phone && t("errors.validation.phone"))}>
            <PhoneInput {...register("phone", { validate: (v) => isEmptyPhone(v) || isValidUzPhone(v) })} />
          </Field>

          {!role?.system && (
            <div className="sm:col-span-2">
              <button
                type="button"
                className="flex w-full items-center justify-between rounded-lg bg-muted px-4 py-3 text-left text-sm font-medium"
                onClick={() => setShowPermissions(!showPermissions)}
                aria-expanded={showPermissions}
              >
                <span>
                  {t("staff.extraPermissions")}
                  {granted.size + denied.size > 0 && <span className="ml-2 text-primary">({granted.size + denied.size})</span>}
                </span>
                <ChevronDown className={`size-4 transition-transform ${showPermissions ? "rotate-180" : ""}`} />
              </button>
              {showPermissions && (
                <div className="mt-3">
                  <p className="mb-4 text-sm text-muted-foreground">{t("staff.extraPermissionsHint")}</p>
                  <PermissionPicker selected={effective} fromRole={rolePermissions} onToggle={toggle} />
                </div>
              )}
            </div>
          )}

          {staff && <SignatureField staff={staff} />}

          <div className="sm:col-span-2">
            {Object.keys(server).length === 0 && <FormError error={save.error} />}
            <DialogFooter>
              <Button variant="secondary" onClick={() => onOpenChange(false)}>
                {t("common.cancel")}
              </Button>
              <Button type="submit" loading={save.isPending}>
                {staff ? t("common.save") : t("common.create")}
              </Button>
            </DialogFooter>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function SignatureField({ staff }: { staff: Staff }) {
  const { t } = useTranslation();
  const upload = useSignatureUpload();
  const input = React.useRef<HTMLInputElement>(null);
  const [version, setVersion] = React.useState(0);
  return (
    <div className="rounded-lg border border-dashed border-border p-4 sm:col-span-2">
      <div className="mb-2 text-sm font-medium">{t("staff.signature")}</div>
      <div className="flex flex-wrap items-center gap-4">
        <div className="flex h-20 w-48 items-center justify-center rounded-md bg-white">
          {staff.hasSignature || version > 0 ? (
            <img src={`/api/v1/staff/${staff.id}/signature?v=${version}`} alt={t("staff.signature")} className="max-h-full max-w-full object-contain" />
          ) : (
            <span className="text-xs text-muted-foreground">—</span>
          )}
        </div>
        <div className="flex flex-col gap-2">
          <input
            ref={input}
            type="file"
            accept="image/png,image/jpeg"
            className="hidden"
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) {
                upload.mutate({ id: staff.id, file }, {
                  onSuccess: () => setVersion((v) => v + 1),
                  onError: (err) => toast.error(fieldErrors(t, err).file ?? errorMessage(t, err)),
                });
              }
              e.target.value = "";
            }}
          />
          <Button size="sm" variant="secondary" loading={upload.isPending} onClick={() => input.current?.click()}>
            <ImageUp /> {t("staff.uploadSignature")}
          </Button>
          {staff.hasSignature && (
            <Button size="sm" variant="ghost" onClick={() => upload.mutate({ id: staff.id, file: null })}>
              <Trash2 /> {t("staff.removeSignature")}
            </Button>
          )}
        </div>
      </div>
      <p className="mt-2 text-xs text-muted-foreground">{t("staff.signatureHint")}</p>
    </div>
  );
}
