import * as React from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { useAlerts } from "@/api/hooks";
import { EmptyState, ErrorState, PageHeader, Pagination, Segmented, Spinner } from "@medclinic/web-ui/components/Common";
import { AlertBadge } from "@/components/StatusBadge";
import { Card } from "@medclinic/web-ui/components/ui/card";
import { Table, Td, Th, Tr } from "@medclinic/web-ui/components/ui/table";
import { formatDateTime } from "@medclinic/web-ui/lib/format";

export function AlertsPage() {
  const { t } = useTranslation();
  const [mode, setMode] = React.useState<"open" | "all">("open");
  const [page, setPage] = React.useState(0);
  const { data, isPending, error, refetch } = useAlerts({ open: mode === "open", page });

  return (
    <>
      <PageHeader title={t("alerts.title")} subtitle={t("dashboard.subtitle")} />
      <div className="mb-4">
        <Segmented
          value={mode}
          onChange={(m) => {
            setMode(m);
            setPage(0);
          }}
          options={[
            { value: "open", label: t("alerts.open") },
            { value: "all", label: t("alerts.all") },
          ]}
        />
      </div>
      <Card>
        {isPending ? (
          <Spinner />
        ) : error ? (
          <ErrorState error={error} onRetry={() => void refetch()} />
        ) : data.items.length === 0 ? (
          <EmptyState text={t("alerts.empty")} />
        ) : (
          <>
            <Table>
              <thead>
                <tr>
                  <Th>{t("alerts.type")}</Th>
                  <Th>{t("alerts.where")}</Th>
                  <Th>{t("alerts.openedAt")}</Th>
                  <Th>{t("alerts.resolvedAt")}</Th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((a) => (
                  <Tr key={a.id}>
                    <Td>
                      <AlertBadge type={a.type} />
                    </Td>
                    <Td>
                      <Link to={`/clinics/${a.clinicId}`} className="font-medium text-primary hover:underline">
                        {a.clinicName ?? "—"}
                      </Link>
                      <div className="text-xs text-muted-foreground">
                        {a.branchName} {a.branchCode && `· ${a.branchCode}`}
                      </div>
                    </Td>
                    <Td>{formatDateTime(a.openedAt)}</Td>
                    <Td>{a.resolvedAt ? formatDateTime(a.resolvedAt) : <span className="font-medium text-red-700">{t("alerts.ongoing")}</span>}</Td>
                  </Tr>
                ))}
              </tbody>
            </Table>
            <Pagination page={page} totalPages={data.totalPages} onPage={setPage} />
          </>
        )}
      </Card>
    </>
  );
}
