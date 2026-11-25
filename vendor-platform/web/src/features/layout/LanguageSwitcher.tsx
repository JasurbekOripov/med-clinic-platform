import { Languages } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Select } from "@medclinic/web-ui/components/ui/input";
import { LANGUAGES } from "@/i18n";

export function LanguageSwitcher() {
  const { t, i18n } = useTranslation();
  return (
    <label className="inline-flex items-center gap-2 text-sm text-muted-foreground">
      <Languages className="size-4" aria-hidden />
      <span className="sr-only">{t("auth.language")}</span>
      <Select value={i18n.language} onChange={(e) => void i18n.changeLanguage(e.target.value)} className="h-9 w-auto min-w-32 text-foreground">
        {LANGUAGES.map((l) => (
          <option key={l.code} value={l.code}>
            {l.label}
          </option>
        ))}
      </Select>
    </label>
  );
}
