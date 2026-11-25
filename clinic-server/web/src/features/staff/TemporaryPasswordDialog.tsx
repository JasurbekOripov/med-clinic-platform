import { useTranslation } from "react-i18next";
import { CopyButton } from "@medclinic/web-ui/components/Common";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogTitle } from "@medclinic/web-ui/components/ui/dialog";
import type { StaffWithPassword } from "@/api/types";

export function TemporaryPasswordDialog({ result, onClose }: { result: StaffWithPassword | null; onClose: () => void }) {
  const { t } = useTranslation();
  return (
    <Dialog open={result !== null} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogTitle>{t("staff.temporaryPassword")}</DialogTitle>
        <DialogDescription>{result && t("staff.temporaryPasswordHint", { name: result.staff.fullName })}</DialogDescription>
        {result && (
          <div className="mt-5 rounded-xl border-2 border-dashed border-primary/40 bg-primary/5 p-6 text-center">
            <div className="text-sm text-muted-foreground">{result.staff.login}</div>
            <div className="mt-1 select-all font-mono text-3xl font-bold tracking-wider" data-testid="temporary-password">
              {result.temporaryPassword}
            </div>
            <CopyButton value={result.temporaryPassword} className="mt-4" />
          </div>
        )}
        <DialogFooter>
          <Button onClick={onClose}>{t("common.close")}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
