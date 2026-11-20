import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { Navigate, useNavigate, useSearchParams } from "react-router";
import { useLogin, useMe } from "@/api/hooks";
import { FormError } from "@/components/Common";
import { Field } from "@/components/Field";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
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
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 px-4">
      <div className="text-center">
        <div className="text-sm font-semibold uppercase tracking-widest text-primary">{t("app.name")}</div>
        <h1 className="mt-1 text-2xl font-semibold">{t("auth.title")}</h1>
      </div>
      <Card className="w-full max-w-sm">
        <CardContent>
          <form
            className="space-y-4"
            onSubmit={handleSubmit((values) =>
              login.mutate(values, {
                onSuccess: (result) => navigate(result.mustChangePassword ? "/change-password" : safeNext(params.get("next")), { replace: true }),
              }),
            )}
          >
            <Field label={t("auth.login")} error={formState.errors.login && t("errors.validation.not_blank")}>
              <Input autoFocus autoComplete="username" autoCapitalize="none" spellCheck={false} {...register("login", { required: true })} />
            </Field>
            <Field label={t("auth.password")} error={formState.errors.password && t("errors.validation.not_blank")}>
              <Input type="password" autoComplete="current-password" {...register("password", { required: true })} />
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
