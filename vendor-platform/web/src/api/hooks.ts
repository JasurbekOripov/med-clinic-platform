import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, http, query } from "./client";
import type {
  Alert,
  AuditEntry,
  Branch,
  BranchRequest,
  Clinic,
  ClinicRequest,
  Device,
  License,
  LicenseCatalog,
  LicenseTermsRequest,
  Me,
  Page,
  PairingCode,
  ServerOverview,
  ServerStatus,
  Staff,
  StaffWithPassword,
  TenantStatus,
  VendorRole,
} from "./types";

export const keys = {
  me: ["me"] as const,
  clinics: (params: object) => ["clinics", params] as const,
  clinic: (id: string) => ["clinic", id] as const,
  branches: (clinicId: string) => ["clinic", clinicId, "branches"] as const,
  licenses: (clinicId: string) => ["clinic", clinicId, "licenses"] as const,
  pairingCodes: (branchId: string) => ["branch", branchId, "pairing-codes"] as const,
  devices: (branchId: string) => ["branch", branchId, "devices"] as const,
  servers: (params: object) => ["monitoring", "servers", params] as const,
  alerts: (params: object) => ["monitoring", "alerts", params] as const,
  staff: (params: object) => ["staff", params] as const,
  audit: (params: object) => ["audit", params] as const,
  catalog: ["license-catalog"] as const,
};

// ---------- auth ----------

/** `null` means "not logged in" (401), which is a normal state, not an error. */
export function useMe() {
  return useQuery({
    queryKey: keys.me,
    queryFn: async (): Promise<Me | null> => {
      try {
        return await http.get<Me>("/api/v1/auth/me");
      } catch (e) {
        if (e instanceof ApiError && e.isUnauthorized) return null;
        throw e;
      }
    },
    staleTime: 60_000,
    retry: false,
  });
}

export function useLogin() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { login: string; password: string }) => http.post<Me>("/api/v1/auth/login", body),
    onSuccess: (me) => {
      qc.clear();
      qc.setQueryData(keys.me, me);
    },
  });
}

export function useLogout() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => http.post<void>("/api/v1/auth/logout"),
    onSettled: () => {
      qc.clear();
      qc.setQueryData(keys.me, null);
    },
  });
}

export function useChangePassword() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { currentPassword: string; newPassword: string }) => http.post<Me>("/api/v1/auth/password", body),
    onSuccess: (me) => qc.setQueryData(keys.me, me),
  });
}

// ---------- clinics & branches ----------

export interface ClinicSearch {
  q?: string;
  status?: TenantStatus | "";
  page?: number;
}

export function useClinics(params: ClinicSearch) {
  return useQuery({
    queryKey: keys.clinics(params),
    queryFn: () => http.get<Page<Clinic>>(`/api/v1/clinics${query({ ...params, size: 25 })}`),
    placeholderData: keepPreviousData,
  });
}

export function useClinic(id: string) {
  return useQuery({ queryKey: keys.clinic(id), queryFn: () => http.get<Clinic>(`/api/v1/clinics/${id}`) });
}

function useInvalidate() {
  const qc = useQueryClient();
  return (...prefixes: readonly unknown[][]) => Promise.all(prefixes.map((queryKey) => qc.invalidateQueries({ queryKey })));
}

export function useSaveClinic() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, body }: { id?: string; body: ClinicRequest }) =>
      id ? http.put<Clinic>(`/api/v1/clinics/${id}`, body) : http.post<Clinic>("/api/v1/clinics", body),
    onSuccess: (clinic) => invalidate(["clinics"], ["clinic", clinic.id], ["monitoring"]),
  });
}

export function useClinicStatus() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, status, version }: { id: string; status: TenantStatus; version: number }) =>
      http.post<Clinic>(`/api/v1/clinics/${id}/status`, { status, version }),
    onSuccess: (clinic) => invalidate(["clinics"], ["clinic", clinic.id], ["monitoring"]),
  });
}

export function useDeleteClinic() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, version }: { id: string; version: number }) => http.delete(`/api/v1/clinics/${id}${query({ version })}`),
    onSuccess: () => invalidate(["clinics"], ["monitoring"]),
  });
}

export function useBranches(clinicId: string) {
  return useQuery({ queryKey: keys.branches(clinicId), queryFn: () => http.get<Branch[]>(`/api/v1/clinics/${clinicId}/branches`) });
}

export function useSaveBranch(clinicId: string) {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, body }: { id?: string; body: BranchRequest }) =>
      id ? http.put<Branch>(`/api/v1/branches/${id}`, body) : http.post<Branch>(`/api/v1/clinics/${clinicId}/branches`, body),
    onSuccess: () => invalidate(["clinic", clinicId], ["clinics"], ["monitoring"]),
  });
}

export function useBranchStatus(clinicId: string) {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, status, version }: { id: string; status: TenantStatus; version: number }) =>
      http.post<Branch>(`/api/v1/branches/${id}/status`, { status, version }),
    onSuccess: () => invalidate(["clinic", clinicId], ["monitoring"]),
  });
}

