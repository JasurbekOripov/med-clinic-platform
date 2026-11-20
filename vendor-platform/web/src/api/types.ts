import type { components } from "./schema";

type S = components["schemas"];

export type Me = S["MeResponse"];
export type Staff = S["StaffResponse"];
export type StaffWithPassword = S["StaffWithPasswordResponse"];
export type Clinic = S["ClinicResponse"];
export type ClinicRequest = S["ClinicRequest"];
export type Branch = S["BranchResponse"];
export type BranchRequest = S["BranchRequest"];
export type License = S["LicenseResponse"];
export type LicenseTermsRequest = S["LicenseTermsRequest"];
export type LicenseCatalog = S["LicenseCatalogResponse"];
export type PairingCode = S["PairingCodeResponse"];
export type Device = S["DeviceResponse"];
export type ServerOverview = S["ServerOverviewResponse"];
export type ServerRow = S["ServerRowResponse"];
export type Alert = S["AlertResponse"];
export type AuditEntry = S["AuditEntryResponse"];

export type TenantStatus = Clinic["status"];
export type Region = Clinic["region"];
export type ServerStatus = ServerRow["status"];
export type LicenseStatus = License["status"];
export type VendorRole = Staff["role"];
export type AlertType = Alert["type"];

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export const REGIONS: Region[] = [
  "TASHKENT_CITY",
  "TASHKENT_REGION",
  "ANDIJAN",
  "BUKHARA",
  "FERGANA",
  "JIZZAKH",
  "KARAKALPAKSTAN",
  "KASHKADARYA",
  "KHOREZM",
  "NAMANGAN",
  "NAVOI",
  "SAMARKAND",
  "SURKHANDARYA",
  "SYRDARYA",
];

export type Permission =
  | "CLINIC_VIEW"
  | "CLINIC_EDIT"
  | "CLINIC_STATUS"
  | "LICENSE_VIEW"
  | "LICENSE_MANAGE"
  | "DEVICE_PAIR"
  | "DEVICE_REVOKE"
  | "MONITORING_VIEW"
  | "STAFF_MANAGE"
  | "AUDIT_VIEW";
