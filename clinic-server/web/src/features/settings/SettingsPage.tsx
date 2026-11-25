import { Building2, Clock, ImageUp, Printer, ShieldCheck, Trash2 } from "lucide-react";
import * as React from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { ErrorState, FormError, PageHeader, Spinner } from "@medclinic/web-ui/components/Common";
import { Field } from "@medclinic/web-ui/components/Field";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@medclinic/web-ui/components/ui/card";
import { Input, PhoneInput, Textarea } from "@medclinic/web-ui/components/ui/input";
import { errorMessage, fieldErrors } from "@medclinic/web-ui/lib/errors";
import { formatPhoneInput, isEmptyPhone, isValidUzPhone } from "@medclinic/web-ui/lib/phone";
import { useLogoUpload, useSaveSettings, useSettings } from "@/api/hooks";
import type { Settings, WorkingDay } from "@/api/types";

interface Values {
  name: string;
  legalName: string;
  address: string;
  phone: string;
  letterhead: string;
  receiptHeader: string;
  receiptFooter: string;
  idleLockMinutes: number;
  hours: { closed: boolean; opensAt: string; closesAt: string }[];
}

const DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"] as const;

function toValues(s: Settings): Values {
  return {
    name: s.name,
    legalName: s.legalName ?? "",
    address: s.address ?? "",
    phone: s.phone ? formatPhoneInput(s.phone) : "",
    letterhead: s.letterhead ?? "",
    receiptHeader: s.receiptHeader ?? "",
    receiptFooter: s.receiptFooter ?? "",
    idleLockMinutes: s.idleLockMinutes,
    hours: DAYS.map((day) => {
      const d = s.workingHours.find((w) => w.day === day);
      return { closed: d?.closed ?? true, opensAt: d?.opensAt?.slice(0, 5) ?? "08:00", closesAt: d?.closesAt?.slice(0, 5) ?? "18:00" };
    }),
  };
}

export function SettingsPage() {
  const { t } = useTranslation();
  const { data, isPending, error, refetch } = useSettings();
  if (isPending) return <Spinner />;
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />;
  return (
    <>
      <PageHeader title={t("settings.title")} />
      <SettingsForm settings={data} />
    </>
  );
}

