import { AlertTriangle, ArrowRight } from "lucide-react";
import * as React from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import { FormError } from "@medclinic/web-ui/components/Common";
import { Field } from "@medclinic/web-ui/components/Field";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogTitle } from "@medclinic/web-ui/components/ui/dialog";
import { Input, PhoneInput, Textarea } from "@medclinic/web-ui/components/ui/input";
import { fieldErrors } from "@medclinic/web-ui/lib/errors";
import { formatDate, formatPhone, todayInTashkent } from "@medclinic/web-ui/lib/format";
import { formatPhoneInput, isEmptyPhone, isValidUzPhone } from "@medclinic/web-ui/lib/phone";
import { cn } from "@medclinic/web-ui/lib/utils";
import { duplicateCandidates, useDuplicateCheck, useSavePatient } from "@/api/hooks";
import type { DuplicateCandidate, Gender, Patient, PatientRequest } from "@/api/types";

interface Values {
  lastName: string;
  firstName: string;
  patronymic: string;
  gender: Gender | "";
  birthDate: string;
  phone: string;
  address: string;
  notes: string;
}

function useDebounced<T>(value: T, ms = 400): T {
  const [v, setV] = React.useState(value);
  React.useEffect(() => {
    const id = setTimeout(() => setV(value), ms);
    return () => clearTimeout(id);
  }, [value, ms]);
  return v;
}

/**
 * New or edit patient. While typing, possible duplicates are shown so the receptionist can open the existing card
 * instead of creating a second one; creating anyway requires an explicit confirmation.
 */
