import { useTranslation } from "react-i18next";

/** Template roles are shown translated; custom roles show their own name. */
export function RoleLabel({ template, name }: { template?: string | null; name?: string | null }) {
  const { t } = useTranslation();
  return <>{name ?? (template ? t(`roleTemplate.${template}`) : "—")}</>;
}

export function roleText(t: (key: string) => string, role: { template?: string | null; name?: string | null }): string {
  return role.name ?? (role.template ? t(`roleTemplate.${role.template}`) : "—");
}
