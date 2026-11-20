import { createBrowserRouter } from "react-router";
import { AlertsPage } from "./features/alerts/AlertsPage";
import { AuditPage } from "./features/audit/AuditPage";
import { ChangePasswordPage } from "./features/auth/ChangePasswordPage";
import { LoginPage } from "./features/auth/LoginPage";
import { RequireAuth, RequirePermission } from "./features/auth/RequireAuth";
import { ClinicDetailPage } from "./features/clinics/ClinicDetailPage";
import { ClinicsPage } from "./features/clinics/ClinicsPage";
import { DashboardPage } from "./features/dashboard/DashboardPage";
import { AppLayout, NotFound } from "./features/layout/AppLayout";
import { StaffPage } from "./features/staff/StaffPage";

export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      { path: "/change-password", element: <ChangePasswordPage /> },
      {
        element: <AppLayout />,
        children: [
          { index: true, element: <RequirePermission permission="MONITORING_VIEW"><DashboardPage /></RequirePermission> },
          { path: "clinics", element: <RequirePermission permission="CLINIC_VIEW"><ClinicsPage /></RequirePermission> },
          { path: "clinics/:id", element: <RequirePermission permission="CLINIC_VIEW"><ClinicDetailPage /></RequirePermission> },
          { path: "alerts", element: <RequirePermission permission="MONITORING_VIEW"><AlertsPage /></RequirePermission> },
          { path: "staff", element: <RequirePermission permission="STAFF_MANAGE"><StaffPage /></RequirePermission> },
          { path: "audit", element: <RequirePermission permission="AUDIT_VIEW"><AuditPage /></RequirePermission> },
          { path: "*", element: <NotFound /> },
        ],
      },
    ],
  },
]);
