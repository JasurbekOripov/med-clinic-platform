import { ShieldAlert } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Card, CardContent } from "@medclinic/web-ui/components/ui/card";
import type { LicenseStatus } from "@/api/types";
import { usePermissions } from "@/features/auth/permissions";
import { LicensePanel } from "@/features/license/LicensePanel";

/** Shown instead of every work screen while the license is not usable. Calm, clear, and data stays safe. */
export function StopScreen({ license }: { license: LicenseStatus }) {
  const { t } = useTranslation();
  const { can } = usePermissions();
  return (
    <div className="mx-auto max-w-2xl py-10">
      <div className="mb-8 text-center">
        <div className="mx-auto mb-5 flex size-16 items-center justify-center rounded-full bg-amber-100 text-amber-700">
          <ShieldAlert className="size-8" aria-hidden />
        </div>
        <h1 className="text-2xl font-semibold">{t("license.stoppedTitle")}</h1>
        <p className="mt-2 text-lg text-amber-800">{t(`license.state.${license.state}`)}</p>
        <p className="mt-3 text-muted-foreground">{t("license.contactVendor")}</p>
      </div>
      {can("LICENSE_MANAGE") && (
        <Card>
          <CardContent>
            <LicensePanel />
          </CardContent>
        </Card>
      )}
    </div>
  );
}