export function useDeleteBranch(clinicId: string) {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, version }: { id: string; version: number }) => http.delete(`/api/v1/branches/${id}${query({ version })}`),
    onSuccess: () => invalidate(["clinic", clinicId], ["clinics"], ["monitoring"]),
  });
}

// ---------- pairing & devices ----------

export function usePairingCodes(branchId: string, enabled = true) {
  return useQuery({
    queryKey: keys.pairingCodes(branchId),
    queryFn: () => http.get<PairingCode[]>(`/api/v1/branches/${branchId}/pairing-codes`),
    enabled,
  });
}

export function useCreatePairingCode() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (branchId: string) => http.post<PairingCode>(`/api/v1/branches/${branchId}/pairing-codes`),
    onSuccess: (code) => invalidate(["branch", code.branchId]),
  });
}

export function useCancelPairingCode(branchId: string) {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (id: string) => http.post<void>(`/api/v1/pairing-codes/${id}/cancel`),
    onSuccess: () => invalidate(["branch", branchId]),
  });
}

export function useDevices(branchId: string, enabled = true) {
  return useQuery({
    queryKey: keys.devices(branchId),
    queryFn: () => http.get<Device[]>(`/api/v1/branches/${branchId}/devices`),
    enabled,
  });
}

export function useRevokeDevice(branchId: string) {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, version, reason }: { id: string; version: number; reason: string }) =>
      http.post<Device>(`/api/v1/devices/${id}/revoke`, { version, reason }),
    onSuccess: () => invalidate(["branch", branchId], ["monitoring"]),
  });
}

// ---------- licenses ----------

export function useLicenses(clinicId: string, enabled = true) {
  return useQuery({
    queryKey: keys.licenses(clinicId),
    queryFn: () => http.get<License[]>(`/api/v1/clinics/${clinicId}/licenses`),
    enabled,
  });
}

export function useLicenseCatalog() {
  return useQuery({ queryKey: keys.catalog, queryFn: () => http.get<LicenseCatalog>("/api/v1/license-catalog"), staleTime: Infinity });
}

export function useIssueLicense(clinicId: string) {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ renewId, body }: { renewId?: string; body: LicenseTermsRequest }) =>
      renewId ? http.post<License>(`/api/v1/licenses/${renewId}/renew`, body) : http.post<License>(`/api/v1/clinics/${clinicId}/licenses`, body),
    onSuccess: () => invalidate(["clinic", clinicId]),
  });
}

export function useRevokeLicense(clinicId: string) {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, version, reason }: { id: string; version: number; reason: string }) =>
      http.post<License>(`/api/v1/licenses/${id}/revoke`, { version, reason }),
    onSuccess: () => invalidate(["clinic", clinicId]),
  });
}

// ---------- monitoring ----------

export function useServers(params: { status?: ServerStatus | ""; q?: string }) {
  return useQuery({
    queryKey: keys.servers(params),
    queryFn: () => http.get<ServerOverview>(`/api/v1/monitoring/servers${query(params)}`),
    refetchInterval: 30_000,
    placeholderData: keepPreviousData,
  });
}

export function useAlerts(params: { open: boolean; page: number }) {
  return useQuery({
    queryKey: keys.alerts(params),
    queryFn: () => http.get<Page<Alert>>(`/api/v1/monitoring/alerts${query({ ...params, size: 50 })}`),
    refetchInterval: 30_000,
    placeholderData: keepPreviousData,
  });
}

// ---------- staff ----------

export function useStaff(params: { q?: string; includeInactive: boolean; page: number }) {
  return useQuery({
    queryKey: keys.staff(params),
    queryFn: () => http.get<Page<Staff>>(`/api/v1/staff${query({ ...params, size: 50 })}`),
    placeholderData: keepPreviousData,
  });
}

export function useCreateStaff() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (body: { login: string; fullName: string; role: VendorRole }) => http.post<StaffWithPassword>("/api/v1/staff", body),
    onSuccess: () => invalidate(["staff"]),
  });
}

export function useUpdateStaff() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: { fullName: string; role: VendorRole; version: number } }) =>
      http.put<Staff>(`/api/v1/staff/${id}`, body),
    onSuccess: () => invalidate(["staff"]),
  });
}

export function useStaffAction() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, action, version }: { id: string; action: "activate" | "deactivate" | "reset-password"; version: number }) =>
      http.post<Staff | StaffWithPassword>(`/api/v1/staff/${id}/${action}`, { version }),
    onSuccess: () => invalidate(["staff"]),
  });
}

// ---------- audit ----------

export interface AuditSearch {
  action?: string;
  entityType?: string;
  entityId?: string;
  clinicId?: string;
  page: number;
}

export function useAudit(params: AuditSearch, enabled = true) {
  return useQuery({
    queryKey: keys.audit(params),
    queryFn: () => http.get<Page<AuditEntry>>(`/api/v1/audit${query({ ...params, size: 50 })}`),
    placeholderData: keepPreviousData,
    enabled,
  });
}
