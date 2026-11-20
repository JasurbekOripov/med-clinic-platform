import { Plus } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { useClinics } from "@/api/hooks";
import type { TenantStatus } from "@/api/types";
import { EmptyState, ErrorState, PageHeader, Pagination, SearchBox, Segmented, Spinner } from "@/components/Common";
import { TenantStatusBadge } from "@/components/StatusBadge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Table, Td, Th, Tr } from "@/components/ui/table";
import { usePermissions } from "@/features/auth/permissions";
import { formatPhone } from "@/lib/format";
import { ClinicFormDialog } from "./ClinicFormDialog";

export function ClinicsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { can } = usePermissions();
  const [search, setSearch] = React.useState("");
  const [q, setQ] = React.useState("");
  const [status, setStatus] = React.useState<TenantStatus | "">("ACTIVE");
  const [page, setPage] = React.useState(0);
  const [creating, setCreating] = React.useState(false);

  React.useEffect(() => {
    const id = setTimeout(() => {
      setQ(search);
      setPage(0);
    }, 250);
    return () => clearTimeout(id);
  }, [search]);

  const { data, isPending, error, refetch } = useClinics({ q, status, page });

  return (
    <>
      <PageHeader
        title={t("clinics.title")}
        subtitle={data && t("common.total", { count: data.totalItems })}
        actions={
          can("CLINIC_EDIT") && (
            <Button size="lg" onClick={() => setCreating(true)}>
              <Plus /> {t("clinics.new")}
            </Button>
          )
        }
      />
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <SearchBox
          value={search}
          onChange={setSearch}
          placeholder={t("clinics.searchPlaceholder")}
          onEnter={() => {
            const first = data?.items[0];
            if (first) navigate(`/clinics/${first.id}`);
          }}
        />
        <Segmented
          value={status}
          onChange={(s) => {
            setStatus(s);
            setPage(0);
          }}
          options={(["ACTIVE", "SUSPENDED", "ARCHIVED", ""] as const).map((s) => ({
            value: s,
            label: s ? t(`tenantStatus.${s}`) : t("common.all"),
          }))}
        />
      </div>
      <Card>
        {isPending ? (
          <Spinner />
        ) : error ? (
          <ErrorState error={error} onRetry={() => void refetch()} />
        ) : data.items.length === 0 ? (
          <EmptyState text={t("clinics.empty")} />
        ) : (
          <>
            <Table>
              <thead>
                <tr>
                  <Th>{t("clinics.code")}</Th>
                  <Th>{t("common.name")}</Th>
                  <Th>{t("clinics.ownerName")}</Th>
                  <Th>{t("clinics.region")}</Th>
                  <Th>{t("clinics.branchCount")}</Th>
                  <Th>{t("common.status")}</Th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((c) => (
                  <Tr
                    key={c.id}
                    tabIndex={0}
                    className="cursor-pointer hover:bg-muted/50"
                    onClick={() => navigate(`/clinics/${c.id}`)}
                    onKeyDown={(e) => e.key === "Enter" && navigate(`/clinics/${c.id}`)}
                  >
                    <Td className="font-mono text-xs">{c.code}</Td>
                    <Td>
                      <div className="font-medium">{c.name}</div>
                      {c.legalName && <div className="text-xs text-muted-foreground">{c.legalName}</div>}
                    </Td>
                    <Td>
                      {c.ownerName}
                      <div className="text-xs text-muted-foreground">{formatPhone(c.phone)}</div>
                    </Td>
                    <Td>{t(`region.${c.region}`)}</Td>
                    <Td className="tabular-nums">{c.branchCount ?? 0}</Td>
                    <Td>
                      <TenantStatusBadge status={c.status} />
                    </Td>
                  </Tr>
                ))}
              </tbody>
            </Table>
            <Pagination page={page} totalPages={data.totalPages} onPage={setPage} />
          </>
        )}
      </Card>
      <ClinicFormDialog open={creating} onOpenChange={setCreating} onSaved={(c) => navigate(`/clinics/${c.id}`)} />
    </>
  );
}
