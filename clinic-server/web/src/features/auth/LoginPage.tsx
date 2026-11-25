import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { Navigate, useNavigate, useSearchParams } from "react-router";
import { FormError } from "@medclinic/web-ui/components/Common";
import { Field } from "@medclinic/web-ui/components/Field";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Card, CardContent } from "@medclinic/web-ui/components/ui/card";
import { Input } from "@medclinic/web-ui/components/ui/input";
import { useLogin, useMe } from "@/api/hooks";
import { ClinicBrand } from "@/features/layout/ClinicBrand";
import { LanguageSwitcher } from "@/features/layout/LanguageSwitcher";

/** Only same-app relative paths are accepted as a post-login target (no open redirects). */
export function safeNext(next: string | null): string {
  return next && next.startsWith("/") && !next.startsWith("//") ? next : "/";
}

export function LoginPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const { data: me } = useMe();
  const login = useLogin();
  const { register, handleSubmit, formState } = useForm<{ login: string; password: string }>();

  if (me) return <Navigate to={me.mustChangePassword ? "/change-password" : safeNext(params.get("next"))} replace />;

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-8 px-4">
      <ClinicBrand size="lg" />
      <Card className="w-full max-w-sm">
        <CardContent className="p-7">
          <h1 className="mb-5 text-xl font-semibold">{t("auth.title")}</h1>
          <form
            className="space-y-4"
            onSubmit={handleSubmit((values) =>
              login.mutate(values, {
                onSuccess: (result) => navigate(result.mustChangePassword ? "/change-password" : safeNext(params.get("next")), { replace: true }),
              }),
            )}
          >
            <Field label={t("auth.login")} error={formState.errors.login && t("errors.validation.not_blank")}>
              <Input autoFocus className="h-12 text-base" autoComplete="username" autoCapitalize="none" spellCheck={false} {...register("login", { required: true })} />
            </Field>
            <Field label={t("auth.password")} error={formState.errors.password && t("errors.validation.not_blank")}>
              <Input type="password" className="h-12 text-base" autoComplete="current-password" {...register("password", { required: true })} />
            </Field>
            <FormError error={login.error} />
            <Button type="submit" size="lg" className="w-full" loading={login.isPending}>
              {t("auth.signIn")}
            </Button>
          </form>
        </CardContent>
      </Card>
      <LanguageSwitcher />
    </div>
  );
}
