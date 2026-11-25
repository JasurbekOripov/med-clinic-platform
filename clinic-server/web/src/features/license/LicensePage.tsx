import { useTranslation } from "react-i18next";
import { PageHeader } from "@medclinic/web-ui/components/Common";
import { Card, CardContent } from "@medclinic/web-ui/components/ui/card";
import { LicensePanel } from "./LicensePanel";

export function LicensePage() {
  const { t } = useTranslation();
  return (
    <>
      <PageHeader title={t("license.title")} />
      <Card className="max-w-3xl">
        <CardContent>
          <LicensePanel />
        </CardContent>
      </Card>
    </>
  );
}
