import type { components } from "./schema";

type S = components["schemas"];

export type Me = S["MeResponse"];
export type Staff = S["StaffResponse"];
export type StaffRequest = S["StaffRequest"];
export type StaffWithPassword = S["StaffWithPasswordResponse"];
export type Role = S["RoleResponse"];
export type PermissionGroup = S["PermissionGroupResponse"];
export type Settings = S["SettingsResponse"];
export type SettingsRequest = S["SettingsRequest"];
export type WorkingDay = S["WorkingDay"];
export type PublicSettings = S["PublicSettingsResponse"];
export type SetupStatus = S["SetupStatusResponse"];
export type LicenseStatus = S["LicenseStatusResponse"];
export type LicenseState = LicenseStatus["state"];
export type VendorLink = S["VendorLinkResponse"];
export type ImportPreview = S["ImportPreviewResponse"];
export type ImportRow = S["ImportRow"];
export type ImportResult = S["ImportResultResponse"];
export type AuditEntry = S["AuditEntryResponse"];
export type Patient = S["PatientResponse"];
export type PatientRequest = S["PatientRequest"];
export type DuplicateCandidate = S["DuplicateResponse"];
export type Gender = Patient["gender"];
export type ServiceGroup = S["ServiceGroupResponse"];
export type ServiceItem = S["ServiceResponse"];
export type ServiceRequest = S["ServiceRequest"];
export type Doctor = S["DoctorResponse"];
export type DiscountType = S["DiscountTypeResponse"];
export type DiscountTypeRequest = S["DiscountTypeRequest"];
export type Agent = S["AgentResponse"];
export type AgentRequest = S["AgentRequest"];

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export type Permission = string;
