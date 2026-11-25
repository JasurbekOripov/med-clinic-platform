import { CheckCircle2, Link2, UserRoundPlus } from "lucide-react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { Navigate, useNavigate } from "react-router";
import { FormError, Spinner } from "@medclinic/web-ui/components/Common";
import { Field } from "@medclinic/web-ui/components/Field";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Card, CardContent } from "@medclinic/web-ui/components/ui/card";
import { Input } from "@medclinic/web-ui/components/ui/input";
import { fieldErrors } from "@medclinic/web-ui/lib/errors";
import { cn } from "@medclinic/web-ui/lib/utils";
import { useCreateFirstAdmin, usePair, useSetupStatus } from "@/api/hooks";
import { LanguageSwitcher } from "@/features/layout/LanguageSwitcher";

const MIN_PASSWORD = 8;

/** First run on a new clinic PC: connect to the vendor, then create the Admin. */
export function SetupPage() {
  const { t } = useTranslation();
  const { data, isPending } = useSetupStatus();
  if (isPending) return <Spinner className="h-screen" />;
  if (data?.completed) return <Navigate to="/login" replace />;
  const step = data?.paired ? 2 : 1;

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-8 px-4 py-10">
      <div className="text-center">
        <h1 className="text-3xl font-semibold">{t("setup.title")}</h1>
        <p className="mt-2 text-muted-foreground">{t("setup.subtitle")}</p>
      </div>
      <ol className="flex items-center gap-3 text-sm">
        <StepChip number={1} label={t("setup.step1")} state={step === 1 ? "current" : "done"} />
        <span className="h-px w-10 bg-border" aria-hidden />
        <StepChip number={2} label={t("setup.step2")} state={step === 2 ? "current" : "todo"} />
      </ol>
      <Card className="w-full max-w-md">
        <CardContent className="p-7">{step === 1 ? <PairStep /> : <AdminStep clinic={data?.clinicCode} branch={data?.branchCode} />}</CardContent>
      </Card>
      <LanguageSwitcher />
    </div>
  );
}

function StepChip({ number, label, state }: { number: number; label: string; state: "done" | "current" | "todo" }) {
  return (
    <li className={cn("flex items-center gap-2", state === "todo" && "text-muted-foreground")}>
      <span
        className={cn(
          "flex size-7 items-center justify-center rounded-full text-xs font-semibold",
          state === "done" && "bg-primary text-primary-foreground",
          state === "current" && "bg-primary/15 text-primary ring-2 ring-primary",
          state === "todo" && "bg-muted",
        )}
      >
        {state === "done" ? <CheckCircle2 className="size-4" /> : number}
      </span>
      {label}
    </li>
  );
}

function PairStep() {
  const { t } = useTranslation();
  const pair = usePair();
  const { register, handleSubmit, formState } = useForm<{ vendorUrl: string; code: string }>({ defaultValues: { vendorUrl: "https://", code: "" } });
  const server = fieldErrors(t, pair.error);
  return (
    <form className="space-y-5" onSubmit={handleSubmit((v) => pair.mutate(v))}>
      <div className="flex items-center gap-3">
        <Link2 className="size-6 text-primary" aria-hidden />
        <h2 className="text-lg font-semibold">{t("setup.step1")}</h2>
      </div>
      <Field label={t("setup.vendorUrl")} hint={t("setup.vendorUrlHint")} error={server.vendorUrl ?? (formState.errors.vendorUrl && t("errors.validation.not_blank"))}>
        <Input autoComplete="url" spellCheck={false} {...register("vendorUrl", { required: true })} />
      </Field>
      <Field label={t("setup.code")} hint={t("setup.codeHint")} error={server.code ?? (formState.errors.code && t("errors.validation.not_blank"))}>
        <Input autoFocus className="h-12 font-mono text-lg tracking-widest uppercase" autoComplete="off" spellCheck={false} {...register("code", { required: true })} />
      </Field>
      {Object.keys(server).length === 0 && <FormError error={pair.error} />}
      <Button type="submit" size="lg" className="w-full" loading={pair.isPending}>
        {t("setup.connect")}
      </Button>
    </form>
  );
}

interface AdminValues {
  fullName: string;
  login: string;
  password: string;
  repeat: string;
  vendorSupportPassword: string;
}

function AdminStep({ clinic, branch }: { clinic?: string | null; branch?: string | null }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const create = useCreateFirstAdmin();
  const { register, handleSubmit, formState, getValues } = useForm<AdminValues>({ defaultValues: { login: "admin" } });
  const server = fieldErrors(t, create.error);
  const rules = t("auth.passwordRules", { min: MIN_PASSWORD });
  return (
    <form
      className="space-y-5"
      onSubmit={handleSubmit((v) =>
        create.mutate(
          { fullName: v.fullName, login: v.login, password: v.password, vendorSupportPassword: v.vendorSupportPassword || null },
          { onSuccess: () => navigate("/login", { replace: true }) },
        ),
      )}
    >
      <p className="flex items-center gap-2 rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
        <CheckCircle2 className="size-4" aria-hidden /> {t("setup.connected", { clinic, branch })}
      </p>
      <div className="flex items-center gap-3">
        <UserRoundPlus className="size-6 text-primary" aria-hidden />
        <div>
          <h2 className="text-lg font-semibold">{t("setup.adminTitle")}</h2>
          <p className="text-sm text-muted-foreground">{t("setup.adminHint")}</p>
        </div>
      </div>
      <Field label={t("setup.fullName")} error={server.fullName ?? (formState.errors.fullName && t("errors.validation.not_blank"))}>
        <Input autoFocus {...register("fullName", { required: true })} />
      </Field>
      <Field label={t("auth.login")} hint={t("staff.loginHint")} error={server.login ?? (formState.errors.login && t("errors.validation.login_format"))}>
        <Input autoCapitalize="none" spellCheck={false} {...register("login", { required: true, pattern: /^[a-zA-Z0-9][a-zA-Z0-9._-]{2,49}$/ })} />
      </Field>
      <Field label={t("auth.password")} hint={rules} error={server.password ?? (formState.errors.password && rules)}>
        <Input type="password" autoComplete="new-password" {...register("password", { required: true, minLength: MIN_PASSWORD })} />
      </Field>
      <Field label={t("auth.repeatPassword")} error={formState.errors.repeat && t("auth.passwordsDontMatch")}>
        <Input type="password" autoComplete="new-password" {...register("repeat", { validate: (v) => v === getValues("password") })} />
      </Field>
      <Field label={t("setup.vendorSupportPassword")} hint={t("setup.vendorSupportHint")} optional error={server.vendorSupportPassword ?? (formState.errors.vendorSupportPassword && rules)}>
        <Input type="password" autoComplete="new-password" {...register("vendorSupportPassword", { validate: (v) => !v || v.length >= MIN_PASSWORD })} />
      </Field>
      {Object.keys(server).length === 0 && <FormError error={create.error} />}
      <Button type="submit" size="lg" className="w-full" loading={create.isPending}>
        {t("setup.finish")}
      </Button>
    </form>
  );
}

