import * as React from "react";
import { useTranslation } from "react-i18next";
import { FormError } from "./Common";
import { Button } from "./ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogTitle } from "./ui/dialog";

/**
 * Confirmation for state changes. Stays open on failure and shows the translated error,
 * so nothing fails silently.
 */
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel,
  danger = false,
  onConfirm,
  children,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: string;
  confirmLabel: string;
  danger?: boolean;
  onConfirm: () => Promise<unknown>;
  children?: React.ReactNode;
}) {
  const { t } = useTranslation();
  const [pending, setPending] = React.useState(false);
  const [error, setError] = React.useState<unknown>(null);

  React.useEffect(() => {
    if (open) setError(null);
  }, [open]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogTitle>{title}</DialogTitle>
        {description && <DialogDescription>{description}</DialogDescription>}
        <form
          onSubmit={async (e) => {
            e.preventDefault();
            setPending(true);
            setError(null);
            try {
              await onConfirm();
              onOpenChange(false);
            } catch (err) {
              setError(err);
            } finally {
              setPending(false);
            }
          }}
        >
          {children}
          <FormError error={error} />
          <DialogFooter>
            <Button variant="secondary" onClick={() => onOpenChange(false)}>
              {t("common.cancel")}
            </Button>
            <Button type="submit" variant={danger ? "danger" : "primary"} loading={pending} autoFocus={!children}>
              {confirmLabel}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
