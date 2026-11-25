import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import { safeStorage } from "@medclinic/web-ui/lib/storage";
import ru from "./locales/ru.json";
import uzCyrl from "./locales/uz-Cyrl.json";
import uzLatn from "./locales/uz-Latn.json";

export const LANGUAGES = [
  { code: "uz-Latn", label: "Oʻzbekcha" },
  { code: "uz-Cyrl", label: "Ўзбекча" },
  { code: "ru", label: "Русский" },
] as const;

const STORAGE_KEY = "clinic.lang";
const DEFAULT_LANGUAGE = "uz-Latn";

function initialLanguage(): string {
  const stored = safeStorage.get(STORAGE_KEY);
  return LANGUAGES.some((l) => l.code === stored) ? (stored as string) : DEFAULT_LANGUAGE;
}

void i18n.use(initReactI18next).init({
  resources: { "uz-Latn": { translation: uzLatn }, "uz-Cyrl": { translation: uzCyrl }, ru: { translation: ru } },
  lng: initialLanguage(),
  fallbackLng: DEFAULT_LANGUAGE,
  interpolation: { escapeValue: false },
  returnNull: false,
});

document.documentElement.lang = i18n.language;
i18n.on("languageChanged", (lng) => {
  safeStorage.set(STORAGE_KEY, lng);
  document.documentElement.lang = lng;
});

export default i18n;
