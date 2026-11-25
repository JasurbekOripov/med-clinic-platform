import { RefreshCw, Upload } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { FormError } from "@medclinic/web-ui/components/Common";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { formatDateTime } from "@medclinic/web-ui/lib/format";
import { fieldErrors } from "@medclinic/web-ui/lib/errors";
import { useLicense, useLicenseSync, useLicenseUpload, useVendorLink } from "@/api/hooks";

/** License details, "check now" and the offline `.lic` upload (Admin only). */
export function LicensePanel() {
  const { t } = useTranslation();
  const { data: license } = useLicense();
  const link = useVendorLink(true);
  const sync = useLicenseSync();
  const upload = useLicenseUpload();
  const fileInput = React.useRef<HTMLInputElement>(null);
  const uploadErrors = fieldErrors(t, upload.error);

  return (
    <div className="space-y-6">
      <dl className="grid gap-x-8 gap-y-3 text-sm sm:grid-cols-2">
        <Row label={t("common.status")} value={license && t(`license.state.${license.state}`)} />
        <Row label={t("license.plan")} value={license?.plan} />
        <Row label={t("license.validUntil")} value={formatDateTime(license?.validUntil)} />
        <Row label={t("license.maxUsers")} value={license?.maxUsers?.toString()} />
        <Row label={t("license.clinicCode")} value={link.data?.clinicCode} />
        <Row label={t("license.branchCode")} value={link.data?.branchCode} />
        <Row label={t("license.lastSuccess")} value={link.data?.lastSuccessAt ? formatDateTime(link.data.lastSuccessAt) : t("license.never")} />
        {link.data?.lastErrorCode && <Row label={t("license.lastError")} value={t(`errors.${link.data.lastErrorCode}`, { defaultValue: link.data.lastErrorCode })} />}
      </dl>
      <div className="flex flex-wrap gap-3">
        <Button
          variant="secondary"
          loading={sync.isPending}
          onClick={() => sync.mutate(undefined, { onSuccess: () => toast.success(t("license.checked")) })}
        >
          <RefreshCw /> {t("license.checkNow")}
        </Button>
        <input
          ref={fileInput}
          type="file"
          accept=".lic,application/json"
          className="hidden"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) upload.mutate(file, { onSuccess: () => toast.success(t("common.saved")) });
            e.target.value = "";
          }}
        />
        <Button variant="secondary" loading={upload.isPending} onClick={() => fileInput.current?.click()}>
          <Upload /> {t("license.upload")}
        </Button>
      </div>
      <p className="text-sm text-muted-foreground">{t("license.uploadHint")}</p>
      {uploadErrors.file ? <p className="text-sm font-medium text-danger" role="alert">{uploadErrors.file}</p> : <FormError error={upload.error} />}
    </div>
  );
}

function Row({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div className="flex justify-between gap-4 border-b border-border/70 pb-2">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="text-right font-medium">{value || "—"}</dd>
    </div>
  );
}
