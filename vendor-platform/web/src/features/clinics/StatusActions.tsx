import * as React from "react";
import { useTranslation } from "react-i18next";
import type { TenantStatus } from "@/api/types";
import { ConfirmDialog } from "@medclinic/web-ui/components/ConfirmDialog";
import { Button } from "@medclinic/web-ui/components/ui/button";

type Action = "suspend" | "activate" | "archive" | "restore" | "delete";

const ACTIONS: Record<TenantStatus, Action[]> = {
  ACTIVE: ["suspend", "archive"],
  SUSPENDED: ["activate", "archive"],
  ARCHIVED: ["restore", "delete"],
};

const TARGET: Partial<Record<Action, TenantStatus>> = { suspend: "SUSPENDED", activate: "ACTIVE", archive: "ARCHIVED", restore: "ACTIVE" };

/**
 * Buttons for the status lifecycle of a clinic or branch (`kind`), each behind a confirmation that names the
 * object. Mirrors the server state machine; the server still validates every transition.
 */
export function StatusActions({
  kind,
  name,
  status,
  size = "md",
  onStatus,
  onDelete,
}: {
  kind: "clinics" | "branches";
  name: string;
  status: TenantStatus;
  size?: "sm" | "md";
  onStatus: (target: TenantStatus) => Promise<unknown>;
  onDelete: () => Promise<unknown>;
}) {
  const { t } = useTranslation();
  const [pending, setPending] = React.useState<Action | null>(null);

  const confirmKey: Record<Action, string> = {
    suspend: `${kind}.confirmSuspend`,
    activate: `${kind}.confirmRestore`,
    archive: `${kind}.confirmArchive`,
    restore: `${kind}.confirmRestore`,
    delete: `${kind}.confirmDelete`,
  };
  const label: Record<Action, string> = {
    suspend: t("clinics.suspend"),
    activate: t("clinics.activate"),
    archive: t("clinics.archive"),
    restore: t("clinics.restore"),
    delete: t("common.delete"),
  };

  return (
    <>
      {ACTIONS[status].map((action) => (
        <Button key={action} size={size} variant={action === "delete" ? "danger" : "secondary"} onClick={() => setPending(action)}>
          {label[action]}
        </Button>
      ))}
      <ConfirmDialog
        open={pending !== null}
        onOpenChange={(open) => !open && setPending(null)}
        title={pending ? t(confirmKey[pending], { name }) : ""}
        confirmLabel={pending ? label[pending] : ""}
        danger={pending === "delete" || pending === "archive" || pending === "suspend"}
        onConfirm={() => {
          if (!pending) return Promise.resolve();
          const target = TARGET[pending];
          return target ? onStatus(target) : onDelete();
        }}
      />
    </>
  );
}
