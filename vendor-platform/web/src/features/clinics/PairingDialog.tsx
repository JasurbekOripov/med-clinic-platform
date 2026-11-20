import { AlertTriangle, KeyRound } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { useCancelPairingCode, useCreatePairingCode, useDevices, usePairingCodes, useRevokeDevice } from "@/api/hooks";
import type { Branch, Device, PairingCode } from "@/api/types";
import { CopyButton, EmptyState, ErrorState, FormError, Spinner } from "@/components/Common";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { Field } from "@/components/Field";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Table, Td, Th, Tr } from "@/components/ui/table";
import { usePermissions } from "@/features/auth/permissions";
import { formatDateTime } from "@/lib/format";

const STATE_TONE = { OPEN: "blue", CLAIMED: "green", CANCELLED: "gray", EXPIRED: "gray" } as const;

export function PairingDialog({ branch, open, onOpenChange }: { branch: Branch; open: boolean; onOpenChange: (open: boolean) => void }) {
  const { t } = useTranslation();
  const codes = usePairingCodes(branch.id, open);
  const devices = useDevices(branch.id, open);
  const create = useCreatePairingCode();
  const cancel = useCancelPairingCode(branch.id);
  const [created, setCreated] = React.useState<PairingCode | null>(null);
  const hasActiveServer = devices.data?.some((d) => d.status === "ACTIVE") ?? false;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent wide>
        <DialogTitle>{t("pairing.title", { branch: `${branch.name} (${branch.code})` })}</DialogTitle>
        {hasActiveServer && (
          <p className="mt-3 flex items-start gap-2 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900">
            <AlertTriangle className="mt-0.5 size-4 shrink-0" /> {t("pairing.replaceWarning")}
          </p>
        )}

        {created?.code ? (
          <div className="mt-5 rounded-xl border-2 border-dashed border-primary/40 bg-primary/5 p-6 text-center">
            <div className="select-all font-mono text-4xl font-bold tracking-[0.15em]" data-testid="pairing-code">
              {created.code}
            </div>
            <p className="mt-3 text-sm text-muted-foreground">{t("pairing.hint", { time: formatDateTime(created.expiresAt) })}</p>
            <p className="mt-1 text-sm font-medium text-amber-800">{t("pairing.oneTime")}</p>
            <div className="mt-4 flex justify-center gap-2">
              <CopyButton value={created.code} />
              <Button variant="secondary" size="sm" loading={create.isPending} onClick={() => create.mutate(branch.id, { onSuccess: setCreated })}>
                {t("pairing.regenerate")}
              </Button>
            </div>
          </div>
        ) : (
          <div className="mt-5 flex justify-center">
            <Button size="lg" autoFocus loading={create.isPending} onClick={() => create.mutate(branch.id, { onSuccess: setCreated })}>
              <KeyRound /> {t("pairing.generate")}
            </Button>
          </div>
        )}
        <FormError error={create.error} />

        <h3 className="mt-6 mb-2 text-sm font-semibold">{t("pairing.history")}</h3>
        {codes.isPending ? (
          <Spinner className="py-4" />
        ) : codes.error ? (
          <ErrorState error={codes.error} />
        ) : codes.data.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t("pairing.empty")}</p>
        ) : (
          <Table>
            <tbody>
              {codes.data.map((c) => (
                <Tr key={c.id}>
                  <Td>
                    <Badge tone={STATE_TONE[c.state]}>{t(`pairingState.${c.state}`)}</Badge>
                  </Td>
                  <Td className="text-xs text-muted-foreground">
                    {formatDateTime(c.createdAt)} → {t("pairing.expiresAt")}: {formatDateTime(c.expiresAt)}
                  </Td>
                  <Td className="text-right">
                    {c.state === "OPEN" && (
                      <Button size="sm" variant="ghost" loading={cancel.isPending && cancel.variables === c.id} onClick={() => cancel.mutate(c.id)}>
                        {t("pairing.cancelCode")}
                      </Button>
                    )}
                  </Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}
      </DialogContent>
    </Dialog>
  );
}

export function DevicesDialog({ branch, open, onOpenChange }: { branch: Branch; open: boolean; onOpenChange: (open: boolean) => void }) {
  const { t } = useTranslation();
  const { can } = usePermissions();
  const { data, isPending, error } = useDevices(branch.id, open);
  const revoke = useRevokeDevice(branch.id);
  const [target, setTarget] = React.useState<Device | null>(null);
  const [reason, setReason] = React.useState("");

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent wide className="max-w-4xl">
        <DialogTitle>
          {t("branches.devices")} — {branch.name}
        </DialogTitle>
        <DialogDescription>{branch.code}</DialogDescription>
        <div className="mt-4">
          {isPending ? (
            <Spinner />
          ) : error ? (
            <ErrorState error={error} />
          ) : data.length === 0 ? (
            <EmptyState text={t("devices.empty")} />
          ) : (
            <Table>
              <thead>
                <tr>
                  <Th>{t("devices.hostname")}</Th>
                  <Th>{t("common.status")}</Th>
                  <Th>{t("devices.pairedAt")}</Th>
                  <Th>{t("devices.lastSeen")}</Th>
                  <Th />
                </tr>
              </thead>
              <tbody>
                {data.map((d) => (
                  <Tr key={d.id}>
                    <Td>
                      <div className="font-medium">{d.hostname}</div>
                      <div className="text-xs text-muted-foreground">
                        {d.os} · v{d.appVersion}
                        {d.lastIp ? ` · ${d.lastIp}` : ""}
                      </div>
                    </Td>
                    <Td>
                      <Badge tone={d.status === "ACTIVE" ? "green" : "gray"}>{t(`deviceStatus.${d.status}`)}</Badge>
                      {d.revokeReason && <div className="mt-1 text-xs text-muted-foreground">{d.revokeReason}</div>}
                    </Td>
                    <Td>{formatDateTime(d.pairedAt)}</Td>
                    <Td>{formatDateTime(d.lastSeenAt)}</Td>
                    <Td className="text-right">
                      {can("DEVICE_REVOKE") && d.status === "ACTIVE" && (
                        <Button
                          size="sm"
                          variant="danger"
                          onClick={() => {
                            setReason("");
                            setTarget(d);
                          }}
                        >
                          {t("devices.revoke")}
                        </Button>
                      )}
                    </Td>
                  </Tr>
                ))}
              </tbody>
            </Table>
          )}
        </div>
        <ConfirmDialog
          open={target !== null}
          onOpenChange={(o) => !o && setTarget(null)}
          title={target ? t("devices.confirmRevoke", { name: target.hostname }) : ""}
          confirmLabel={t("devices.revoke")}
          danger
          onConfirm={async () => {
            if (!target) return;
            await revoke.mutateAsync({ id: target.id, version: target.version, reason });
            toast.success(t("devices.revoked"));
          }}
        >
          <Field label={t("common.reason")} optional className="mt-4">
            <Input autoFocus value={reason} maxLength={200} onChange={(e) => setReason(e.target.value)} />
          </Field>
        </ConfirmDialog>
      </DialogContent>
    </Dialog>
  );
}
