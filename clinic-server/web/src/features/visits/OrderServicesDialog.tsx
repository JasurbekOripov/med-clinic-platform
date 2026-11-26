import { Plus, Search, Stethoscope, X } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { FormError, Spinner } from "@medclinic/web-ui/components/Common";
import { Field } from "@medclinic/web-ui/components/Field";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogTitle } from "@medclinic/web-ui/components/ui/dialog";
import { Input, Select } from "@medclinic/web-ui/components/ui/input";
import { formatAmount } from "@medclinic/web-ui/lib/money";
import { cn } from "@medclinic/web-ui/lib/utils";
import { useAddVisitService, useAgents, useCreateVisit, useDiscountTypes, useDoctors, useServiceGroups, useServices } from "@/api/hooks";
import type { ServiceItem, Visit } from "@/api/types";
import { matchesSearch } from "@/features/catalog/search";
import { discountOf, priceFor } from "./pricing";

type Line = { key: number; service: ServiceItem; doctorId: string };

/**
 * Reception picks services for a patient: type to filter, Enter or click to add. Consultations need a doctor (the
 * doctor's own price applies). Creates a new visit, or adds the services to [visit].
 */
export function OrderServicesDialog({ patientId, visit, onClose }: { patientId: string; visit?: Visit; onClose: () => void }) {
  const { t } = useTranslation();
  const services = useServices();
  const groups = useServiceGroups();
  const doctors = useDoctors();
  const discounts = useDiscountTypes();
  const agents = useAgents();
  const create = useCreateVisit();
  const add = useAddVisitService();
  const [text, setText] = React.useState("");
  const [lines, setLines] = React.useState<Line[]>([]);
  const [discountId, setDiscountId] = React.useState(visit?.discount?.typeId ?? "");
  const [agentId, setAgentId] = React.useState(visit?.agent?.id ?? "");
  const nextKey = React.useRef(0);
  const adding = Boolean(visit);

  const groupName = React.useMemo(() => new Map((groups.data ?? []).map((g) => [g.id, g.name])), [groups.data]);
  const matches = React.useMemo(
    () => (services.data ?? []).filter((s) => s.active && matchesSearch(text, s.name, s.code, groupName.get(s.groupId))).slice(0, 50),
    [services.data, text, groupName],
  );
  const percent = adding ? visit?.discount?.percent : discounts.data?.find((d) => d.id === discountId)?.percent;
  const priced = lines.map((l) => {
    const price = priceFor(l.service, l.doctorId || undefined);
    return { ...l, price, discount: discountOf(price, percent) };
  });
  const total = priced.reduce((sum, l) => sum + l.price - l.discount, 0);
  const missingDoctor = lines.some((l) => l.service.perDoctor && !l.doctorId);
  const pending = create.isPending || add.isPending;

  const addLine = (service: ServiceItem) => {
    setLines((ls) => [...ls, { key: nextKey.current++, service, doctorId: "" }]);
    setText("");
  };

  const submit = async () => {
    try {
      if (visit) {
        // One request per service; if one fails, the ones already saved leave the list so a retry cannot add them twice.
        let version = visit.version;
        for (const l of lines) {
          const updated = await add.mutateAsync({ id: visit.id, serviceId: l.service.id, doctorId: l.doctorId || undefined, version });
          version = updated.version;
          setLines((ls) => ls.filter((x) => x.key !== l.key));
        }
      } else {
        await create.mutateAsync({
          patientId,
          discountTypeId: discountId || undefined,
          agentId: agentId || undefined,
          services: lines.map((l) => ({ serviceId: l.service.id, doctorId: l.doctorId || undefined })),
        });
      }
      toast.success(t("visits.saved"));
      onClose();
    } catch {
      // shown by FormError below
    }
  };

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent wide className="max-w-5xl">
        <DialogTitle>{adding ? t("visits.addServices", { no: visit!.visitNo }) : t("visits.new")}</DialogTitle>
        {services.isPending || groups.isPending ? (
          <Spinner />
        ) : (
          <div className="mt-5 grid gap-5 md:grid-cols-[1fr_1.1fr]">
            <div className="flex min-h-0 flex-col">
              <div className="relative mb-2">
                <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
                <Input
                  autoFocus
                  type="search"
                  value={text}
                  onChange={(e) => setText(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" && matches[0]) {
                      e.preventDefault();
                      addLine(matches[0]);
                    }
                  }}
                  placeholder={t("visits.searchService")}
                  className="pl-9"
                />
              </div>
              <ul className="max-h-96 overflow-y-auto rounded-lg border border-border" aria-label={t("catalog.services")}>
                {matches.length === 0 ? (
                  <li className="px-4 py-6 text-center text-sm text-muted-foreground">{t("visits.noServiceFound")}</li>
                ) : (
                  matches.map((s, i) => (
                    <li key={s.id}>
                      <button
                        type="button"
                        onClick={() => addLine(s)}
                        className={cn("flex w-full items-center gap-3 px-4 py-2.5 text-left hover:bg-muted", i === 0 && text && "bg-primary/5")}
                      >
                        <Plus className="size-4 shrink-0 text-primary" aria-hidden />
                        <span className="min-w-0 flex-1">
                          <span className="block truncate text-sm font-medium">{s.name}</span>
                          <span className="block text-xs text-muted-foreground">{groupName.get(s.groupId)}</span>
                        </span>
                        <span className="text-sm tabular-nums">
                          {s.perDoctor && <Stethoscope className="mr-1 inline size-3.5 text-muted-foreground" aria-label={t("catalog.perDoctor")} />}
                          {formatAmount(s.price)}
                        </span>
                      </button>
                    </li>
                  ))
                )}
              </ul>
            </div>

            <div className="flex flex-col gap-4">
              <div className="rounded-lg border border-border">
                <div className="border-b border-border px-4 py-2 text-sm font-medium">{t("visits.chosen", { count: lines.length })}</div>
                {lines.length === 0 ? (
                  <p className="px-4 py-6 text-center text-sm text-muted-foreground">{t("visits.chooseHint")}</p>
                ) : (
                  <ul className="max-h-72 divide-y divide-border overflow-y-auto">
                    {priced.map((l) => (
                      <li key={l.key} className="flex items-center gap-3 px-4 py-2">
                        <div className="min-w-0 flex-1">
                          <div className="truncate text-sm font-medium">{l.service.name}</div>
                          {l.service.perDoctor && (
                            <Select
                              aria-label={t("visits.doctor")}
                              value={l.doctorId}
                              onChange={(e) => setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, doctorId: e.target.value } : x)))}
                              className={cn("mt-1 h-8 text-xs", !l.doctorId && "border-amber-400")}
                            >
                              <option value="">{t("visits.chooseDoctor")}</option>
                              {(doctors.data ?? []).map((d) => (
                                <option key={d.id} value={d.id}>{d.fullName}</option>
                              ))}
                            </Select>
                          )}
                        </div>
                        <div className="text-right text-sm tabular-nums">
                          <div className="font-semibold">{formatAmount(l.price - l.discount)}</div>
                          {l.discount > 0 && <div className="text-xs text-muted-foreground line-through">{formatAmount(l.price)}</div>}
                        </div>
                        <Button
                          type="button"
                          size="icon"
                          variant="ghost"
                          aria-label={t("common.remove")}
                          onClick={() => setLines((ls) => ls.filter((x) => x.key !== l.key))}
                        >
                          <X />
                        </Button>
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              {!adding && (
                <div className="grid gap-4 sm:grid-cols-2">
                  <Field label={t("visits.discount")} optional>
                    <Select value={discountId} onChange={(e) => setDiscountId(e.target.value)}>
                      <option value="">{t("visits.noDiscount")}</option>
                      {(discounts.data ?? []).filter((d) => d.active).map((d) => (
                        <option key={d.id} value={d.id}>{d.name} — {d.percent}%</option>
                      ))}
                    </Select>
                  </Field>
                  <Field label={t("visits.agent")} optional>
                    <Select value={agentId} onChange={(e) => setAgentId(e.target.value)}>
                      <option value="">{t("visits.noAgent")}</option>
                      {(agents.data ?? []).filter((a) => a.active).map((a) => (
                        <option key={a.id} value={a.id}>{a.name}</option>
                      ))}
                    </Select>
                  </Field>
                </div>
              )}

              <div className="mt-auto flex items-baseline justify-between rounded-lg bg-muted px-4 py-3">
                <span className="text-sm text-muted-foreground">{t("visits.toPay")}</span>
                <span className="text-2xl font-semibold tabular-nums">
                  {formatAmount(total)} <span className="text-base font-normal text-muted-foreground">{t("common.sum")}</span>
                </span>
              </div>
            </div>
          </div>
        )}
        <FormError error={create.error ?? add.error} />
        <DialogFooter>
          <Button type="button" variant="ghost" onClick={onClose}>{t("common.cancel")}</Button>
          <Button type="button" size="lg" onClick={() => void submit()} disabled={pending || lines.length === 0 || missingDoctor}>
            {adding ? t("visits.addToVisit") : t("visits.create")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
