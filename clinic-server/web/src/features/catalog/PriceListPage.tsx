import { FolderPlus, Pencil, Percent, Plus, Search, Stethoscope, Trash2 } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { EmptyState, ErrorState, FormError, PageHeader, Segmented, Spinner } from "@medclinic/web-ui/components/Common";
import { ConfirmDialog } from "@medclinic/web-ui/components/ConfirmDialog";
import { Field } from "@medclinic/web-ui/components/Field";
import { Badge } from "@medclinic/web-ui/components/ui/badge";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Card } from "@medclinic/web-ui/components/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogTitle } from "@medclinic/web-ui/components/ui/dialog";
import { Input, MoneyInput, Select } from "@medclinic/web-ui/components/ui/input";
import { fieldErrors } from "@medclinic/web-ui/lib/errors";
import { formatAmount } from "@medclinic/web-ui/lib/money";
import { cn } from "@medclinic/web-ui/lib/utils";
import {
  useDiscountMutations,
  useDiscountTypes,
  useDoctors,
  useServiceGroupMutations,
  useServiceGroups,
  useServiceMutations,
  useServices,
} from "@/api/hooks";
import type { DiscountType, ServiceGroup, ServiceItem } from "@/api/types";
import { matchesSearch } from "./search";

type Tab = "services" | "discounts";

/** Admin's price list: groups of services (consultations may have an own price per doctor) and discount types. */
export function PriceListPage() {
  const { t } = useTranslation();
  const [tab, setTab] = React.useState<Tab>("services");
  return (
    <>
      <PageHeader title={t("catalog.title")} subtitle={t("catalog.subtitle")} />
      <div className="mb-5">
        <Segmented
          value={tab}
          onChange={setTab}
          options={[
            { value: "services", label: t("catalog.services") },
            { value: "discounts", label: t("catalog.discounts") },
          ]}
        />
      </div>
      {tab === "services" ? <ServicesTab /> : <DiscountsTab />}
    </>
  );
}

// ---------------------------------------------------------------- services

