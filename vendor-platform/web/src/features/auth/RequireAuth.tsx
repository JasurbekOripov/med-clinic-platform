import type * as React from "react";
import { useTranslation } from "react-i18next";
import { Navigate, Outlet, useLocation } from "react-router";
import { useMe } from "@/api/hooks";
import type { Permission } from "@/api/types";
import { EmptyState, ErrorState, Spinner } from "@medclinic/web-ui/components/Common";
import { usePermissions } from "./permissions";

export function RequireAuth() {
  const { data: me, isPending, error, refetch } = useMe();
  const location = useLocation();
  if (isPending) return <Spinner className="h-screen" />;
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />;
  if (!me) {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?next=${next}`} replace />;
  }
  if (me.mustChangePassword && location.pathname !== "/change-password") return <Navigate to="/change-password" replace />;
  return <Outlet />;
}

export function RequirePermission({ permission, children }: { permission: Permission; children: React.ReactNode }) {
  const { can, me } = usePermissions();
  const { t } = useTranslation();
  if (!can(permission)) {
    // Support staff landing on "/" without MONITORING_VIEW would see nothing useful: send them to clinics.
    if (permission === "MONITORING_VIEW" && me?.permissions.includes("CLINIC_VIEW")) return <Navigate to="/clinics" replace />;
    return <EmptyState text={t("errors.auth.forbidden")} />;
  }
  return <>{children}</>;
}
