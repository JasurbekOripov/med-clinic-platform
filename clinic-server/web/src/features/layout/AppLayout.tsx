import type * as React from "react";
import { AlertTriangle, ClipboardList, Contact, Handshake, ListOrdered, Tags, Wallet, FileSpreadsheet, Home, KeyRound, LogOut, Settings, ShieldCheck, UserRoundCog, Users } from "lucide-react";
import { useTranslation } from "react-i18next";
import { NavLink, Outlet, useNavigate } from "react-router";
import { EmptyState, Spinner } from "@medclinic/web-ui/components/Common";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { formatDate } from "@medclinic/web-ui/lib/format";
import { cn } from "@medclinic/web-ui/lib/utils";
import { useLicense, useLogout } from "@/api/hooks";
import { usePermissions } from "@/features/auth/permissions";
import { RoleLabel } from "@/features/roles/RoleLabel";
import { ClinicBrand } from "./ClinicBrand";
import { IdleLock } from "./IdleLock";
import { LanguageSwitcher } from "./LanguageSwitcher";
import { StopScreen } from "./StopScreen";

/** Queue screen: reception, doctors, lab and the Admin who sets queues up (mirrors `HAS_QUEUE_READ`). */
export const QUEUE_READERS = ["QUEUE_MANAGE", "CONSULTATION_WRITE", "LAB_WORK", "SETTINGS_MANAGE"];

type NavItem = { to: string; label: string; icon: typeof Home; hint: string | null; needs: string[]; any?: string[] };

export const NAV: NavItem[] = [
  { to: "/", label: "nav.home", icon: Home, hint: null, needs: [] },
  { to: "/patients", label: "nav.patients", icon: Contact, hint: "home.patientsHint", needs: ["PATIENT_VIEW"] },
  { to: "/queue", label: "nav.queue", icon: ListOrdered, hint: "home.queueHint", needs: [], any: QUEUE_READERS },
  { to: "/cashier", label: "nav.cashier", icon: Wallet, hint: "home.cashierHint", needs: ["PAYMENT_ACCEPT"] },
  { to: "/prices", label: "nav.priceList", icon: Tags, hint: "home.priceListHint", needs: ["PRICE_LIST_MANAGE"] },
  { to: "/agents", label: "nav.agents", icon: Handshake, hint: "home.agentsHint", needs: ["AGENT_MANAGE"] },
  { to: "/staff", label: "nav.staff", icon: Users, hint: "home.staffHint", needs: ["STAFF_VIEW"] },
  { to: "/roles", label: "nav.roles", icon: UserRoundCog, hint: "home.rolesHint", needs: ["STAFF_VIEW"] },
  { to: "/settings", label: "nav.settings", icon: Settings, hint: "home.settingsHint", needs: ["SETTINGS_MANAGE"] },
  { to: "/import", label: "nav.import", icon: FileSpreadsheet, hint: "home.importHint", needs: ["DATA_IMPORT", "STAFF_MANAGE"] },
  { to: "/audit", label: "nav.audit", icon: ClipboardList, hint: "home.auditHint", needs: ["AUDIT_VIEW"] },
  { to: "/license", label: "nav.license", icon: ShieldCheck, hint: "home.licenseHint", needs: ["LICENSE_MANAGE"] },
];

export function AppLayout() {
  const { t } = useTranslation();
  const { me, can } = usePermissions();
  const license = useLicense();
  const logout = useLogout();
  const navigate = useNavigate();
  const items = NAV.filter((n) => canOpen(n, can));
  const signOut = () => logout.mutate(undefined, { onSettled: () => navigate("/login", { replace: true }) });

  if (!me) return null;
  const stopped = license.data && !license.data.usable;

  return (
    <div className="flex min-h-screen">
      <aside className="sticky top-0 hidden h-screen w-64 shrink-0 flex-col border-r border-border bg-surface/70 md:flex">
        <div className="px-5 py-5">
          <ClinicBrand />
        </div>
        <nav className="flex flex-1 flex-col gap-1 px-3">
          {items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === "/"}
              className={({ isActive }) =>
                cn(
                  "flex items-center gap-3 rounded-lg px-3.5 py-3 text-[15px] font-medium transition-colors",
                  isActive ? "bg-primary/12 text-primary" : "text-foreground/85 hover:bg-muted",
                )
              }
            >
              <item.icon className="size-[18px]" aria-hidden />
              {t(item.label)}
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 border-b border-border bg-background/90 backdrop-blur">
          <div className="flex h-16 items-center gap-3 px-4 md:px-8">
            <ClinicBrand className="md:hidden" />
            <div className="ml-auto flex items-center gap-2 sm:gap-3">
              <div className="hidden text-right text-sm leading-tight sm:block">
                <div className="font-medium">{me.fullName}</div>
                <div className="text-xs text-muted-foreground">
                  <RoleLabel template={me.roleTemplate} name={me.roleName} />
                </div>
              </div>
              <LanguageSwitcher />
              <Button variant="ghost" size="icon" title={t("auth.changePassword")} aria-label={t("auth.changePassword")} onClick={() => navigate("/change-password")}>
                <KeyRound />
              </Button>
              <Button variant="secondary" size="sm" onClick={signOut} title={t("auth.switchUser")}>
                <LogOut /> <span className="hidden lg:inline">{t("auth.switchUser")}</span>
              </Button>
            </div>
          </div>
          <nav className="flex gap-1 overflow-x-auto px-3 pb-2 md:hidden">
            {items.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.to === "/"} className="whitespace-nowrap rounded-md px-3 py-1.5 text-sm aria-[current=page]:bg-primary/12 aria-[current=page]:text-primary">
                {t(item.label)}
              </NavLink>
            ))}
          </nav>
          {license.data?.state === "GRACE" && (
            <Banner>{t("license.graceBanner", { date: formatDate(license.data.graceEndsAt?.slice(0, 10)) })}</Banner>
          )}
          {license.data?.clockWarning && <Banner>{t("license.clockBanner")}</Banner>}
        </header>

        <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 md:px-10">
          {license.isPending ? <Spinner /> : stopped ? <StopScreen license={license.data} /> : <Outlet />}
        </main>
      </div>
      <IdleLock me={me} />
    </div>
  );
}

function Banner({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex items-center gap-2 border-t border-amber-200 bg-amber-50 px-4 py-2 text-sm text-amber-900 md:px-8" role="status">
      <AlertTriangle className="size-4 shrink-0" aria-hidden />
      {children}
    </div>
  );
}

export function NotFound() {
  const { t } = useTranslation();
  return <EmptyState text={t("errors.common.not_found")} />;
}

/** Whether the menu item is shown: all of `needs` and, when set, one of `any`. */
export function canOpen(item: NavItem, can: (...permissions: string[]) => boolean): boolean {
  return can(...item.needs) && (!item.any || item.any.some((p) => can(p)));
}
