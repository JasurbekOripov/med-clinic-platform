import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, http, query, readCookie } from "@medclinic/web-ui/api/client";
import type {
  Agent,
  AgentRequest,
  AuditEntry,
  LabTemplate,
  LabTemplateRequest,
  Conclusion,
  ConclusionTemplate,
  CreateVisitRequest,
  Payment,
  PaymentMethod,
  PrintSettings,
  Queue,
  QueueRequest,
  Ticket,
  UnpaidVisit,
  Visit,
  DiscountType,
  DiscountTypeRequest,
  Doctor,
  ServiceGroup,
  ServiceItem,
  ServiceRequest,
  DuplicateCandidate,
  Patient,
  PatientRequest,
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
  patients: (params: object) => ["patients", params] as const,
  patient: (id: string) => ["patient", id] as const,
  serviceGroups: ["catalog", "groups"] as const,
  services: ["catalog", "services"] as const,
  doctors: ["catalog", "doctors"] as const,
  discounts: ["catalog", "discounts"] as const,
  agents: ["agents"] as const,
  patientVisits: (patientId: string) => ["visits", "patient", patientId] as const,
  unpaid: (page: number) => ["cashier", "unpaid", page] as const,
  printSettings: ["settings", "print"] as const,
  queues: ["queues"] as const,
  conclusions: (patientId: string) => ["conclusions", patientId] as const,
  conclusionTemplates: ["conclusion-templates"] as const,
  labTemplates: ["lab", "templates"] as const,
  tickets: (queueId: string) => ["queues", "tickets", queueId] as const,
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

// ---------- patients ----------

export function usePatients(params: { q?: string; page: number }) {
  return useQuery({
    queryKey: keys.patients(params),
    queryFn: () => http.get<Page<Patient>>(`/api/v1/patients${query({ ...params, size: 30 })}`),
    placeholderData: keepPreviousData,
  });
}

export function usePatient(id: string) {
  return useQuery({ queryKey: keys.patient(id), queryFn: () => http.get<Patient>(`/api/v1/patients/${id}`) });
}

export function useSavePatient() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, body }: { id?: string; body: PatientRequest }) =>
      id ? http.put<Patient>(`/api/v1/patients/${id}`, body) : http.post<Patient>("/api/v1/patients", body),
    onSuccess: (p) => invalidate(["patients"], ["patient", p.id]),
  });
}

export function useDeletePatient() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, version }: { id: string; version: number }) => http.delete(`/api/v1/patients/${id}${query({ version })}`),
    onSuccess: () => invalidate(["patients"]),
  });
}

/** Live duplicate check while the form is filled in (only once enough is typed to be meaningful). */
export function useDuplicateCheck(body: Partial<PatientRequest>, excludeId: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: ["patients", "duplicates", body, excludeId],
    queryFn: () => http.post<DuplicateCandidate[]>(`/api/v1/patients/duplicates${query({ excludeId })}`, body),
    enabled,
    staleTime: 10_000,
  });
}

/** Candidates attached to a 409 `patient.possible_duplicate` response. */
export function duplicateCandidates(error: unknown): DuplicateCandidate[] {
  return error instanceof ApiError && error.code === "patient.possible_duplicate"
    ? ((error.body.candidates as DuplicateCandidate[] | undefined) ?? [])
    : [];
}

// ---------- price list (catalog) ----------

export function useServiceGroups() {
  return useQuery({ queryKey: keys.serviceGroups, queryFn: () => http.get<ServiceGroup[]>("/api/v1/catalog/groups") });
}

export function useServices() {
  return useQuery({ queryKey: keys.services, queryFn: () => http.get<ServiceItem[]>("/api/v1/catalog/services") });
}

export function useDoctors() {
  return useQuery({ queryKey: keys.doctors, queryFn: () => http.get<Doctor[]>("/api/v1/catalog/doctors") });
}

export function useDiscountTypes() {
  return useQuery({ queryKey: keys.discounts, queryFn: () => http.get<DiscountType[]>("/api/v1/catalog/discounts") });
}

/** Create/update/delete for one reference list; every change refreshes the queries under [prefix]. */
function useCatalogMutation<B, R>(path: string, prefix: string = "catalog") {
  const invalidate = useInvalidate();
  const save = useMutation({
    mutationFn: ({ id, body }: { id?: string; body: B }) => (id ? http.put<R>(`${path}/${id}`, body) : http.post<R>(path, body)),
    onSuccess: () => invalidate([prefix]),
  });
  const remove = useMutation({
    mutationFn: ({ id, version }: { id: string; version: number }) => http.delete(`${path}/${id}${query({ version })}`),
    onSuccess: () => invalidate([prefix]),
  });
  return { save, remove };
}

export const useServiceGroupMutations = () =>
  useCatalogMutation<{ name: string; sortOrder: number; version?: number }, ServiceGroup>("/api/v1/catalog/groups");
export const useServiceMutations = () => useCatalogMutation<ServiceRequest, ServiceItem>("/api/v1/catalog/services");
export const useDiscountMutations = () => useCatalogMutation<DiscountTypeRequest, DiscountType>("/api/v1/catalog/discounts");

// ---------- referral agents ----------

export function useAgents() {
  return useQuery({ queryKey: keys.agents, queryFn: () => http.get<Agent[]>("/api/v1/agents") });
}

export const useAgentMutations = () => useCatalogMutation<AgentRequest, Agent>("/api/v1/agents", "agents");

// ---------- visits ----------

