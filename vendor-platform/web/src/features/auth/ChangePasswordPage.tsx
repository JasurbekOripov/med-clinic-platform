import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import { useChangePassword, useLogout, useMe } from "@/api/hooks";
import { FormError } from "@/components/Common";
import { Field } from "@/components/Field";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { fieldErrors } from "@/lib/errors";

/** Mirrors the server rule (PasswordPolicy.MIN_LENGTH); the server stays authoritative. */
const PASSWORD_MIN_LENGTH = 8;

interface Values {
  currentPassword: string;
  newPassword: string;
  repeat: string;
}

export function ChangePasswordPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { data: me } = useMe();
  const change = useChangePassword();
  const logout = useLogout();
  const { register, handleSubmit, formState, getValues } = useForm<Values>();
  const server = fieldErrors(t, change.error);

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <Card className="w-full max-w-md">
        <CardContent>
          <h1 className="text-xl font-semibold">{t("auth.changePassword")}</h1>
          {me?.mustChangePassword && <p className="mt-2 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900">{t("auth.mustChangeHint")}</p>}
          <form
            className="mt-5 space-y-4"
            onSubmit={handleSubmit(({ currentPassword, newPassword }) =>
              change.mutate(
                { currentPassword, newPassword },
                {
                  onSuccess: () => {
                    toast.success(t("auth.passwordChanged"));
                    navigate("/", { replace: true });
                  },
                },
              ),
            )}
          >
            <Field label={t("auth.currentPassword")} error={server.currentPassword}>
              <Input type="password" autoFocus autoComplete="current-password" {...register("currentPassword", { required: true })} />
            </Field>
            <Field label={t("auth.newPassword")} hint={t("auth.passwordRules", { min: PASSWORD_MIN_LENGTH })} error={server.newPassword ?? (formState.errors.newPassword && t("errors.validation.password_policy", { min: PASSWORD_MIN_LENGTH }))}>
              <Input type="password" autoComplete="new-password" {...register("newPassword", { required: true, minLength: PASSWORD_MIN_LENGTH })} />
            </Field>
            <Field label={t("auth.repeatPassword")} error={formState.errors.repeat && t("auth.passwordsDontMatch")}>
              <Input
                type="password"
                autoComplete="new-password"
                {...register("repeat", { validate: (value) => value === getValues("newPassword") })}
              />
            </Field>
            {!Object.keys(server).length && <FormError error={change.error} />}
            <div className="flex justify-between gap-2 pt-2">
              <Button variant="ghost" onClick={() => logout.mutate(undefined, { onSettled: () => navigate("/login", { replace: true }) })}>
                {t("auth.signOut")}
              </Button>
              <div className="flex gap-2">
                {!me?.mustChangePassword && (
                  <Button variant="secondary" onClick={() => navigate(-1)}>
                    {t("common.cancel")}
                  </Button>
                )}
                <Button type="submit" loading={change.isPending}>
                  {t("common.save")}
                </Button>
              </div>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
