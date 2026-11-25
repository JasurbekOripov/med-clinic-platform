import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SetupPage } from "./SetupPage";

const json = (status: number, body: unknown) =>
  Promise.resolve(new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }));

describe("SetupPage", () => {
  const fetchMock = vi.fn<typeof fetch>();
  let paired = false;

  beforeEach(() => {
    paired = false;
    vi.stubGlobal("fetch", fetchMock);
    document.cookie = "XSRF-TOKEN=t; path=/";
    fetchMock.mockImplementation((input, init) => {
      const url = String(input);
      if (url.endsWith("/setup/status")) return json(200, { paired, hasAdmin: false, completed: false, clinicCode: paired ? "CL-0001" : null, branchCode: paired ? "CL-0001-01" : null });
      if (url.endsWith("/setup/pair")) {
        const body = JSON.parse(String(init?.body)) as { code: string };
        if (body.code !== "K7QM-2XRD-9WTF") return json(401, { code: "pairing.invalid_code" });
        paired = true;
        return json(200, { paired: true, hasAdmin: false, completed: false, clinicCode: "CL-0001", branchCode: "CL-0001-01" });
      }
      return json(404, {});
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  function renderSetup() {
    const router = createMemoryRouter([{ path: "/setup", element: <SetupPage /> }], { initialEntries: ["/setup"] });
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    );
  }

  it("shows the vendor error, then moves to the admin step after pairing", async () => {
    renderSetup();
    const url = await screen.findByLabelText("Vendor manzili");
    await userEvent.clear(url);
    await userEvent.type(url, "https://vendor.example.uz");
    const code = screen.getByLabelText("Ulanish kodi");

    await userEvent.type(code, "AAAA-BBBB-CCCC{Enter}");
    expect(await screen.findByRole("alert")).toHaveTextContent("Kod notoʻgʻri");

    await userEvent.clear(code);
    await userEvent.type(code, "K7QM-2XRD-9WTF{Enter}");
    expect(await screen.findByText("Ulandi: CL-0001 (CL-0001-01)")).toBeInTheDocument();
    expect(screen.getByText("Administrator hisobi")).toBeInTheDocument();
  });
});
