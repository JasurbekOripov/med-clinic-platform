import { Lock } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { FormError } from "@medclinic/web-ui/components/Common";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Input } from "@medclinic/web-ui/components/ui/input";
import { useLogin, useLogout, usePublicSettings } from "@/api/hooks";
import type { Me } from "@/api/types";

const ACTIVITY_EVENTS = ["mousemove", "mousedown", "keydown", "touchstart", "wheel"] as const;

/**
 * Shared PCs: after the clinic's idle minutes the screen locks and hides everything. The same person unlocks with
 * their password (a fresh sign-in); anyone else uses "another staff member", which signs out first.
 */
export function IdleLock({ me }: { me: Me }) {
  const { data: settings } = usePublicSettings();
  const minutes = settings?.idleLockMinutes ?? 10;
  const [locked, setLocked] = React.useState(false);

  React.useEffect(() => {
    if (locked) return;
    let timer = window.setTimeout(() => setLocked(true), minutes * 60_000);
    const reset = () => {
      window.clearTimeout(timer);
      timer = window.setTimeout(() => setLocked(true), minutes * 60_000);
    };
    ACTIVITY_EVENTS.forEach((e) => window.addEventListener(e, reset, { passive: true }));
    return () => {
      window.clearTimeout(timer);
      ACTIVITY_EVENTS.forEach((e) => window.removeEventListener(e, reset));
    };
  }, [locked, minutes]);

  return locked ? <LockScreen me={me} onUnlocked={() => setLocked(false)} /> : null;
}

function LockScreen({ me, onUnlocked }: { me: Me; onUnlocked: () => void }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const login = useLogin();
  const logout = useLogout();
  const [password, setPassword] = React.useState("");

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-background/95 p-4 backdrop-blur-md" role="dialog" aria-modal="true">
      <form
        className="w-full max-w-sm rounded-xl border border-border bg-surface p-8 text-center shadow-xl"
        onSubmit={(e) => {
          e.preventDefault();
          login.mutate({ login: me.login, password }, { onSuccess: () => { setPassword(""); onUnlocked(); } });
        }}
      >
        <div className="mx-auto mb-4 flex size-14 items-center justify-center rounded-full bg-primary/12 text-primary">
          <Lock className="size-7" aria-hidden />
        </div>
        <h2 className="text-xl font-semibold">{t("lock.title")}</h2>
        <p className="mt-1 text-sm text-muted-foreground">{me.fullName}</p>
        <p className="mt-4 text-sm text-muted-foreground">{t("lock.hint")}</p>
        <Input type="password" autoFocus className="mt-3 h-12 text-base" aria-label={t("auth.password")} value={password} onChange={(e) => setPassword(e.target.value)} />
        <FormError error={login.error} />
        <Button type="submit" size="lg" className="mt-4 w-full" loading={login.isPending} disabled={!password}>
          {t("lock.unlock")}
        </Button>
        <Button variant="link" className="mt-3" onClick={() => logout.mutate(undefined, { onSettled: () => navigate("/login", { replace: true }) })}>
          {t("lock.notYou")}
        </Button>
      </form>
    </div>
  );
}
