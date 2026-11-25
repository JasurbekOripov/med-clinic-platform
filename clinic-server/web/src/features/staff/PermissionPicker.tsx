import { useTranslation } from "react-i18next";
import { cn } from "@medclinic/web-ui/lib/utils";
import { usePermissionCatalog } from "@/api/hooks";

/**
 * Permissions grouped by area as large checkable rows. Optionally shows, per permission, whether it comes from the
 * role or was added/removed for this person.
 */
export function PermissionPicker({
  selected,
  onToggle,
  fromRole,
  disabled = false,
}: {
  selected: Set<string>;
  onToggle: (permission: string, checked: boolean) => void;
  fromRole?: Set<string>;
  disabled?: boolean;
}) {
  const { t } = useTranslation();
  const { data: groups } = usePermissionCatalog();
  return (
    <div className="space-y-5">
      {groups?.map((group) => (
        <fieldset key={group.group}>
          <legend className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t(`permissionGroup.${group.group}`)}</legend>
          <div className="grid gap-2 sm:grid-cols-2">
            {group.permissions.map((p) => {
              const checked = selected.has(p);
              const inRole = fromRole?.has(p) ?? false;
              const note = fromRole ? (inRole && checked ? "fromRole" : !inRole && checked ? "granted" : inRole && !checked ? "denied" : null) : null;
              return (
                <label
                  key={p}
                  className={cn(
                    "flex cursor-pointer items-start gap-3 rounded-lg border border-border px-3 py-2.5 text-sm transition-colors",
                    checked && "border-primary/50 bg-primary/6",
                    disabled && "cursor-default opacity-70",
                  )}
                >
                  <input
                    type="checkbox"
                    className="mt-0.5 size-4 accent-[var(--color-primary)]"
                    checked={checked}
                    disabled={disabled}
                    onChange={(e) => onToggle(p, e.target.checked)}
                  />
                  <span className="flex-1">
                    {t(`permission.${p}`, { defaultValue: p })}
                    {note && (
                      <span className={cn("ml-2 text-xs", note === "denied" ? "text-danger" : note === "granted" ? "text-primary" : "text-muted-foreground")}>
                        ({t(`staff.${note}`)})
                      </span>
                    )}
                  </span>
                </label>
              );
            })}
          </div>
        </fieldset>
      ))}
    </div>
  );
}
