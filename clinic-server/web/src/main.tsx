import "./index.css";
import "./i18n";
import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router";
import { Toaster } from "sonner";
import { ApiError } from "@medclinic/web-ui/api/client";
import { keys } from "./api/hooks";
import { router } from "./router";

function onError(error: unknown) {
  if (!(error instanceof ApiError)) return;
  if (error.isUnauthorized) queryClient.setQueryData(keys.me, null);
  // The license stopped (or the clock guard tripped) while working: refresh the license state so the stop screen shows.
  if (error.code.startsWith("license.")) void queryClient.invalidateQueries({ queryKey: keys.license });
}

const queryClient = new QueryClient({
  queryCache: new QueryCache({ onError }),
  mutationCache: new MutationCache({
    onError: (error) => {
      onError(error);
      if (error instanceof ApiError && error.code === "common.version_conflict") void queryClient.invalidateQueries();
    },
  }),
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      retry: (count, error) => !(error instanceof ApiError && error.status > 0 && error.status < 500) && count < 2,
    },
    mutations: { retry: false },
  },
});

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      <Toaster richColors position="bottom-right" closeButton />
    </QueryClientProvider>
  </StrictMode>,
);
