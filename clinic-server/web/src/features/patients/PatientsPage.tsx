import { Plus, Search, UserRound } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { EmptyState, ErrorState, PageHeader, Pagination, Spinner } from "@medclinic/web-ui/components/Common";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Card } from "@medclinic/web-ui/components/ui/card";
import { Input } from "@medclinic/web-ui/components/ui/input";
import { formatDate, formatPhone } from "@medclinic/web-ui/lib/format";
import { cn } from "@medclinic/web-ui/lib/utils";
import { usePatients } from "@/api/hooks";
import { usePermissions } from "@/features/auth/permissions";
import { ageOn } from "./age";
import { PatientDialog } from "./PatientDialog";

/**
 * Reception's main screen: one big search box (name in any script, phone or card number), arrow keys to pick,
 * Enter to open, F2 for a new patient. No mouse needed.
 */
export function PatientsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { can } = usePermissions();
  const canCreate = can("PATIENT_EDIT");
  const [text, setText] = React.useState("");
  const [q, setQ] = React.useState("");
  const [page, setPage] = React.useState(0);
  const [active, setActive] = React.useState(0);
  const [creating, setCreating] = React.useState(false);
  const { data, isPending, error, refetch, isFetching } = usePatients({ q: q || undefined, page });
  const items = data?.items ?? [];

  React.useEffect(() => {
    const id = setTimeout(() => {
      setQ(text.trim());
      setPage(0);
      setActive(0);
    }, 200);
    return () => clearTimeout(id);
  }, [text]);

  React.useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "F2" && canCreate) {
        e.preventDefault();
        setCreating(true);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [canCreate]);

  const onSearchKey = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActive((a) => Math.min(a + 1, items.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActive((a) => Math.max(a - 1, 0));
    } else if (e.key === "Enter") {
      const target = items[active];
      if (target) navigate(`/patients/${target.id}`);
      else if (canCreate && text.trim()) setCreating(true);
    } else if (e.key === "Escape") {
      setText("");
    }
  };

  return (
    <>
      <PageHeader
        title={t("patients.title")}
        actions={
          canCreate && (
            <Button size="lg" onClick={() => setCreating(true)}>
              <Plus /> {t("patients.new")} <kbd className="ml-1 rounded bg-white/20 px-1.5 text-xs">F2</kbd>
            </Button>
          )
        }
      />
      <div className="relative mb-2">
        <Search className="pointer-events-none absolute left-4 top-1/2 size-5 -translate-y-1/2 text-muted-foreground" aria-hidden />
        <Input
          autoFocus
          type="search"
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={onSearchKey}
          placeholder={t("patients.searchPlaceholder")}
          aria-label={t("common.search")}
          className="h-14 rounded-xl pl-12 text-lg shadow-sm"
        />
      </div>
      <p className="mb-5 text-xs text-muted-foreground">{t("patients.searchHint")}</p>

      <Card>
        {isPending ? (
          <Spinner />
        ) : error ? (
          <ErrorState error={error} onRetry={() => void refetch()} />
        ) : items.length === 0 ? (
          <EmptyState text={t("patients.empty")} action={canCreate ? <p className="text-sm">{t("patients.emptyHint")}</p> : undefined} />
        ) : (
          <>
            {!q && <div className="px-5 pt-4 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t("patients.recent")}</div>}
            <ul className={cn("divide-y divide-border p-2", isFetching && "opacity-70")} role="listbox" aria-label={t("patients.title")}>
              {items.map((p, i) => (
                <li key={p.id} role="option" aria-selected={i === active}>
                  <button
                    type="button"
                    onMouseEnter={() => setActive(i)}
                    onClick={() => navigate(`/patients/${p.id}`)}
                    className={cn(
                      "flex w-full items-center gap-4 rounded-lg px-4 py-3 text-left transition-colors",
                      i === active ? "bg-primary/10" : "hover:bg-muted",
                    )}
                  >
                    <div className={cn("flex size-11 shrink-0 items-center justify-center rounded-full", p.gender === "FEMALE" ? "bg-rose-100 text-rose-700" : "bg-sky-100 text-sky-700")}>
                      <UserRound className="size-5" aria-hidden />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-base font-medium">{p.fullName}</div>
                      <div className="text-sm text-muted-foreground">
                        {formatDate(p.birthDate)} · {t("patients.age", { count: ageOn(p.birthDate) })} · {formatPhone(p.phone)}
                      </div>
                    </div>
                    <div className="text-right text-sm text-muted-foreground">
                      {t("patients.card")} <span className="font-mono font-semibold text-foreground">{p.cardNo}</span>
                    </div>
                  </button>
                </li>
              ))}
            </ul>
            <Pagination page={page} totalPages={data.totalPages} onPage={setPage} />
          </>
        )}
      </Card>
      <PatientDialog open={creating} onOpenChange={setCreating} initialSearch={q} />
    </>
  );
}
