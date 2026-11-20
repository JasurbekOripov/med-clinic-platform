import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import { safeStorage } from "@/lib/storage";
import en from "./locales/en.json";
import ru from "./locales/ru.json";
import uzCyrl from "./locales/uz-Cyrl.json";
import uzLatn from "./locales/uz-Latn.json";

export const LANGUAGES = [
  { code: "uz-Latn", label: "Oʻzbekcha" },
  { code: "uz-Cyrl", label: "Ўзбекча" },
  { code: "ru", label: "Русский" },
  { code: "en", label: "English" },
] as const;

export type LanguageCode = (typeof LANGUAGES)[number]["code"];

const STORAGE_KEY = "vendor.lang";
const DEFAULT_LANGUAGE: LanguageCode = "uz-Latn";

function initialLanguage(): LanguageCode {
  const stored = safeStorage.get(STORAGE_KEY);
  return LANGUAGES.some((l) => l.code === stored) ? (stored as LanguageCode) : DEFAULT_LANGUAGE;
}

void i18n.use(initReactI18next).init({
  resources: {
    "uz-Latn": { translation: uzLatn },
    "uz-Cyrl": { translation: uzCyrl },
    ru: { translation: ru },
    en: { translation: en },
  },
  lng: initialLanguage(),
  fallbackLng: DEFAULT_LANGUAGE,
  interpolation: { escapeValue: false },
  returnNull: false,
});

function applyDocumentLanguage(lng: string) {
  document.documentElement.lang = lng;
}

applyDocumentLanguage(i18n.language);
i18n.on("languageChanged", (lng) => {
  safeStorage.set(STORAGE_KEY, lng);
  applyDocumentLanguage(lng);
});

export default i18n;