function ServicesTab() {
  const { t } = useTranslation();
  const groups = useServiceGroups();
  const services = useServices();
  const { remove: removeGroup } = useServiceGroupMutations();
  const { remove: removeService } = useServiceMutations();
  const [text, setText] = React.useState("");
  const [groupDialog, setGroupDialog] = React.useState<ServiceGroup | "new" | null>(null);
  const [serviceDialog, setServiceDialog] = React.useState<{ service?: ServiceItem; groupId?: string } | null>(null);
  const [deleting, setDeleting] = React.useState<{ kind: "group"; item: ServiceGroup } | { kind: "service"; item: ServiceItem } | null>(null);

  if (groups.isPending || services.isPending) return <Spinner />;
  if (groups.error || services.error) {
    return <ErrorState error={groups.error ?? services.error} onRetry={() => void Promise.all([groups.refetch(), services.refetch()])} />;
  }

  const visible = services.data.filter((s) => matchesSearch(text, s.name, s.code));
  const byGroup = new Map<string, ServiceItem[]>();
  for (const s of visible) byGroup.set(s.groupId, [...(byGroup.get(s.groupId) ?? []), s]);

  return (
    <>
      <div className="mb-5 flex flex-wrap items-center gap-3">
        <div className="relative min-w-64 flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input value={text} onChange={(e) => setText(e.target.value)} placeholder={t("catalog.searchPlaceholder")} className="pl-9" type="search" />
        </div>
        <Button variant="secondary" onClick={() => setGroupDialog("new")}>
          <FolderPlus /> {t("catalog.newGroup")}
        </Button>
        <Button onClick={() => setServiceDialog({})} disabled={groups.data.length === 0}>
          <Plus /> {t("catalog.newService")}
        </Button>
      </div>

      {groups.data.length === 0 ? (
        <Card>
          <EmptyState text={t("catalog.noGroups")} action={<p className="text-sm">{t("catalog.noGroupsHint")}</p>} />
        </Card>
      ) : (
        <div className="space-y-5">
          {groups.data.map((g) => {
            const items = byGroup.get(g.id) ?? [];
            if (text && items.length === 0) return null;
            return (
              <Card key={g.id} className="overflow-hidden">
                <div className="flex items-center gap-2 border-b border-border bg-muted/40 px-5 py-3">
                  <h2 className="flex-1 text-base font-semibold">
                    {g.name} <span className="ml-1 text-sm font-normal text-muted-foreground">{items.length}</span>
                  </h2>
                  <Button size="sm" variant="ghost" onClick={() => setServiceDialog({ groupId: g.id })}>
                    <Plus /> {t("catalog.addService")}
                  </Button>
                  <Button size="icon" variant="ghost" aria-label={t("common.edit")} onClick={() => setGroupDialog(g)}>
                    <Pencil />
                  </Button>
                  <Button size="icon" variant="ghost" aria-label={t("common.delete")} onClick={() => setDeleting({ kind: "group", item: g })}>
                    <Trash2 />
                  </Button>
                </div>
                {items.length === 0 ? (
                  <p className="px-5 py-4 text-sm text-muted-foreground">{t("catalog.groupEmpty")}</p>
                ) : (
                  <ul className="divide-y divide-border">
                    {items.map((s) => (
                      <li key={s.id} className={cn("flex items-center gap-4 px-5 py-3", !s.active && "opacity-60")}>
                        <div className="min-w-0 flex-1">
                          <div className="flex flex-wrap items-center gap-2">
                            {s.code && <span className="font-mono text-xs text-muted-foreground">{s.code}</span>}
                            <span className="font-medium">{s.name}</span>
                            {s.perDoctor && (
                              <Badge tone="blue">
                                <Stethoscope className="mr-1 size-3" aria-hidden />
                                {t("catalog.perDoctorBadge", { count: s.doctorPrices.length })}
                              </Badge>
                            )}
                            {!s.active && <Badge tone="gray">{t("catalog.inactive")}</Badge>}
                          </div>
                        </div>
                        <div className="text-right font-semibold tabular-nums">
                          {formatAmount(s.price)} <span className="text-sm font-normal text-muted-foreground">{t("common.sum")}</span>
                        </div>
                        <Button size="icon" variant="ghost" aria-label={t("common.edit")} onClick={() => setServiceDialog({ service: s })}>
                          <Pencil />
                        </Button>
                        <Button size="icon" variant="ghost" aria-label={t("common.delete")} onClick={() => setDeleting({ kind: "service", item: s })}>
                          <Trash2 />
                        </Button>
                      </li>
                    ))}
                  </ul>
                )}
              </Card>
            );
          })}
        </div>
      )}

      {groupDialog && <GroupDialog group={groupDialog === "new" ? undefined : groupDialog} nextOrder={groups.data.length} onClose={() => setGroupDialog(null)} />}
      {serviceDialog && (
        <ServiceDialog
          service={serviceDialog.service}
          groupId={serviceDialog.groupId}
          groups={groups.data}
          onClose={() => setServiceDialog(null)}
        />
      )}
      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(o) => !o && setDeleting(null)}
        title={deleting ? t(deleting.kind === "group" ? "catalog.confirmDeleteGroup" : "catalog.confirmDeleteService", { name: deleting.item.name }) : ""}
        description={deleting?.kind === "service" ? t("catalog.deleteServiceHint") : undefined}
        confirmLabel={t("common.delete")}
        danger
        onConfirm={async () => {
          if (!deleting) return;
          const target = { id: deleting.item.id, version: deleting.item.version };
          await (deleting.kind === "group" ? removeGroup : removeService).mutateAsync(target);
          toast.success(t("common.saved"));
        }}
      />
    </>
  );
}

