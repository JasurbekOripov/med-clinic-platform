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
export type Visit = S["VisitResponse"];
export type VisitItem = S["VisitItemResponse"];
export type VisitItemStatus = VisitItem["status"];
export type CreateVisitRequest = S["CreateVisitRequest"];
export type UnpaidVisit = S["UnpaidVisitResponse"];
export type Payment = S["PaymentResponse"];
export type PaymentMethod = Payment["method"];
export type PrintSettings = S["PrintSettingsResponse"];
export type TicketSlip = S["TicketSlip"];
export type Queue = S["QueueResponse"];
export type QueueRequest = S["QueueRequest"];
export type Ticket = S["TicketResponse"];
export type Conclusion = S["ConclusionResponse"];
export type ConclusionTemplate = S["TemplateResponse"];
export type LabTemplate = S["LabTemplateResponse"];
export type LabTemplateRequest = S["LabTemplateRequest"];
export type FormRow = S["FormRow"];
export type RowKind = FormRow["kind"];
export type ReferenceRange = S["ReferenceRange"];
export type PhysioState = ReferenceRange["state"];
export type LabWorkItem = S["WorkItemResponse"];
export type LabResult = S["LabResultResponse"];
export type LabResultRow = S["ResultRowResponse"];
export type LabFlag = NonNullable<LabResultRow["flag"]>;
export type RowValue = S["RowValue"];

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export type Permission = string;
