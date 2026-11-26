import { CalendarClock, Plus } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { EmptyState, ErrorState, Spinner } from "@medclinic/web-ui/components/Common";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Card } from "@medclinic/web-ui/components/ui/card";
import { usePatientVisits } from "@/api/hooks";
import { usePermissions } from "@/features/auth/permissions";
import { OrderServicesDialog } from "./OrderServicesDialog";
import { type ConclusionAction, VisitCard } from "./VisitCard";

/** Visits on the patient page. What each person sees is decided by the server (unpaid services stay hidden from doctors). */
export function PatientVisits({ patientId, conclusions }: { patientId: string; conclusions?: ConclusionAction }) {
  const { t } = useTranslation();
  const { can } = usePermissions();
  const { data, isPending, error, refetch } = usePatientVisits(patientId);
  const [creating, setCreating] = React.useState(false);

  return (
    <section>
      <div className="mb-3 flex items-center gap-3">
        <h2 className="flex flex-1 items-center gap-2 text-lg font-semibold">
          <CalendarClock className="size-5 text-primary" aria-hidden /> {t("patients.history")}
        </h2>
        {can("VISIT_CREATE") && (
          <Button size="lg" onClick={() => setCreating(true)}>
            <Plus /> {t("visits.new")}
          </Button>
        )}
      </div>
      {isPending ? (
        <Spinner />
      ) : error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : data.length === 0 ? (
        <Card>
          <EmptyState text={t("visits.empty")} />
        </Card>
      ) : (
        <div className="space-y-4">
          {data.map((v) => (
            <VisitCard key={v.id} visit={v} conclusions={conclusions} />
          ))}
        </div>
      )}
      {creating && <OrderServicesDialog patientId={patientId} onClose={() => setCreating(false)} />}
    </section>
  );
}
