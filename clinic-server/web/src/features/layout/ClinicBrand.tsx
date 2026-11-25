import { Stethoscope } from "lucide-react";
import { usePublicSettings } from "@/api/hooks";
import { cn } from "@medclinic/web-ui/lib/utils";

/** Clinic logo (or a friendly default mark) and name. */
export function ClinicBrand({ size = "md", className }: { size?: "md" | "lg"; className?: string }) {
  const { data } = usePublicSettings();
  const box = size === "lg" ? "size-16 rounded-2xl" : "size-10 rounded-xl";
  return (
    <div className={cn("flex items-center gap-3", className)}>
      {data?.hasLogo ? (
        <img src="/api/v1/settings/logo" alt="" className={cn(box, "bg-surface object-contain p-1 shadow-sm")} />
      ) : (
        <div className={cn(box, "flex items-center justify-center bg-primary/12 text-primary")}>
          <Stethoscope className={size === "lg" ? "size-8" : "size-5"} aria-hidden />
        </div>
      )}
      <div className="min-w-0">
        <div className={cn("truncate font-semibold", size === "lg" ? "text-2xl" : "text-base")}>{data?.name ?? "MedClinic"}</div>
      </div>
    </div>
  );
}