function SettingsForm({ settings }: { settings: Settings }) {
  const { t } = useTranslation();
  const save = useSaveSettings();
  const { register, handleSubmit, reset, watch, formState } = useForm<Values>({ defaultValues: toValues(settings) });
  React.useEffect(() => reset(toValues(settings)), [settings, reset]);
  const server = fieldErrors(t, save.error);
  const hours = watch("hours");

  return (
    <form
      className="space-y-6"
      onSubmit={handleSubmit((v) =>
        save.mutate(
          {
            name: v.name,
            legalName: v.legalName || null,
            address: v.address || null,
            phone: isEmptyPhone(v.phone) ? null : v.phone,
            letterhead: v.letterhead || null,
            receiptHeader: v.receiptHeader || null,
            receiptFooter: v.receiptFooter || null,
            idleLockMinutes: Number(v.idleLockMinutes),
            workingHours: DAYS.map((day, i): WorkingDay => {
              const h = v.hours[i]!;
              return h.closed ? { day, closed: true } : { day, closed: false, opensAt: h.opensAt, closesAt: h.closesAt };
            }),
            version: settings.version,
          },
          { onSuccess: () => toast.success(t("common.saved")) },
        ),
      )}
    >
      <Section icon={Building2} title={t("settings.clinic")}>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label={t("settings.name")} error={server.name ?? (formState.errors.name && t("errors.validation.not_blank"))} className="sm:col-span-2">
            <Input {...register("name", { required: true })} />
          </Field>
          <Field label={t("settings.legalName")} optional error={server.legalName}>
            <Input {...register("legalName")} />
          </Field>
          <Field label={t("common.phone")} optional error={server.phone ?? (formState.errors.phone && t("errors.validation.phone"))}>
            <PhoneInput {...register("phone", { validate: (v) => isEmptyPhone(v) || isValidUzPhone(v) })} />
          </Field>
          <Field label={t("common.address")} optional error={server.address} className="sm:col-span-2">
            <Input {...register("address")} />
          </Field>
        </div>
        <LogoField hasLogo={settings.hasLogo} />
      </Section>

      <Section icon={Printer} title={t("settings.printing")}>
        <div className="grid gap-4">
          <Field label={t("settings.letterhead")} hint={t("settings.letterheadHint")} optional error={server.letterhead}>
            <Textarea rows={3} {...register("letterhead")} />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label={t("settings.receiptHeader")} optional error={server.receiptHeader}>
              <Textarea rows={3} {...register("receiptHeader")} />
            </Field>
            <Field label={t("settings.receiptFooter")} optional error={server.receiptFooter}>
              <Textarea rows={3} {...register("receiptFooter")} />
            </Field>
          </div>
        </div>
      </Section>

      <Section icon={Clock} title={t("settings.hours")}>
        <div className="divide-y divide-border">
          {DAYS.map((day, i) => (
            <div key={day} className="flex flex-wrap items-center gap-4 py-2.5">
              <div className="w-32 font-medium">{t(`day.${day}`)}</div>
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" className="size-4 accent-[var(--color-primary)]" {...register(`hours.${i}.closed`)} />
                {t("settings.closed")}
              </label>
              {!hours[i]?.closed && (
                <div className="flex items-center gap-2">
                  <Input type="time" className="w-32" aria-label={t("settings.opensAt")} {...register(`hours.${i}.opensAt`)} />
                  <span className="text-muted-foreground">—</span>
                  <Input type="time" className="w-32" aria-label={t("settings.closesAt")} {...register(`hours.${i}.closesAt`)} />
                </div>
              )}
              {server[`workingHours.${day}`] && <span className="text-xs font-medium text-danger">{server[`workingHours.${day}`]}</span>}
            </div>
          ))}
        </div>
      </Section>

      <Section icon={ShieldCheck} title={t("settings.security")}>
        <Field label={t("settings.idleLock")} hint={t("settings.idleLockHint")} error={server.idleLockMinutes} className="max-w-sm">
          <Input type="number" min={1} max={240} {...register("idleLockMinutes")} />
        </Field>
      </Section>

      {Object.keys(server).length === 0 && <FormError error={save.error} />}
      <div className="sticky bottom-4 flex justify-end">
        <Button type="submit" size="lg" className="shadow-lg" loading={save.isPending}>
          {t("common.save")}
        </Button>
      </div>
    </form>
  );
}

function Section({ icon: Icon, title, children }: { icon: typeof Clock; title: string; children: React.ReactNode }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Icon className="size-5 text-primary" aria-hidden /> {title}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-5">{children}</CardContent>
    </Card>
  );
}

function LogoField({ hasLogo }: { hasLogo: boolean }) {
  const { t } = useTranslation();
  const upload = useLogoUpload();
  const input = React.useRef<HTMLInputElement>(null);
  const [version, setVersion] = React.useState(0);
  return (
    <div className="flex flex-wrap items-center gap-5 rounded-lg border border-dashed border-border p-4">
      <div className="flex size-24 items-center justify-center rounded-xl bg-white">
        {hasLogo ? <img src={`/api/v1/settings/logo?v=${version}`} alt={t("settings.logo")} className="max-h-full max-w-full object-contain" /> : <span className="text-xs text-muted-foreground">—</span>}
      </div>
      <div className="space-y-2">
        <div className="text-sm font-medium">{t("settings.logo")}</div>
        <p className="max-w-sm text-xs text-muted-foreground">{t("settings.logoHint")}</p>
        <div className="flex gap-2">
          <input
            ref={input}
            type="file"
            accept="image/png,image/jpeg"
            className="hidden"
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) upload.mutate(file, { onSuccess: () => setVersion((v) => v + 1), onError: (err) => toast.error(fieldErrors(t, err).file ?? errorMessage(t, err)) });
              e.target.value = "";
            }}
          />
          <Button size="sm" variant="secondary" loading={upload.isPending} onClick={() => input.current?.click()}>
            <ImageUp /> {t("common.uploadFile")}
          </Button>
          {hasLogo && (
            <Button size="sm" variant="ghost" onClick={() => upload.mutate(null)}>
              <Trash2 /> {t("common.remove")}
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
