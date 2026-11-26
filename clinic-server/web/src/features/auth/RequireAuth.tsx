import type * as React from "react";
import { useTranslation } from "react-i18next";
import { Navigate, Outlet, useLocation } from "react-router";
import { EmptyState, ErrorState, Spinner } from "@medclinic/web-ui/components/Common";
import { useMe, useSetupStatus } from "@/api/hooks";
import { usePermissions } from "./permissions";

/** A fresh server (not paired or without an Admin) always shows the first-run setup. */
export function RequireSetup() {
  const { data, isPending, error, refetch } = useSetupStatus();
  if (isPending) return <Spinner className="h-screen" />;
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />;
  if (!data.completed) return <Navigate to="/setup" replace />;
  return <Outlet />;
}

export function RequireAuth() {
  const { data: me, isPending, error, refetch } = useMe();
  const location = useLocation();
  if (isPending) return <Spinner className="h-screen" />;
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />;
  if (!me) return <Navigate to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`} replace />;
  if (me.mustChangePassword && location.pathname !== "/change-password") return <Navigate to="/change-password" replace />;
  return <Outlet />;
}

/** Shows [children] when the user has every permission in [all] and, if given, at least one in [any]. */
export function RequirePermission({ all = [], any, children }: { all?: string[]; any?: string[]; children: React.ReactNode }) {
  const { can } = usePermissions();
  const { t } = useTranslation();
  const allowed = can(...all) && (!any || any.some((p) => can(p)));
  return allowed ? <>{children}</> : <EmptyState text={t("errors.auth.forbidden")} />;
}
