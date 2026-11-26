import * as React from "react";
import { useTranslation } from "react-i18next";
import { FormError } from "@medclinic/web-ui/components/Common";
import { Field } from "@medclinic/web-ui/components/Field";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogTitle } from "@medclinic/web-ui/components/ui/dialog";
import { Textarea } from "@medclinic/web-ui/components/ui/input";

/** Asks for the reason the audit log keeps (cancelling a service, emergency permission). */
export function ReasonDialog({
  title,
  description,
  confirmLabel,
  danger,
  onConfirm,
  onClose,
}: {
  title: string;
  description?: string;
  confirmLabel: string;
  danger?: boolean;
  onConfirm: (reason: string) => Promise<unknown>;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const [reason, setReason] = React.useState("");
  const [error, setError] = React.useState<unknown>(null);
  const [pending, setPending] = React.useState(false);
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogTitle>{title}</DialogTitle>
        {description && <DialogDescription>{description}</DialogDescription>}
        <form
          className="mt-4 space-y-4"
          onSubmit={async (e) => {
            e.preventDefault();
            setPending(true);
            try {
              await onConfirm(reason.trim());
              onClose();
            } catch (err) {
              setError(err);
            } finally {
              setPending(false);
            }
          }}
        >
          <Field label={t("visits.reason")}>
            <Textarea autoFocus value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} />
          </Field>
          <FormError error={error} />
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={onClose}>{t("common.cancel")}</Button>
            <Button type="submit" variant={danger ? "danger" : "primary"} disabled={pending || !reason.trim()}>{confirmLabel}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
