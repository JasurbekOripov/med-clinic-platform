import { Sparkles } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { Card } from "@medclinic/web-ui/components/ui/card";
import { usePermissions } from "@/features/auth/permissions";
import { NAV } from "@/features/layout/AppLayout";

/** A warm start page: greeting by time of day (Tashkent) and big tiles for what this person can do. */
export function HomePage() {
  const { t } = useTranslation();
  const { me, can } = usePermissions();
  const tiles = NAV.filter((n) => n.hint && can(...n.needs));
  const hour = Number(new Intl.DateTimeFormat("en-GB", { timeZone: "Asia/Tashkent", hour: "2-digit", hourCycle: "h23" }).format(new Date()));
  const greeting = hour < 12 ? "home.morning" : hour < 18 ? "home.afternoon" : "home.evening";
  const firstName = me?.fullName.split(" ").slice(1, 2)[0] ?? me?.fullName.split(" ")[0] ?? "";

  return (
    <>
      <div className="mb-8">
        <h1 className="text-3xl font-semibold tracking-tight">{t(greeting, { name: firstName })}</h1>
        <p className="mt-1 text-lg text-muted-foreground">{t("home.subtitle")}</p>
      </div>
      {tiles.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {tiles.map((tile) => (
            <Link key={tile.to} to={tile.to} className="group rounded-xl focus-visible:ring-2">
              <Card className="h-full p-6 transition-all group-hover:-translate-y-0.5 group-hover:shadow-md">
                <div className="mb-4 flex size-12 items-center justify-center rounded-xl bg-primary/12 text-primary">
                  <tile.icon className="size-6" aria-hidden />
                </div>
                <div className="text-lg font-semibold">{t(tile.label)}</div>
                <p className="mt-1 text-sm text-muted-foreground">{tile.hint && t(tile.hint)}</p>
              </Card>
            </Link>
          ))}
        </div>
      )}
      <Card className="mt-8 flex items-start gap-4 border-dashed bg-accent-warm/40 p-6">
        <Sparkles className="mt-0.5 size-6 shrink-0 text-amber-600" aria-hidden />
        <div>
          <div className="font-semibold">{t("home.workspaceSoon")}</div>
          <p className="mt-1 text-sm text-muted-foreground">{t("home.workspaceSoonHint")}</p>
        </div>
      </Card>
    </>
  );
}
