import { useMe } from "@/api/hooks";
import type { Permission } from "@/api/types";

/** UI-level permission check; the server enforces every permission independently. */
export function usePermissions() {
  const { data: me } = useMe();
  const granted = new Set(me?.permissions ?? []);
  return {
    me,
    can: (permission: Permission) => granted.has(permission),
  };
}
