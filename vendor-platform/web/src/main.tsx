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

function onUnauthorized(error: unknown) {
  // Session expired/ended elsewhere: drop the cached user; route guards send the user to the login page.
  if (error instanceof ApiError && error.isUnauthorized) queryClient.setQueryData(keys.me, null);
}

const queryClient = new QueryClient({
  queryCache: new QueryCache({ onError: onUnauthorized }),
  mutationCache: new MutationCache({
    onError: (error) => {
      onUnauthorized(error);
      // Optimistic-lock conflict: someone else changed the data; reload everything visible so the user retries on fresh data.
      if (error instanceof ApiError && error.code === "common.version_conflict") void queryClient.invalidateQueries();
    },
  }),
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      refetchOnWindowFocus: true,
      // Client errors are final; only network/server errors are retried.
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
