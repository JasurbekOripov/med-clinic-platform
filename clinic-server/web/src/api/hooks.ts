import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, http, query, readCookie } from "@medclinic/web-ui/api/client";
import type {
  AuditEntry,
  ImportPreview,
  ImportResult,
  LicenseStatus,
  Me,
  Page,
  PermissionGroup,
  PublicSettings,
  Role,
  Settings,
  SettingsRequest,
  SetupStatus,
  Staff,
  StaffRequest,
  StaffWithPassword,
  VendorLink,
} from "./types";

export const keys = {
  me: ["me"] as const,
  setup: ["setup"] as const,
  license: ["license"] as const,
  publicSettings: ["settings", "public"] as const,
  settings: ["settings", "full"] as const,
  staff: (params: object) => ["staff", params] as const,
  roles: ["roles"] as const,
  permissions: ["permissions"] as const,
  vendorLink: ["vendor-link"] as const,
  audit: (params: object) => ["audit", params] as const,
};

/** Multipart upload with the CSRF header (file inputs). */
export async function upload<T>(method: "POST" | "PUT", path: string, file: File): Promise<T> {
  if (!readCookie("XSRF-TOKEN")) await fetch("/api/v1/auth/csrf", { credentials: "same-origin" });
  const form = new FormData();
  form.append("file", file);
  const response = await fetch(path, {
    method,
    body: form,
    credentials: "same-origin",
    headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") ?? "", Accept: "application/json" },
  });
  if (!response.ok) {
    const body = (await response.json().catch(() => ({}))) as { code?: string; params?: Record<string, unknown>; fieldErrors?: [] };
    throw new ApiError(response.status, body.code ?? `http.${response.status}`, body.params ?? {}, body.fieldErrors ?? []);
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

function useInvalidate() {
  const qc = useQueryClient();
  return (...prefixes: readonly unknown[][]) => Promise.all(prefixes.map((queryKey) => qc.invalidateQueries({ queryKey })));
}

// ---------- setup, license, public settings (work before login) ----------

export function useSetupStatus() {
  return useQuery({ queryKey: keys.setup, queryFn: () => http.get<SetupStatus>("/api/v1/setup/status"), staleTime: 5_000 });
}

export function usePair() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (body: { vendorUrl: string; code: string }) => http.post<SetupStatus>("/api/v1/setup/pair", body),
    onSuccess: () => invalidate(["setup"], ["license"], ["settings"]),
  });
}

export function useCreateFirstAdmin() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (body: { fullName: string; login: string; password: string; vendorSupportPassword: string | null }) =>
      http.post<SetupStatus>("/api/v1/setup/admin", body),
    onSuccess: () => invalidate(["setup"]),
  });
}

/** Polled every minute so a renewal (or an expiry) shows up without reloading the page. */
export function useLicense() {
  return useQuery({ queryKey: keys.license, queryFn: () => http.get<LicenseStatus>("/api/v1/license/status"), refetchInterval: 60_000 });
}

export function usePublicSettings() {
  return useQuery({ queryKey: keys.publicSettings, queryFn: () => http.get<PublicSettings>("/api/v1/settings/public"), staleTime: 60_000 });
}

export function useLicenseUpload() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (file: File) => upload<LicenseStatus>("POST", "/api/v1/license/upload", file),
    onSuccess: () => invalidate(["license"], ["vendor-link"]),
  });
}

export function useLicenseSync() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: () => http.post<LicenseStatus>("/api/v1/license/sync"),
    onSettled: () => invalidate(["license"], ["vendor-link"]),
  });
}

export function useVendorLink(enabled: boolean) {
  return useQuery({ queryKey: keys.vendorLink, queryFn: () => http.get<VendorLink>("/api/v1/license/vendor-link"), enabled });
}

// ---------- auth ----------

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
      // A different person may sign in on the same PC: never show the previous user's cached data.
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

// ---------- staff & roles ----------

export function useStaff(params: { q?: string; roleId?: string; includeInactive: boolean; page: number }) {
  return useQuery({
    queryKey: keys.staff(params),
    queryFn: () => http.get<Page<Staff>>(`/api/v1/staff${query({ ...params, size: 50 })}`),
    placeholderData: keepPreviousData,
  });
}

export function useSaveStaff() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, body }: { id?: string; body: StaffRequest }): Promise<Staff | StaffWithPassword> =>
      id ? http.put<Staff>(`/api/v1/staff/${id}`, body) : http.post<StaffWithPassword>("/api/v1/staff", body),
    onSuccess: () => invalidate(["staff"], ["roles"]),
  });
}

export function useStaffAction() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, action, version }: { id: string; action: "activate" | "deactivate" | "reset-password"; version: number }) =>
      http.post<Staff | StaffWithPassword>(`/api/v1/staff/${id}/${action}`, { version }),
    onSuccess: () => invalidate(["staff"], ["roles"]),
  });
}

export function useSignatureUpload() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, file }: { id: string; file: File | null }) =>
      file ? upload<void>("PUT", `/api/v1/staff/${id}/signature`, file) : http.delete(`/api/v1/staff/${id}/signature`),
    onSuccess: () => invalidate(["staff"]),
  });
}

export function useRoles(enabled = true) {
  return useQuery({ queryKey: keys.roles, queryFn: () => http.get<Role[]>("/api/v1/roles"), enabled });
}

export function usePermissionCatalog() {
  return useQuery({ queryKey: keys.permissions, queryFn: () => http.get<PermissionGroup[]>("/api/v1/permissions"), staleTime: Infinity });
}

export function useSaveRole() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, body }: { id?: string; body: { name: string | null; permissions: string[]; version?: number } }) =>
      id ? http.put<Role>(`/api/v1/roles/${id}`, body) : http.post<Role>("/api/v1/roles", body),
    onSuccess: () => invalidate(["roles"]),
  });
}

export function useDeleteRole() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, version }: { id: string; version: number }) => http.delete(`/api/v1/roles/${id}${query({ version })}`),
    onSuccess: () => invalidate(["roles"]),
  });
}

// ---------- settings ----------

export function useSettings() {
  return useQuery({ queryKey: keys.settings, queryFn: () => http.get<Settings>("/api/v1/settings") });
}

export function useSaveSettings() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (body: SettingsRequest) => http.put<Settings>("/api/v1/settings", body),
    onSuccess: () => invalidate(["settings"]),
  });
}

export function useLogoUpload() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (file: File | null) => (file ? upload<void>("PUT", "/api/v1/settings/logo", file) : http.delete("/api/v1/settings/logo")),
    onSuccess: () => invalidate(["settings"]),
  });
}

// ---------- import ----------

export function useImportPreview() {
  return useMutation({ mutationFn: (file: File) => upload<ImportPreview>("POST", "/api/v1/imports/STAFF/preview", file) });
}

export function useImportCommit() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (batchId: string) => http.post<ImportResult>(`/api/v1/imports/${batchId}/commit`),
    onSuccess: () => invalidate(["staff"], ["roles"]),
  });
}

// ---------- audit ----------

export function useAudit(params: { action?: string; entityType?: string; page: number }) {
  return useQuery({
    queryKey: keys.audit(params),
    queryFn: () => http.get<Page<AuditEntry>>(`/api/v1/audit${query({ ...params, size: 50 })}`),
    placeholderData: keepPreviousData,
  });
}
