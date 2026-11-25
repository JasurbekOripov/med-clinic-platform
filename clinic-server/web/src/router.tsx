import { createBrowserRouter } from "react-router";
import { AuditPage } from "./features/audit/AuditPage";
import { ChangePasswordPage } from "./features/auth/ChangePasswordPage";
import { LoginPage } from "./features/auth/LoginPage";
import { RequireAuth, RequirePermission, RequireSetup } from "./features/auth/RequireAuth";
import { HomePage } from "./features/home/HomePage";
import { ImportPage } from "./features/import/ImportPage";
import { AppLayout, NotFound } from "./features/layout/AppLayout";
import { LicensePage } from "./features/license/LicensePage";
import { RolesPage } from "./features/roles/RolesPage";
import { SettingsPage } from "./features/settings/SettingsPage";
import { SetupPage } from "./features/setup/SetupPage";
import { StaffPage } from "./features/staff/StaffPage";

export const router = createBrowserRouter([
  { path: "/setup", element: <SetupPage /> },
  {
    element: <RequireSetup />,
    children: [
      { path: "/login", element: <LoginPage /> },
      {
        element: <RequireAuth />,
        children: [
          { path: "/change-password", element: <ChangePasswordPage /> },
          {
            element: <AppLayout />,
            children: [
              { index: true, element: <HomePage /> },
              { path: "staff", element: <RequirePermission all={["STAFF_VIEW"]}><StaffPage /></RequirePermission> },
              { path: "roles", element: <RequirePermission all={["STAFF_VIEW"]}><RolesPage /></RequirePermission> },
              { path: "settings", element: <RequirePermission all={["SETTINGS_MANAGE"]}><SettingsPage /></RequirePermission> },
              { path: "import", element: <RequirePermission all={["DATA_IMPORT", "STAFF_MANAGE"]}><ImportPage /></RequirePermission> },
              { path: "audit", element: <RequirePermission all={["AUDIT_VIEW"]}><AuditPage /></RequirePermission> },
              { path: "license", element: <RequirePermission all={["LICENSE_MANAGE"]}><LicensePage /></RequirePermission> },
              { path: "*", element: <NotFound /> },
            ],
          },
        ],
      },
    ],
  },
]);
