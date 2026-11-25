import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { IdleLock } from "./IdleLock";

const me = { id: "1", login: "dilnoza", fullName: "Karimova Dilnoza", roleId: "r", roleTemplate: "RECEPTION", roleName: null, permissions: [], mustChangePassword: false };

describe("IdleLock", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.stubGlobal("fetch", vi.fn(() => Promise.resolve(new Response(JSON.stringify({ name: "Shifo", hasLogo: false, idleLockMinutes: 1 }), { status: 200 }))));
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("locks after the idle time, activity postpones it", async () => {
    const client = new QueryClient();
    client.setQueryData(["settings", "public"], { name: "Shifo", hasLogo: false, idleLockMinutes: 1 });
    const router = createMemoryRouter([{ path: "/", element: <IdleLock me={me} /> }]);
    render(
      <QueryClientProvider client={client}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    );

    act(() => vi.advanceTimersByTime(50_000));
    fireEvent.keyDown(window, { key: "a" });
    act(() => vi.advanceTimersByTime(50_000));
    expect(screen.queryByText("Ekran qulflangan")).toBeNull();

    act(() => vi.advanceTimersByTime(15_000));
    expect(screen.getByText("Ekran qulflangan")).toBeInTheDocument();
    expect(screen.getByText("Karimova Dilnoza")).toBeInTheDocument();
  });
});