function GroupDialog({ group, nextOrder, onClose }: { group?: ServiceGroup; nextOrder: number; onClose: () => void }) {
  const { t } = useTranslation();
  const { save } = useServiceGroupMutations();
  const [name, setName] = React.useState(group?.name ?? "");
  const server = fieldErrors(t, save.error);
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogTitle>{group ? t("catalog.editGroup") : t("catalog.newGroup")}</DialogTitle>
        <form
          className="mt-5 space-y-5"
          onSubmit={(e) => {
            e.preventDefault();
            save.mutate(
              { id: group?.id, body: { name, sortOrder: group?.sortOrder ?? nextOrder, version: group?.version } },
              { onSuccess: () => { toast.success(t("common.saved")); onClose(); } },
            );
          }}
        >
          <Field label={t("catalog.groupName")} error={server.name} hint={t("catalog.groupNameHint")}>
            <Input autoFocus value={name} maxLength={100} onChange={(e) => setName(e.target.value)} />
          </Field>
          <FormError error={save.error} />
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={onClose}>{t("common.cancel")}</Button>
            <Button type="submit" disabled={save.isPending || !name.trim()}>{t("common.save")}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function ServiceDialog({ service, groupId, groups, onClose }: { service?: ServiceItem; groupId?: string; groups: ServiceGroup[]; onClose: () => void }) {
  const { t } = useTranslation();
  const { save } = useServiceMutations();
  const doctors = useDoctors();
  const [group, setGroup] = React.useState(service?.groupId ?? groupId ?? groups[0]?.id ?? "");
  const [code, setCode] = React.useState(service?.code ?? "");
  const [name, setName] = React.useState(service?.name ?? "");
  const [price, setPrice] = React.useState<number | null>(service?.price ?? null);
  const [perDoctor, setPerDoctor] = React.useState(service?.perDoctor ?? false);
  const [active, setActive] = React.useState(service?.active ?? true);
  const [doctorPrices, setDoctorPrices] = React.useState<Record<string, number | null>>(
    Object.fromEntries((service?.doctorPrices ?? []).map((d) => [d.staffId, d.price])),
  );
  const server = fieldErrors(t, save.error);
  const sum = t("common.sum");

  // Doctors who left keep their saved price (shown, editable, removable) so saving never drops data silently.
  const doctorRows = React.useMemo(() => {
    const list = (doctors.data ?? []).map((d) => ({ id: d.id, fullName: d.fullName, former: false }));
    const known = new Set(list.map((d) => d.id));
    for (const id of Object.keys(doctorPrices)) if (!known.has(id)) list.push({ id, fullName: t("catalog.formerDoctor"), former: true });
    return list;
  }, [doctors.data, doctorPrices, t]);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const prices = perDoctor
      ? Object.entries(doctorPrices).flatMap(([staffId, p]) => (p === null || p === undefined ? [] : [{ staffId, price: p }]))
      : [];
    save.mutate(
      {
        id: service?.id,
        body: { groupId: group, code: code.trim() || undefined, name, price: price ?? undefined, perDoctor, doctorPrices: prices, active, version: service?.version },
      },
      { onSuccess: () => { toast.success(t("common.saved")); onClose(); } },
    );
  };

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent wide className="max-w-2xl">
        <DialogTitle>{service ? t("catalog.editService") : t("catalog.newService")}</DialogTitle>
        <form className="mt-5 space-y-5" onSubmit={submit}>
          <div className="grid gap-4 sm:grid-cols-[1fr_9rem]">
            <Field label={t("catalog.serviceName")} error={server.name}>
              <Input autoFocus value={name} maxLength={200} onChange={(e) => setName(e.target.value)} />
            </Field>
            <Field label={t("catalog.code")} optional error={server.code}>
              <Input value={code} maxLength={20} onChange={(e) => setCode(e.target.value)} className="font-mono" />
            </Field>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label={t("catalog.group")} error={server.groupId}>
              <Select value={group} onChange={(e) => setGroup(e.target.value)}>
                {groups.map((g) => (
                  <option key={g.id} value={g.id}>{g.name}</option>
                ))}
              </Select>
            </Field>
            <Field label={perDoctor ? t("catalog.basePrice") : t("catalog.price")} error={server.price}>
              <MoneyInput value={price} onValueChange={setPrice} suffix={sum} />
            </Field>
          </div>

          <label className="flex cursor-pointer items-start gap-3 rounded-lg border border-border p-3">
            <input type="checkbox" className="mt-1 size-4 accent-primary" checked={perDoctor} onChange={(e) => setPerDoctor(e.target.checked)} />
            <span>
              <span className="block text-sm font-medium">{t("catalog.perDoctor")}</span>
              <span className="block text-xs text-muted-foreground">{t("catalog.perDoctorHint")}</span>
            </span>
          </label>

          {perDoctor && (
            <div className="rounded-lg border border-border">
              <div className="border-b border-border px-4 py-2 text-sm font-medium">{t("catalog.doctorPrices")}</div>
              {doctors.isPending ? (
                <Spinner />
              ) : doctorRows.length === 0 ? (
                <p className="px-4 py-3 text-sm text-muted-foreground">{t("catalog.noDoctors")}</p>
              ) : (
                <ul className="max-h-72 divide-y divide-border overflow-y-auto">
                  {doctorRows.map((d) => (
                    <li key={d.id} className="flex items-center gap-3 px-4 py-2">
                      <span className={cn("flex-1 text-sm", d.former && "italic text-muted-foreground")}>{d.fullName}</span>
                      <div className="w-44">
                        <MoneyInput
                          aria-label={d.fullName}
                          value={doctorPrices[d.id] ?? null}
                          placeholder={price !== null ? formatAmount(price) : undefined}
                          onValueChange={(v) => setDoctorPrices((m) => ({ ...m, [d.id]: v }))}
                          suffix={sum}
                        />
                      </div>
                    </li>
                  ))}
                </ul>
              )}
              <p className="border-t border-border px-4 py-2 text-xs text-muted-foreground">{t("catalog.doctorPricesHint")}</p>
            </div>
          )}

          <label className="flex cursor-pointer items-center gap-3 text-sm">
            <input type="checkbox" className="size-4 accent-primary" checked={active} onChange={(e) => setActive(e.target.checked)} />
            {t("catalog.activeLabel")}
          </label>
          <FormError error={save.error} />
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={onClose}>{t("common.cancel")}</Button>
            <Button type="submit" disabled={save.isPending || !name.trim() || price === null || !group}>{t("common.save")}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

// ---------------------------------------------------------------- discounts

function DiscountsTab() {
  const { t } = useTranslation();
  const { data, isPending, error, refetch } = useDiscountTypes();
  const { remove } = useDiscountMutations();
  const [editing, setEditing] = React.useState<DiscountType | "new" | null>(null);
  const [deleting, setDeleting] = React.useState<DiscountType | null>(null);

  if (isPending) return <Spinner />;
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />;

  return (
    <>
      <div className="mb-5 flex items-center gap-3">
        <p className="flex-1 text-sm text-muted-foreground">{t("catalog.discountsHint")}</p>
        <Button onClick={() => setEditing("new")}>
          <Plus /> {t("catalog.newDiscount")}
        </Button>
      </div>
      <Card>
        {data.length === 0 ? (
          <EmptyState text={t("catalog.noDiscounts")} />
        ) : (
          <ul className="divide-y divide-border">
            {data.map((d) => (
              <li key={d.id} className={cn("flex items-center gap-4 px-5 py-3", !d.active && "opacity-60")}>
                <div className="flex size-10 items-center justify-center rounded-full bg-accent-warm text-sm font-semibold">
                  <Percent className="size-4" aria-hidden />
                </div>
                <div className="flex-1 font-medium">
                  {d.name} {!d.active && <Badge tone="gray" className="ml-2">{t("catalog.inactive")}</Badge>}
                </div>
                <div className="text-lg font-semibold tabular-nums">{d.percent}%</div>
                <Button size="icon" variant="ghost" aria-label={t("common.edit")} onClick={() => setEditing(d)}>
                  <Pencil />
                </Button>
                <Button size="icon" variant="ghost" aria-label={t("common.delete")} onClick={() => setDeleting(d)}>
                  <Trash2 />
                </Button>
              </li>
            ))}
          </ul>
        )}
      </Card>
      {editing && <DiscountDialog discount={editing === "new" ? undefined : editing} onClose={() => setEditing(null)} />}
      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(o) => !o && setDeleting(null)}
        title={deleting ? t("catalog.confirmDeleteDiscount", { name: deleting.name }) : ""}
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

function DiscountDialog({ discount, onClose }: { discount?: DiscountType; onClose: () => void }) {
  const { t } = useTranslation();
  const { save } = useDiscountMutations();
  const [name, setName] = React.useState(discount?.name ?? "");
  const [percent, setPercent] = React.useState(discount ? String(discount.percent) : "");
  const [active, setActive] = React.useState(discount?.active ?? true);
  const server = fieldErrors(t, save.error);
  const value = percent === "" ? undefined : Number(percent);
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogTitle>{discount ? t("catalog.editDiscount") : t("catalog.newDiscount")}</DialogTitle>
        <form
          className="mt-5 space-y-5"
          onSubmit={(e) => {
            e.preventDefault();
            save.mutate(
              { id: discount?.id, body: { name, percent: value, active, version: discount?.version } },
              { onSuccess: () => { toast.success(t("common.saved")); onClose(); } },
            );
          }}
        >
          <Field label={t("catalog.discountName")} error={server.name} hint={t("catalog.discountNameHint")}>
            <Input autoFocus value={name} maxLength={100} onChange={(e) => setName(e.target.value)} />
          </Field>
          <Field label={t("catalog.percent")} error={server.percent}>
            <div className="relative w-32">
              <Input
                inputMode="numeric"
                value={percent}
                onChange={(e) => setPercent(e.target.value.replace(/\D/g, "").slice(0, 3))}
                className="pr-8 text-right tabular-nums"
              />
              <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-sm text-muted-foreground">%</span>
            </div>
          </Field>
          <label className="flex cursor-pointer items-center gap-3 text-sm">
            <input type="checkbox" className="size-4 accent-primary" checked={active} onChange={(e) => setActive(e.target.checked)} />
            {t("catalog.activeLabel")}
          </label>
          <FormError error={save.error} />
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={onClose}>{t("common.cancel")}</Button>
            <Button type="submit" disabled={save.isPending || !name.trim() || value === undefined}>{t("common.save")}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
