/**
 * Thin fetch wrapper for the staff API: same-origin session cookie, cookie-to-header CSRF, problem+json errors.
 */

export interface FieldError {
  field: string;
  code: string;
  params?: Record<string, unknown>;
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly params: Record<string, unknown> = {},
    readonly fieldErrors: FieldError[] = [],
    readonly requestId?: string,
  ) {
    super(code);
    this.name = "ApiError";
  }

  get isUnauthorized(): boolean {
    return this.status === 401;
  }
}

const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";

export function readCookie(name: string): string | null {
  const prefix = `${name}=`;
  for (const part of document.cookie.split(";")) {
    const trimmed = part.trim();
    if (trimmed.startsWith(prefix)) return decodeURIComponent(trimmed.slice(prefix.length));
  }
  return null;
}

async function refreshCsrfToken(): Promise<void> {
  await fetch("/api/v1/auth/csrf", { credentials: "same-origin", headers: { Accept: "application/json" } });
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as {
      code?: string;
      params?: Record<string, unknown>;
      fieldErrors?: FieldError[];
      requestId?: string;
    };
    return new ApiError(response.status, body.code ?? `http.${response.status}`, body.params ?? {}, body.fieldErrors ?? [], body.requestId);
  } catch {
    return new ApiError(response.status, response.status >= 500 ? "common.internal_error" : `http.${response.status}`);
  }
}

type Method = "GET" | "POST" | "PUT" | "DELETE";

async function send(method: Method, path: string, body: unknown, retryOnCsrf: boolean): Promise<Response> {
  const headers: Record<string, string> = { Accept: "application/json, application/problem+json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (method !== "GET") {
    if (!readCookie(CSRF_COOKIE)) await refreshCsrfToken();
    const token = readCookie(CSRF_COOKIE);
    if (token) headers[CSRF_HEADER] = token;
  }
  let response: Response;
  try {
    response = await fetch(path, {
      method,
      headers,
      credentials: "same-origin",
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, "common.network_error");
  }
  if (!response.ok) {
    const error = await toApiError(response);
    // An expired/rotated CSRF token is recoverable once: fetch a fresh one and repeat the request.
    if (retryOnCsrf && error.code === "auth.csrf_invalid") {
      await refreshCsrfToken();
      return send(method, path, body, false);
    }
    throw error;
  }
  return response;
}

export async function request<T>(method: Method, path: string, body?: unknown): Promise<T> {
  const response = await send(method, path, body, true);
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const http = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body ?? {}),
  put: <T>(path: string, body: unknown) => request<T>("PUT", path, body),
  delete: <T = void>(path: string) => request<T>("DELETE", path),
};

/** Downloads a file response (e.g. a license) through the browser without leaving the page. */
export async function downloadFile(path: string, fallbackName: string): Promise<void> {
  const response = await send("GET", path, undefined, false);
  const disposition = response.headers.get("Content-Disposition") ?? "";
  const match = /filename="?([^";]+)"?/.exec(disposition);
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = match?.[1] ?? fallbackName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function query(params: Record<string, string | number | boolean | null | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== "") search.set(key, String(value));
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}
