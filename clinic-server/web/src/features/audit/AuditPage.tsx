import * as React from "react";
import { useTranslation } from "react-i18next";
import { EmptyState, ErrorState, PageHeader, Pagination, Spinner } from "@medclinic/web-ui/components/Common";
import { Card } from "@medclinic/web-ui/components/ui/card";
import { Input } from "@medclinic/web-ui/components/ui/input";
import { Table, Td, Th, Tr } from "@medclinic/web-ui/components/ui/table";
import { formatDateTime } from "@medclinic/web-ui/lib/format";
import { useAudit } from "@/api/hooks";

export function AuditPage() {
  const { t } = useTranslation();
  const [action, setAction] = React.useState("");
  const [entityType, setEntityType] = React.useState("");
  const [applied, setApplied] = React.useState<{ action?: string; entityType?: string }>({});
  const [page, setPage] = React.useState(0);
  const [expanded, setExpanded] = React.useState<string | null>(null);
  const { data, isPending, error, refetch } = useAudit({ ...applied, page });
  const apply = () => {
    setApplied({ action: action.trim() || undefined, entityType: entityType.trim() || undefined });
    setPage(0);
  };

  return (
    <>
      <PageHeader title={t("audit.title")} />
      <div className="mb-5 flex flex-wrap gap-3">
        <Input autoFocus className="h-11 max-w-xs" placeholder={t("audit.filterAction")} value={action} onChange={(e) => setAction(e.target.value)} onKeyDown={(e) => e.key === "Enter" && apply()} onBlur={apply} />
        <Input className="h-11 max-w-xs" placeholder={t("audit.filterEntity")} value={entityType} onChange={(e) => setEntityType(e.target.value)} onKeyDown={(e) => e.key === "Enter" && apply()} onBlur={apply} />
      </div>
      <Card>
        {isPending ? (
          <Spinner />
        ) : error ? (
          <ErrorState error={error} onRetry={() => void refetch()} />
        ) : data.items.length === 0 ? (
          <EmptyState text={t("audit.empty")} />
        ) : (
          <>
            <Table>
              <thead>
                <tr>
                  <Th>{t("audit.when")}</Th>
                  <Th>{t("audit.who")}</Th>
                  <Th>{t("audit.action")}</Th>
                  <Th>{t("audit.entity")}</Th>
                  <Th>{t("audit.ip")}</Th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((e) => (
                  <React.Fragment key={e.id}>
                    <Tr
                      tabIndex={0}
                      className="cursor-pointer hover:bg-muted/60"
                      aria-expanded={expanded === e.id}
                      onClick={() => setExpanded(expanded === e.id ? null : e.id)}
                      onKeyDown={(ev) => ev.key === "Enter" && setExpanded(expanded === e.id ? null : e.id)}
                    >
                      <Td className="whitespace-nowrap">{formatDateTime(e.occurredAt)}</Td>
                      <Td>{e.actorName}</Td>
                      <Td className="font-mono text-xs">{e.action}</Td>
                      <Td className="text-xs">{e.entityType}</Td>
                      <Td className="font-mono text-xs">{e.ip ?? "—"}</Td>
                    </Tr>
                    {expanded === e.id && (
                      <tr>
                        <td colSpan={5} className="border-b border-border bg-muted/50 px-4 py-3">
                          <div className="grid gap-3 md:grid-cols-2">
                            {(["before", "after"] as const).map((side) =>
                              e[side] ? (
                                <div key={side}>
                                  <div className="mb-1 text-xs font-semibold uppercase text-muted-foreground">{t(`audit.${side}`)}</div>
                                  <pre className="overflow-x-auto rounded-md bg-surface p-3 text-xs">{JSON.stringify(e[side], null, 2)}</pre>
                                </div>
                              ) : null,
                            )}
                          </div>
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
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