export function usePatientVisits(patientId: string) {
  return useQuery({
    queryKey: keys.patientVisits(patientId),
    queryFn: () => http.get<Visit[]>(`/api/v1/visits${query({ patientId })}`),
    // Payments happen at another desk: keep the reception's view fresh without manual reloads.
    refetchInterval: 20_000,
  });
}

/** Every visit change can move money state: refresh visits and the cashier's list together. */
function useVisitMutation<V>(fn: (vars: V) => Promise<Visit>) {
  const invalidate = useInvalidate();
  return useMutation({ mutationFn: fn, onSuccess: () => invalidate(["visits"], ["cashier"]) });
}

export const useCreateVisit = () => useVisitMutation((body: CreateVisitRequest) => http.post<Visit>("/api/v1/visits", body));

export const useAddVisitService = () =>
  useVisitMutation(({ id, ...body }: { id: string; serviceId: string; doctorId?: string; version: number }) =>
    http.post<Visit>(`/api/v1/visits/${id}/services`, body),
  );

export const useCancelVisitService = () =>
  useVisitMutation(({ id, itemId, ...body }: { id: string; itemId: string; reason: string; version: number }) =>
    http.post<Visit>(`/api/v1/visits/${id}/services/${itemId}/cancel`, body),
  );

export const useUpdateVisit = () =>
  useVisitMutation(({ id, ...body }: { id: string; discountTypeId?: string; agentId?: string; version: number }) =>
    http.put<Visit>(`/api/v1/visits/${id}`, body),
  );

export const useAllowUnpaid = () =>
  useVisitMutation(({ id, ...body }: { id: string; itemIds: string[]; reason: string; version: number }) =>
    http.post<Visit>(`/api/v1/visits/${id}/allow`, body),
  );

// ---------- cashier ----------

export function useUnpaidVisits(page: number) {
  return useQuery({
    queryKey: keys.unpaid(page),
    queryFn: () => http.get<Page<UnpaidVisit>>(`/api/v1/cashier/unpaid${query({ page })}`),
    placeholderData: keepPreviousData,
    refetchInterval: 10_000,
  });
}

export function usePay() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (body: { visitId: string; itemIds: string[]; method: PaymentMethod; amount: number }) =>
      http.post<Payment>("/api/v1/cashier/payments", body),
    // A rejected payment (amount changed, already paid) also means the screen is stale.
    onSettled: () => invalidate(["cashier"], ["visits"]),
  });
}

export function useVisitPayments(visitId: string | undefined) {
  return useQuery({
    queryKey: ["cashier", "payments", visitId],
    queryFn: () => http.get<Payment[]>(`/api/v1/cashier/payments${query({ visitId })}`),
    enabled: Boolean(visitId),
  });
}

export function usePrintSettings() {
  return useQuery({ queryKey: keys.printSettings, queryFn: () => http.get<PrintSettings>("/api/v1/settings/print"), staleTime: 5 * 60_000 });
}

// ---------- queues ----------

export function useQueues() {
  return useQuery({ queryKey: keys.queues, queryFn: () => http.get<Queue[]>("/api/v1/queues") });
}

export const useQueueMutations = () => useCatalogMutation<QueueRequest, Queue>("/api/v1/queues", "queues");

export function useQueueTickets(queueId: string | undefined) {
  return useQuery({
    queryKey: keys.tickets(queueId ?? ""),
    queryFn: () => http.get<Ticket[]>(`/api/v1/queues/${queueId}/tickets`),
    enabled: Boolean(queueId),
    // New paid patients appear without reloading the page.
    refetchInterval: 15_000,
  });
}

export function useTicketDone() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, done, version }: { id: string; done: boolean; version: number }) =>
      http.post<void>(`/api/v1/queues/tickets/${id}/done`, { done, version }),
    onSettled: () => invalidate(["queues", "tickets"]),
  });
}

// ---------- conclusions ----------

export function usePatientConclusions(patientId: string, enabled: boolean) {
  return useQuery({
    queryKey: keys.conclusions(patientId),
    queryFn: () => http.get<Conclusion[]>(`/api/v1/conclusions${query({ patientId })}`),
    enabled,
  });
}

export function useSaveConclusion() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, ...body }: { id?: string; visitItemId?: string; title: string; body: string; version?: number }) =>
      id ? http.put<Conclusion>(`/api/v1/conclusions/${id}`, body) : http.post<Conclusion>("/api/v1/conclusions", body),
    onSuccess: () => invalidate(["conclusions"]),
  });
}

export function useAddConclusionNote() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, text }: { id: string; text: string }) => http.post<Conclusion>(`/api/v1/conclusions/${id}/notes`, { text }),
    onSuccess: () => invalidate(["conclusions"]),
  });
}

export function useConclusionTemplates() {
  return useQuery({ queryKey: keys.conclusionTemplates, queryFn: () => http.get<ConclusionTemplate[]>("/api/v1/conclusions/templates") });
}

export const useConclusionTemplateMutations = () =>
  useCatalogMutation<{ name: string; body: string; version?: number }, ConclusionTemplate>("/api/v1/conclusions/templates", "conclusion-templates");

/** Records the reprint on the server (audited) and returns the payment to print as a copy. */
export function useReprint() {
  return useMutation({ mutationFn: (id: string) => http.post<Payment>(`/api/v1/cashier/payments/${id}/reprint`) });
}

// ---------- laboratory ----------

export function useLabTemplates() {
  return useQuery({ queryKey: keys.labTemplates, queryFn: () => http.get<LabTemplate[]>("/api/v1/lab/templates") });
}

export const useLabTemplateMutations = () => useCatalogMutation<LabTemplateRequest, LabTemplate>("/api/v1/lab/templates", "lab");
