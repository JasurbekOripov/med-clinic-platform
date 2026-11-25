import { useMe } from "@/api/hooks";

/** UI-level checks only (hide what the user cannot use); the server enforces every permission. */
export function usePermissions() {
  const { data: me } = useMe();
  const granted = new Set(me?.permissions ?? []);
  return {
    me,
    can: (...permissions: string[]) => permissions.every((p) => granted.has(p)),
  };
}
