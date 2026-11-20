import * as React from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { useSaveClinic } from "@/api/hooks";
import { type Clinic, type ClinicRequest, REGIONS, type Region } from "@/api/types";
import { FormError } from "@/components/Common";
import { Field } from "@/components/Field";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogTitle } from "@/components/ui/dialog";
import { Input, Select, Textarea } from "@/components/ui/input";
import { fieldErrors } from "@/lib/errors";

interface Values {
  name: string;
  legalName: string;
  ownerName: string;
  phone: string;
  address: string;
  region: Region | "";
  notes: string;
}

function toValues(c?: Clinic): Values {
  return {
    name: c?.name ?? "",
    legalName: c?.legalName ?? "",
    ownerName: c?.ownerName ?? "",
    phone: c?.phone ?? "+998 ",
    address: c?.address ?? "",
    region: c?.region ?? "",
    notes: c?.notes ?? "",
  };
}

export function ClinicFormDialog({
  open,
  onOpenChange,
  clinic,
  onSaved,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  clinic?: Clinic;
  onSaved?: (clinic: Clinic) => void;
}) {
  const { t } = useTranslation();
  const save = useSaveClinic();
  const { register, handleSubmit, reset, formState } = useForm<Values>({ defaultValues: toValues(clinic) });

  React.useEffect(() => {
    if (open) {
      reset(toValues(clinic));
      save.reset();
    }
  }, [open, clinic]);

  // Server validation is authoritative (phone format, trimming); client checks only catch empty required fields.
  const server = fieldErrors(t, save.error);
  const required = (field: keyof Values) => server[field] ?? (formState.errors[field] ? t("errors.validation.not_blank") : undefined);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent wide>
        <DialogTitle>{clinic ? t("clinics.edit") : t("clinics.new")}</DialogTitle>
        <form
          className="mt-5 grid gap-4 sm:grid-cols-2"
          onSubmit={handleSubmit((v) => {
            const body: ClinicRequest = {
              name: v.name,
              legalName: v.legalName || null,
              ownerName: v.ownerName,
              phone: v.phone,
              address: v.address || null,
              region: v.region || null,
              notes: v.notes || null,
              version: clinic?.version,
            };
            save.mutate(
              { id: clinic?.id, body },
              {
                onSuccess: (saved) => {
                  toast.success(clinic ? t("common.saved") : t("clinics.created"));
                  onOpenChange(false);
                  onSaved?.(saved);
                },
              },
            );
          })}
        >
          <Field label={t("common.name")} error={required("name")} className="sm:col-span-2">
            <Input autoFocus {...register("name", { required: true })} />
          </Field>
          <Field label={t("clinics.legalName")} error={server.legalName} optional className="sm:col-span-2">
            <Input {...register("legalName")} />
          </Field>
          <Field label={t("clinics.ownerName")} error={required("ownerName")}>
            <Input {...register("ownerName", { required: true })} />
          </Field>
          <Field label={t("common.phone")} error={server.phone}>
            <Input type="tel" inputMode="tel" {...register("phone")} />
          </Field>
          <Field label={t("clinics.region")} error={required("region")}>
            <Select {...register("region", { required: true })}>
              <option value="">—</option>
              {REGIONS.map((r) => (
                <option key={r} value={r}>
                  {t(`region.${r}`)}
                </option>
              ))}
            </Select>
          </Field>
          <Field label={t("common.address")} error={server.address} optional>
            <Input {...register("address")} />
          </Field>
          <Field label={t("common.notes")} error={server.notes} optional className="sm:col-span-2">
            <Textarea rows={3} {...register("notes")} />
          </Field>
          <div className="sm:col-span-2">
            {Object.keys(server).length === 0 && <FormError error={save.error} />}
            <DialogFooter>
              <Button variant="secondary" onClick={() => onOpenChange(false)}>
                {t("common.cancel")}
              </Button>
              <Button type="submit" loading={save.isPending}>
                {clinic ? t("common.save") : t("common.create")}
              </Button>
            </DialogFooter>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
