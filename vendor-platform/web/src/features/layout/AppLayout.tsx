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
  const { me, can } = usePermissions();
  const logout = useLogout();
  const navigate = useNavigate();

  return (
    <div className="flex min-h-screen">
      <aside className="sticky top-0 hidden h-screen w-60 shrink-0 flex-col border-r border-border bg-surface md:flex">
        <div className="px-5 py-5">
          <div className="text-xs font-semibold uppercase tracking-widest text-primary">{t("app.name")}</div>
          <div className="text-sm text-muted-foreground">{t("app.console")}</div>
        </div>
        <nav className="flex flex-1 flex-col gap-1 px-3">
          {NAV.filter((n) => can(n.permission)).map((item) => (
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
        <div className="space-y-3 border-t border-border p-4">
          <div className="text-sm">
            <div className="font-medium">{me?.fullName}</div>
            <div className="text-muted-foreground">{me && t(`role.${me.role}`)}</div>
          </div>
          <LanguageSwitcher />
          <div className="flex flex-col gap-1">
            <Button variant="ghost" size="sm" className="justify-start" onClick={() => navigate("/change-password")}>
              <KeyRound /> {t("auth.changePassword")}
            </Button>
            <Button
              variant="ghost"
              size="sm"
              className="justify-start"
              onClick={() => logout.mutate(undefined, { onSettled: () => navigate("/login", { replace: true }) })}
            >
              <LogOut /> {t("auth.signOut")}
            </Button>
          </div>
        </div>
      </aside>
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center gap-2 overflow-x-auto border-b border-border bg-surface px-4 py-2 md:hidden">
          {NAV.filter((n) => can(n.permission)).map((item) => (
            <NavLink key={item.to} to={item.to} end={item.to === "/"} className="rounded-md px-2 py-1 text-sm whitespace-nowrap aria-[current=page]:bg-primary/10 aria-[current=page]:text-primary">
              {t(item.label)}
            </NavLink>
          ))}
        </header>
        <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 md:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

export function NotFound() {
  const { t } = useTranslation();
  return <EmptyState text={t("errors.common.not_found")} />;
}
