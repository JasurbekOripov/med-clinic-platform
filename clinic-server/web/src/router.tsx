import { createBrowserRouter } from "react-router";
import { AuditPage } from "./features/audit/AuditPage";
import { ChangePasswordPage } from "./features/auth/ChangePasswordPage";
import { LoginPage } from "./features/auth/LoginPage";
import { RequireAuth, RequirePermission, RequireSetup } from "./features/auth/RequireAuth";
import { HomePage } from "./features/home/HomePage";
import { ImportPage } from "./features/import/ImportPage";
import { AppLayout, NotFound } from "./features/layout/AppLayout";
import { PriceListPage } from "./features/catalog/PriceListPage";
import { AgentsPage } from "./features/agents/AgentsPage";
import { CashierPage } from "./features/cashier/CashierPage";
import { QUEUE_READERS } from "./features/layout/AppLayout";
import { QueuePage } from "./features/queues/QueuePage";
import { LabPage } from "./features/lab/LabPage";
import { LicensePage } from "./features/license/LicensePage";
import { PatientDetailPage } from "./features/patients/PatientDetailPage";
import { PatientsPage } from "./features/patients/PatientsPage";
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
              { path: "patients", element: <RequirePermission all={["PATIENT_VIEW"]}><PatientsPage /></RequirePermission> },
              { path: "patients/:id", element: <RequirePermission all={["PATIENT_VIEW"]}><PatientDetailPage /></RequirePermission> },
              { path: "queue", element: <RequirePermission any={QUEUE_READERS}><QueuePage /></RequirePermission> },
              { path: "lab", element: <RequirePermission any={["LAB_WORK", "LAB_TEMPLATES_MANAGE"]}><LabPage /></RequirePermission> },
              { path: "cashier", element: <RequirePermission all={["PAYMENT_ACCEPT"]}><CashierPage /></RequirePermission> },
              { path: "prices", element: <RequirePermission all={["PRICE_LIST_MANAGE"]}><PriceListPage /></RequirePermission> },
              { path: "agents", element: <RequirePermission all={["AGENT_MANAGE"]}><AgentsPage /></RequirePermission> },
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
