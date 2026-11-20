import * as React from "react";
import { useTranslation } from "react-i18next";
import { type AuditSearch, useAudit } from "@/api/hooks";
import type { AuditEntry } from "@/api/types";
import { EmptyState, ErrorState, PageHeader, Pagination, Spinner } from "@/components/Common";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Table, Td, Th, Tr } from "@/components/ui/table";
import { formatDateTime } from "@/lib/format";

export function AuditPage() {
  const { t } = useTranslation();
  const [action, setAction] = React.useState("");
  const [entityType, setEntityType] = React.useState("");
  const [applied, setApplied] = React.useState<Pick<AuditSearch, "action" | "entityType">>({});

  const apply = () => setApplied({ action: action.trim() || undefined, entityType: entityType.trim() || undefined });

  return (
    <>
      <PageHeader title={t("audit.title")} />
      <div className="mb-4 flex flex-wrap gap-3">
        <Input
          autoFocus
          className="h-11 max-w-xs"
          placeholder={t("audit.filterAction")}
          value={action}
          onChange={(e) => setAction(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && apply()}
          onBlur={apply}
        />
        <Input
          className="h-11 max-w-xs"
          placeholder={t("audit.filterEntity")}
          value={entityType}
          onChange={(e) => setEntityType(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && apply()}
          onBlur={apply}
        />
      </div>
      <Card>
        <AuditTable filter={applied} />
      </Card>
    </>
  );
}

/** Reusable audit list (global page and clinic history tab). */
export function AuditTable({ filter }: { filter: Omit<AuditSearch, "page"> }) {
  const { t } = useTranslation();
  const [page, setPage] = React.useState(0);
  const [expanded, setExpanded] = React.useState<string | null>(null);
  const filterKey = JSON.stringify(filter);
  React.useEffect(() => setPage(0), [filterKey]);
  const { data, isPending, error, refetch } = useAudit({ ...filter, page });

  if (isPending) return <Spinner />;
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />;
  if (data.items.length === 0) return <EmptyState text={t("audit.empty")} />;

  return (
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
                className="cursor-pointer hover:bg-muted/50"
                onClick={() => setExpanded(expanded === e.id ? null : e.id)}
                onKeyDown={(ev) => ev.key === "Enter" && setExpanded(expanded === e.id ? null : e.id)}
                aria-expanded={expanded === e.id}
              >
                <Td className="whitespace-nowrap">{formatDateTime(e.occurredAt)}</Td>
                <Td>
                  {e.actorName}
                  <div className="text-xs text-muted-foreground">{e.actorType}</div>
                </Td>
                <Td className="font-mono text-xs">{e.action}</Td>
                <Td className="text-xs">
                  {e.entityType}
                  {e.entityId && <div className="font-mono text-muted-foreground">{e.entityId.slice(0, 8)}…</div>}
                </Td>
                <Td className="font-mono text-xs">{e.ip ?? "—"}</Td>
              </Tr>
              {expanded === e.id && (
                <tr>
                  <td colSpan={5} className="border-b border-border bg-muted/40 px-4 py-3">
                    <Changes entry={e} />
                  </td>
                </tr>
              )}
            </React.Fragment>
          ))}
        </tbody>
      </Table>
      <Pagination page={page} totalPages={data.totalPages} onPage={setPage} />
    </>
  );
}

function Changes({ entry }: { entry: AuditEntry }) {
  const { t } = useTranslation();
  if (!entry.before && !entry.after) return <p className="text-sm text-muted-foreground">{t("common.none")}</p>;
  return (
    <div className="grid gap-3 md:grid-cols-2">
      {(["before", "after"] as const).map((side) =>
        entry[side] ? (
          <div key={side}>
            <div className="mb-1 text-xs font-semibold uppercase text-muted-foreground">{t(`audit.${side}`)}</div>
            <pre className="overflow-x-auto rounded-md bg-surface p-3 text-xs">{JSON.stringify(entry[side], null, 2)}</pre>
          </div>
        ) : null,
      )}
    </div>
  );
}
