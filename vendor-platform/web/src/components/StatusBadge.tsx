import { useTranslation } from "react-i18next";
import type { AlertType, LicenseStatus, ServerStatus, TenantStatus } from "@/api/types";
import { Badge, type BadgeTone } from "./ui/badge";

const TENANT: Record<TenantStatus, BadgeTone> = { ACTIVE: "green", SUSPENDED: "amber", ARCHIVED: "gray" };
const SERVER: Record<ServerStatus, BadgeTone> = { ONLINE: "green", OFFLINE: "red", NEVER_SEEN: "amber", NOT_PAIRED: "gray" };
const LICENSE: Record<LicenseStatus, BadgeTone> = {
  ACTIVE: "green",
  PENDING: "blue",
  GRACE: "amber",
  EXPIRED: "red",
  REVOKED: "red",
  SUPERSEDED: "gray",
};

export function TenantStatusBadge({ status }: { status: TenantStatus }) {
  const { t } = useTranslation();
  return <Badge tone={TENANT[status]}>{t(`tenantStatus.${status}`)}</Badge>;
}

export function ServerStatusBadge({ status }: { status: ServerStatus }) {
  const { t } = useTranslation();
  return <Badge tone={SERVER[status]}>{t(`serverStatus.${status}`)}</Badge>;
}

export function LicenseStatusBadge({ status }: { status: LicenseStatus }) {
  const { t } = useTranslation();
  return <Badge tone={LICENSE[status]}>{t(`licenseStatus.${status}`)}</Badge>;
}

export function AlertBadge({ type }: { type: AlertType }) {
  const { t } = useTranslation();
  return <Badge tone={type === "SERVER_OFFLINE" || type === "BACKUP_FAILED" ? "red" : "amber"}>{t(`alertType.${type}`)}</Badge>;
}