export function PatientDialog({
  patient,
  open,
  onOpenChange,
  initialSearch,
}: {
  patient?: Patient;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Text typed into the search box, used to prefill the name when nothing was found. */
  initialSearch?: string;
}) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const save = useSavePatient();
  const { register, handleSubmit, reset, watch, setValue, formState } = useForm<Values>();

  React.useEffect(() => {
    if (!open) return;
    const words = (initialSearch ?? "").trim().split(/\s+/).filter((w) => w && !/\d/.test(w));
    reset({
      lastName: patient?.lastName ?? words[0] ?? "",
      firstName: patient?.firstName ?? words[1] ?? "",
      patronymic: patient?.patronymic ?? words.slice(2).join(" "),
      gender: patient?.gender ?? "",
      birthDate: patient?.birthDate ?? "",
      phone: patient?.phone ? formatPhoneInput(patient.phone) : "",
      address: patient?.address ?? "",
      notes: patient?.notes ?? "",
    });
    save.reset();
  }, [open, patient]);

  const values = watch();
  const probe = useDebounced({
    lastName: values.lastName,
    firstName: values.firstName,
    gender: values.gender || undefined,
    birthDate: values.birthDate || undefined,
    phone: values.phone,
  });
  const ready = Boolean(probe.lastName && probe.firstName && probe.birthDate && isValidUzPhone(probe.phone ?? ""));
  const live = useDuplicateCheck({ ...probe, gender: (probe.gender as Gender | undefined) ?? "MALE" }, patient?.id, open && ready);
  const blocked = duplicateCandidates(save.error);
  const candidates = blocked.length ? blocked : (live.data ?? []);
  const server = fieldErrors(t, save.error);
  const required = (f: keyof Values) => server[f] ?? (formState.errors[f] ? t("errors.validation.not_blank") : undefined);

  const submit = (v: Values, confirmedNotDuplicate: boolean) => {
    const body: PatientRequest = {
      lastName: v.lastName,
      firstName: v.firstName,
      patronymic: v.patronymic || null,
      gender: v.gender || null,
      birthDate: v.birthDate || null,
      phone: isEmptyPhone(v.phone) ? null : v.phone,
      address: v.address || null,
      notes: v.notes || null,
      confirmedNotDuplicate,
      version: patient?.version,
    };
    save.mutate(
      { id: patient?.id, body },
      {
        onSuccess: (saved) => {
          onOpenChange(false);
          toast.success(patient ? t("common.saved") : t("patients.created"));
          if (!patient) navigate(`/patients/${saved.id}`);
        },
      },
    );
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent wide className="max-w-3xl">
        <DialogTitle>{patient ? t("patients.edit") : t("patients.new")}</DialogTitle>
        <form className="mt-5 grid gap-4 sm:grid-cols-3" onSubmit={handleSubmit((v) => submit(v, false))}>
          <Field label={t("patients.lastName")} error={required("lastName")}>
            <Input autoFocus className="h-11 text-base" {...register("lastName", { required: true })} />
          </Field>
          <Field label={t("patients.firstName")} error={required("firstName")}>
            <Input className="h-11 text-base" {...register("firstName", { required: true })} />
          </Field>
          <Field label={t("patients.patronymic")} optional error={server.patronymic}>
            <Input className="h-11 text-base" {...register("patronymic")} />
          </Field>
          <Field label={t("patients.birthDate")} error={server.birthDate ?? (formState.errors.birthDate && t("errors.validation.not_null"))}>
            <Input type="date" className="h-11 text-base" max={todayInTashkent()} {...register("birthDate", { required: true })} />
          </Field>
          <div>
            <div className="mb-1.5 text-sm font-medium">{t("patients.gender")}</div>
            <div className="grid grid-cols-2 gap-2" role="radiogroup" aria-label={t("patients.gender")}>
              {(["MALE", "FEMALE"] as const).map((g) => (
                <button
                  key={g}
                  type="button"
                  role="radio"
                  aria-checked={values.gender === g}
                  onClick={() => setValue("gender", g, { shouldValidate: true })}
                  className={cn(
                    "h-11 rounded-lg border text-sm font-medium transition-colors",
                    values.gender === g ? "border-primary bg-primary/12 text-primary" : "border-border bg-surface hover:bg-muted",
                  )}
                >
                  {t(`gender.${g}`)}
                </button>
              ))}
            </div>
            <input type="hidden" {...register("gender", { required: true })} />
            {(server.gender || formState.errors.gender) && <p className="mt-1 text-xs font-medium text-danger">{server.gender ?? t("errors.validation.not_null")}</p>}
          </div>
          <Field label={t("common.phone")} error={server.phone ?? (formState.errors.phone && t("errors.validation.phone"))}>
            <PhoneInput className="h-11 text-base" {...register("phone", { validate: (v) => !isEmptyPhone(v) && isValidUzPhone(v) })} />
          </Field>
          <Field label={t("common.address")} optional error={server.address} className="sm:col-span-3">
            <Input {...register("address")} />
          </Field>
          <Field label={t("patients.notes")} optional error={server.notes} className="sm:col-span-3">
            <Textarea rows={2} {...register("notes")} />
          </Field>

          {candidates.length > 0 && (
            <DuplicatePanel
              candidates={candidates}
              blocked={blocked.length > 0}
              onOpen={(id) => {
                onOpenChange(false);
                navigate(`/patients/${id}`);
              }}
              onCreateAnyway={handleSubmit((v) => submit(v, true))}
            />
          )}

          <div className="sm:col-span-3">
            {Object.keys(server).length === 0 && blocked.length === 0 && <FormError error={save.error} />}
            <DialogFooter>
              <Button variant="secondary" onClick={() => onOpenChange(false)}>
                {t("common.cancel")}
              </Button>
              <Button type="submit" size="lg" loading={save.isPending}>
                {patient ? t("common.save") : t("common.create")}
              </Button>
            </DialogFooter>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function DuplicatePanel({
  candidates,
  blocked,
  onOpen,
  onCreateAnyway,
}: {
  candidates: DuplicateCandidate[];
  blocked: boolean;
  onOpen: (id: string) => void;
  onCreateAnyway: () => void;
}) {
  const { t } = useTranslation();
  return (
    <div className="rounded-xl border border-amber-300 bg-amber-50 p-4 sm:col-span-3" role="alert">
      <div className="flex items-center gap-2 font-semibold text-amber-900">
        <AlertTriangle className="size-5" aria-hidden /> {t("patients.duplicateTitle")}
      </div>
      <p className="mt-1 text-sm text-amber-900/80">{t("patients.duplicateHint")}</p>
      <ul className="mt-3 space-y-2">
        {candidates.map((c) => (
          <li key={c.patient.id} className="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-white px-3 py-2">
            <div className="text-sm">
              <div className="font-medium">
                {c.patient.fullName} <span className="text-muted-foreground">· {t("patients.card")} {c.patient.cardNo}</span>
              </div>
              <div className="text-xs text-muted-foreground">
                {formatDate(c.patient.birthDate)} · {formatPhone(c.patient.phone)} · {t(`patients.reason${c.reason}`)}
              </div>
            </div>
            <Button size="sm" onClick={() => onOpen(c.patient.id)}>
              {t("patients.open")} <ArrowRight />
            </Button>
          </li>
        ))}
      </ul>
      {blocked && (
        <Button variant="link" className="mt-2 text-amber-900" onClick={onCreateAnyway}>
          {t("patients.createAnyway")}
        </Button>
      )}
    </div>
  );
}
