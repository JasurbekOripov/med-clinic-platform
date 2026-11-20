import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import i18n from "@/i18n";
import { LoginPage, safeNext } from "./LoginPage";

function respond(status: number, body: unknown) {
  return Promise.resolve(new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }));
}

describe("LoginPage", () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(async () => {
    vi.stubGlobal("fetch", fetchMock);
    document.cookie = "XSRF-TOKEN=t; path=/";
    await i18n.changeLanguage("ru");
  });

  afterEach(async () => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
    await i18n.changeLanguage("uz-Latn");
  });

  function renderAt(path: string) {
    const router = createMemoryRouter(
      [
        { path: "/login", element: <LoginPage /> },
        { path: "/", element: <div>home</div> },
        { path: "/clinics", element: <div>clinics page</div> },
        { path: "/change-password", element: <div>change password page</div> },
      ],
      { initialEntries: [path] },
    );
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    );
  }

  it("shows the translated server error on wrong credentials", async () => {
    fetchMock.mockImplementation((input) =>
      String(input).endsWith("/auth/me") ? respond(401, { code: "auth.unauthenticated" }) : respond(401, { code: "auth.bad_credentials" }),
    );
    renderAt("/login");

    await userEvent.type(await screen.findByLabelText("Логин"), "owner");
    await userEvent.type(screen.getByLabelText("Пароль"), "wrong-password{Enter}");

    expect(await screen.findByRole("alert")).toHaveTextContent("Неверный логин или пароль.");
  });

  it("goes to the requested page after login, or to password change when required", async () => {
    let mustChange = false;
    fetchMock.mockImplementation((input) =>
      String(input).endsWith("/auth/me")
        ? respond(401, { code: "auth.unauthenticated" })
        : respond(200, { id: "1", login: "owner", fullName: "Owner", role: "OWNER", permissions: [], mustChangePassword: mustChange }),
    );
    renderAt("/login?next=%2Fclinics");
    await userEvent.type(await screen.findByLabelText("Логин"), "owner");
    await userEvent.type(screen.getByLabelText("Пароль"), "Correct-Horse-42{Enter}");
    expect(await screen.findByText("clinics page")).toBeInTheDocument();

    mustChange = true;
    document.body.innerHTML = "";
    renderAt("/login");
    await userEvent.type(await screen.findByLabelText("Логин"), "owner");
    await userEvent.type(screen.getByLabelText("Пароль"), "temp-password-1{Enter}");
    expect(await screen.findByText("change password page")).toBeInTheDocument();
  });

  it("never redirects outside the app after login", () => {
    expect(safeNext("/clinics/1")).toBe("/clinics/1");
    expect(safeNext("//evil.example")).toBe("/");
    expect(safeNext("https://evil.example")).toBe("/");
    expect(safeNext(null)).toBe("/");
  });
});
