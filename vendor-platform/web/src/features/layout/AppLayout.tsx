import { Activity, Bell, Building2, KeyRound, LogOut, ScrollText, Users } from "lucide-react";
import { useTranslation } from "react-i18next";
import { NavLink, Outlet, useNavigate } from "react-router";
import { useLogout } from "@/api/hooks";
import type { Permission } from "@/api/types";
import { EmptyState } from "@/components/Common";
import { Button } from "@/components/ui/button";
import { usePermissions } from "@/features/auth/permissions";
import { cn } from "@/lib/utils";
import { LanguageSwitcher } from "./LanguageSwitcher";

const NAV: { to: string; label: string; icon: typeof Activity; permission: Permission }[] = [
  { to: "/", label: "nav.dashboard", icon: Activity, permission: "MONITORING_VIEW" },
  { to: "/clinics", label: "nav.clinics", icon: Building2, permission: "CLINIC_VIEW" },
  { to: "/alerts", label: "nav.alerts", icon: Bell, permission: "MONITORING_VIEW" },
  { to: "/staff", label: "nav.staff", icon: Users, permission: "STAFF_MANAGE" },
  { to: "/audit", label: "nav.audit", icon: ScrollText, permission: "AUDIT_VIEW" },
];

export function AppLayout() {
  const { t } = useTranslation();
  const { can } = usePermissions();
  const items = NAV.filter((n) => can(n.permission));

  return (
    <div className="flex min-h-screen">
      <aside className="sticky top-0 hidden h-screen w-60 shrink-0 flex-col border-r border-border bg-surface md:flex">
        <div className="flex h-16 flex-col justify-center px-5">
          <div className="text-xs font-semibold uppercase tracking-widest text-primary">{t("app.name")}</div>
          <div className="text-sm text-muted-foreground">{t("app.console")}</div>
        </div>
        <nav className="flex flex-1 flex-col gap-1 px-3 pt-2">
          {items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === "/"}
              className={({ isActive }) =>
                cn(
                  "flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors",
                  isActive ? "bg-primary/10 text-primary" : "text-foreground hover:bg-muted",
                )
              }
            >
              <item.icon className="size-4" aria-hidden />
              {t(item.label)}
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 border-b border-border bg-surface/95 backdrop-blur">
          <div className="flex h-16 items-center gap-3 px-4 md:px-8">
            <div className="text-xs font-semibold uppercase tracking-widest text-primary md:hidden">{t("app.name")}</div>
            <div className="ml-auto">
              <UserMenu />
            </div>
          </div>
          <nav className="flex gap-1 overflow-x-auto px-3 pb-2 md:hidden">
            {items.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === "/"}
                className="whitespace-nowrap rounded-md px-3 py-1.5 text-sm aria-[current=page]:bg-primary/10 aria-[current=page]:text-primary"
              >
                {t(item.label)}
              </NavLink>
            ))}
          </nav>
        </header>
        <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 md:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

/** Top-right corner: who is signed in, language, change password, sign out. */
function UserMenu() {
  const { t } = useTranslation();
  const { me } = usePermissions();
  const logout = useLogout();
  const navigate = useNavigate();

  return (
    <div className="flex items-center gap-2 sm:gap-4">
      <div className="hidden text-right text-sm leading-tight sm:block">
        <div className="font-medium">{me?.fullName}</div>
        <div className="text-xs text-muted-foreground">{me && t(`role.${me.role}`)}</div>
      </div>
      <LanguageSwitcher />
      <Button variant="ghost" size="icon" title={t("auth.changePassword")} aria-label={t("auth.changePassword")} onClick={() => navigate("/change-password")}>
        <KeyRound />
      </Button>
      <Button
        variant="secondary"
        size="sm"
        loading={logout.isPending}
        onClick={() => logout.mutate(undefined, { onSettled: () => navigate("/login", { replace: true }) })}
      >
        <LogOut /> <span className="hidden sm:inline">{t("auth.signOut")}</span>
      </Button>
    </div>
  );
}

export function NotFound() {
  const { t } = useTranslation();
  return <EmptyState text={t("errors.common.not_found")} />;
}
