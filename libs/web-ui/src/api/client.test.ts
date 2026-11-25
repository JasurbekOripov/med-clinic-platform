import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, http, query } from "./client";

function json(status: number, body: unknown, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json", ...headers } });
}

describe("api client", () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    document.cookie = "XSRF-TOKEN=token-1; path=/";
  });

  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
    document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
  });

  it("sends the CSRF cookie value as header on mutations, not on reads", async () => {
    fetchMock.mockImplementation(() => Promise.resolve(json(200, { ok: true })));

    await http.get("/api/v1/x");
    await http.post("/api/v1/y", { a: 1 });

    const getHeaders = fetchMock.mock.calls[0]![1]!.headers as Record<string, string>;
    const postInit = fetchMock.mock.calls[1]![1]!;
    expect(getHeaders["X-XSRF-TOKEN"]).toBeUndefined();
    expect((postInit.headers as Record<string, string>)["X-XSRF-TOKEN"]).toBe("token-1");
    expect(postInit.body).toBe('{"a":1}');
    expect(postInit.credentials).toBe("same-origin");
  });

  it("turns problem+json into ApiError with code, params and field errors", async () => {
    fetchMock.mockResolvedValue(
      json(400, { code: "validation.failed", fieldErrors: [{ field: "name", code: "validation.not_blank" }], requestId: "r1" }),
    );

    const error = await http.post("/api/v1/clinics", {}).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 400, code: "validation.failed", requestId: "r1" });
    expect((error as ApiError).fieldErrors[0]?.field).toBe("name");
  });

  it("retries once with a fresh token when the CSRF token was rejected", async () => {
    fetchMock
      .mockResolvedValueOnce(json(403, { code: "auth.csrf_invalid" }))
      .mockResolvedValueOnce(json(200, {})) // GET /auth/csrf
      .mockResolvedValueOnce(json(200, { done: true }));

    await expect(http.post("/api/v1/y")).resolves.toEqual({ done: true });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[1]![0]).toBe("/api/v1/auth/csrf");
  });

  it("maps network failures and non-JSON errors to stable codes", async () => {
    fetchMock.mockRejectedValueOnce(new TypeError("offline"));
    await expect(http.get("/x")).rejects.toMatchObject({ status: 0, code: "common.network_error" });

    fetchMock.mockResolvedValueOnce(new Response("<html>bad gateway</html>", { status: 502 }));
    await expect(http.get("/x")).rejects.toMatchObject({ status: 502, code: "common.internal_error" });
  });

  it("returns undefined for 204 and builds query strings without empty values", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    await expect(http.delete("/x")).resolves.toBeUndefined();
    expect(query({ q: "a b", status: "", page: 0, x: undefined, y: null })).toBe("?q=a+b&page=0");
    expect(query({})).toBe("");
  });
});
