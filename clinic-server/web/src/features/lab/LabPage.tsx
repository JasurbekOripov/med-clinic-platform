import { Copy, FlaskConical, Pencil, Plus, Trash2 } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { EmptyState, ErrorState, PageHeader, Spinner } from "@medclinic/web-ui/components/Common";
import { ConfirmDialog } from "@medclinic/web-ui/components/ConfirmDialog";
import { Badge } from "@medclinic/web-ui/components/ui/badge";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Card } from "@medclinic/web-ui/components/ui/card";
import { cn } from "@medclinic/web-ui/lib/utils";
import { useLabTemplateMutations, useLabTemplates, useServices } from "@/api/hooks";
import type { LabTemplate } from "@/api/types";
import { usePermissions } from "@/features/auth/permissions";
import { copyOf, LabFormEditor } from "./LabFormEditor";

type Editing = { template?: LabTemplate; initial?: Pick<LabTemplate, "name" | "rows" | "serviceIds"> };

/** Laboratory: result forms (this step); the work list of paid tests is added next to it. */
export function LabPage() {
  const { t } = useTranslation();
  const { can } = usePermissions();
  const manage = can("LAB_TEMPLATES_MANAGE");
  const templates = useLabTemplates();
  const services = useServices();
  const { remove } = useLabTemplateMutations();
  const [editing, setEditing] = React.useState<Editing | null>(null);
  const [deleting, setDeleting] = React.useState<LabTemplate | null>(null);
  const serviceName = new Map((services.data ?? []).map((s) => [s.id, s.name]));

  if (editing) return <LabFormEditor template={editing.template} initial={editing.initial} onClose={() => setEditing(null)} />;

  return (
    <>
      <PageHeader
        title={t("lab.forms")}
        subtitle={t("lab.formsSubtitle")}
        actions={manage && <Button size="lg" onClick={() => setEditing({})}><Plus /> {t("lab.newForm")}</Button>}
      />
      {templates.isPending ? (
        <Spinner />
      ) : templates.error ? (
        <ErrorState error={templates.error} onRetry={() => void templates.refetch()} />
      ) : templates.data.length === 0 ? (
        <Card><EmptyState text={t("lab.noForms")} action={manage ? <p className="text-sm">{t("lab.noFormsHint")}</p> : undefined} /></Card>
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {templates.data.map((x) => (
            <Card key={x.id} className={cn("p-5", !x.active && "opacity-60")}>
              <div className="flex items-start gap-3">
                <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary"><FlaskConical className="size-5" aria-hidden /></div>
                <div className="min-w-0 flex-1">
                  <div className="font-semibold">{x.name} {!x.active && <Badge tone="gray" className="ml-1">{t("catalog.inactive")}</Badge>}</div>
                  <div className="text-sm text-muted-foreground">
                    {t("lab.rowsCount", { count: x.rows.filter((r) => r.kind !== "HEADER").length })} · {t("lab.versionShort", { version: x.versionNo })}
                  </div>
                </div>
                {manage && (
                  <>
                    <Button size="icon" variant="ghost" aria-label={t("common.edit")} onClick={() => setEditing({ template: x })}><Pencil /></Button>
                    <Button size="icon" variant="ghost" aria-label={t("lab.copyForm")} title={t("lab.copyForm")} onClick={() => setEditing({ initial: copyOf(x, t("lab.copySuffix")) })}><Copy /></Button>
                    <Button size="icon" variant="ghost" aria-label={t("common.delete")} onClick={() => setDeleting(x)}><Trash2 /></Button>
                  </>
                )}
              </div>
              <div className="mt-3 flex flex-wrap gap-1.5">
                {x.serviceIds.length === 0 ? (
                  <span className="text-xs text-amber-700">{t("lab.noServicesLinked")}</span>
                ) : (
                  x.serviceIds.map((id) => <Badge key={id} tone="gray">{serviceName.get(id) ?? "—"}</Badge>)
                )}
              </div>
            </Card>
          ))}
        </div>
      )}
      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(o) => !o && setDeleting(null)}
        title={deleting ? t("lab.confirmDelete", { name: deleting.name }) : ""}
        description={t("lab.deleteHint")}
        confirmLabel={t("common.delete")}
        danger
        onConfirm={async () => {
          if (!deleting) return;
          await remove.mutateAsync({ id: deleting.id, version: deleting.version });
          toast.success(t("common.saved"));
        }}
      />
    </>
  );
}
