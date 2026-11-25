import { HardDrive } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import { useServers } from "@/api/hooks";
import type { ServerRow, ServerStatus } from "@/api/types";
import { EmptyState, ErrorState, PageHeader, SearchBox, Segmented, Spinner } from "@medclinic/web-ui/components/Common";
import { AlertBadge, ServerStatusBadge } from "@/components/StatusBadge";
import { Card } from "@medclinic/web-ui/components/ui/card";
import { Table, Td, Th, Tr } from "@medclinic/web-ui/components/ui/table";
import { formatAge, formatBytes, formatDateTime, formatUptime } from "@medclinic/web-ui/lib/format";
import { cn } from "@medclinic/web-ui/lib/utils";

const FILTERS: (ServerStatus | "")[] = ["", "OFFLINE", "ONLINE", "NEVER_SEEN", "NOT_PAIRED"];

function useDebounced<T>(value: T, delay = 250): T {
  const [debounced, setDebounced] = React.useState(value);
  React.useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(id);
  }, [value, delay]);
  return debounced;
}

export function DashboardPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [status, setStatus] = React.useState<ServerStatus | "">("");
  const [search, setSearch] = React.useState("");
  const q = useDebounced(search);
  const { data, isPending, error, refetch } = useServers({ status, q });

  const counts = data?.counts ?? {};
  const first = data?.servers[0];

  return (
    <>
      <PageHeader title={t("dashboard.title")} subtitle={t("dashboard.subtitle")} />
      <div className="mb-5 grid grid-cols-2 gap-3 sm:grid-cols-5">
        <Stat label={t("serverStatus.ONLINE")} value={counts.ONLINE} tone="text-emerald-700" />
        <Stat label={t("serverStatus.OFFLINE")} value={counts.OFFLINE} tone="text-red-700" />
        <Stat label={t("serverStatus.NEVER_SEEN")} value={counts.NEVER_SEEN} tone="text-amber-700" />
        <Stat label={t("serverStatus.NOT_PAIRED")} value={counts.NOT_PAIRED} tone="text-slate-600" />
        <Link to="/alerts" className="rounded-lg focus-visible:ring-2">
          <Stat label={t("dashboard.openAlerts")} value={data?.openAlertCount} tone={data?.openAlertCount ? "text-red-700" : "text-slate-600"} />
        </Link>
      </div>
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <SearchBox
          value={search}
          onChange={setSearch}
          placeholder={t("dashboard.searchPlaceholder")}
          onEnter={() => first && navigate(`/clinics/${first.clinicId}`)}
        />
        <Segmented
          value={status}
          onChange={setStatus}
          options={FILTERS.map((f) => ({ value: f, label: f ? t(`serverStatus.${f}`) : t("common.all") }))}
        />
      </div>
      <Card>
        {isPending ? (
          <Spinner />
        ) : error ? (
          <ErrorState error={error} onRetry={() => void refetch()} />
        ) : data.servers.length === 0 ? (
          <EmptyState text={t("dashboard.empty")} />
        ) : (
          <Table>
            <thead>
              <tr>
                <Th>{t("dashboard.clinicBranch")}</Th>
                <Th>{t("common.status")}</Th>
                <Th>{t("dashboard.lastSeen")}</Th>
                <Th>{t("dashboard.version")}</Th>
                <Th>{t("dashboard.disk")}</Th>
                <Th>{t("dashboard.backup")}</Th>
                <Th>{t("nav.alerts")}</Th>
              </tr>
            </thead>
            <tbody>
              {data.servers.map((row) => (
                <ServerTableRow key={row.branchId} row={row} onOpen={() => navigate(`/clinics/${row.clinicId}`)} />
              ))}
            </tbody>
          </Table>
        )}
      </Card>
    </>
  );
}

function Stat({ label, value, tone }: { label: string; value: number | undefined; tone: string }) {
  return (
    <Card className="px-4 py-3">
      <div className={cn("text-3xl font-semibold tabular-nums", tone)}>{value ?? "–"}</div>
      <div className="text-sm text-muted-foreground">{label}</div>
    </Card>
  );
}

function ServerTableRow({ row, onOpen }: { row: ServerRow; onOpen: () => void }) {
  const { t } = useTranslation();
  const minFree = row.disks.reduce<{ free: number; ratio: number } | null>((worst, d) => {
    if (d.totalBytes <= 0) return worst;
    const ratio = d.freeBytes / d.totalBytes;
    return !worst || ratio < worst.ratio ? { free: d.freeBytes, ratio } : worst;
  }, null);
  return (
    <Tr
      className={cn("cursor-pointer hover:bg-muted/50", row.status === "OFFLINE" && "bg-red-50/60")}
      onClick={onOpen}
      onKeyDown={(e) => e.key === "Enter" && onOpen()}
      tabIndex={0}
    >
      <Td>
        <div className="font-medium">{row.clinicName}</div>
        <div className="text-xs text-muted-foreground">
          {row.branchName} · {row.branchCode}
          {row.hostname ? ` · ${row.hostname}` : ""}
        </div>
      </Td>
      <Td>
        <ServerStatusBadge status={row.status} />
      </Td>
      <Td title={formatDateTime(row.lastHeartbeatAt)}>
        {row.lastHeartbeatAt ? t("time.ago", { value: formatAge(row.lastHeartbeatAt) }) : "—"}
        {row.uptimeSeconds != null && <div className="text-xs text-muted-foreground">{t("dashboard.uptime")}: {formatUptime(row.uptimeSeconds)}</div>}
      </Td>
      <Td className="tabular-nums">{row.appVersion ?? "—"}</Td>
      <Td className="tabular-nums">
        {minFree ? (
          <span className={cn("inline-flex items-center gap-1", minFree.ratio < 0.1 && "font-semibold text-red-700")}>
            <HardDrive className="size-3.5" aria-hidden />
            {formatBytes(minFree.free)} ({Math.round(minFree.ratio * 100)}%)
          </span>
        ) : (
          "—"
        )}
      </Td>
      <Td>
        {row.lastBackupAt ? (
          <span className={cn(row.lastBackupStatus === "FAILED" && "font-semibold text-red-700")}>{formatDateTime(row.lastBackupAt)}</span>
        ) : (
          "—"
        )}
      </Td>
      <Td>
        <div className="flex flex-wrap gap-1">
          {row.openAlerts.map((a) => (
            <AlertBadge key={a} type={a} />
          ))}
        </div>
      </Td>
    </Tr>
  );
}
