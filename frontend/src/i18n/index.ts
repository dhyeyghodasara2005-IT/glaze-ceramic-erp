import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import dayjs from 'dayjs'
import 'dayjs/locale/gu'
import { languageStore } from '../api/client'
import { en } from './en'
import { gu } from './gu'

/** Languages offered in the UI. Hindi can be added later with an hi.ts file. */
export const LANGUAGES = [
  { value: 'en', label: 'English', short: 'EN' },
  { value: 'gu', label: 'ગુજરાતી', short: 'ગુ' },
] as const

const saved = languageStore.get()
const initial = LANGUAGES.some((l) => l.value === saved) ? saved! : 'en'

i18n.use(initReactI18next).init({
  resources: {
    en: { translation: en },
    gu: { translation: gu },
  },
  lng: initial,
  fallbackLng: 'en',
  interpolation: { escapeValue: false }, // React already escapes text
})

function syncLanguage(lng: string) {
  languageStore.set(lng)
  document.documentElement.lang = lng
  dayjs.locale(lng)
}
syncLanguage(initial)
i18n.on('languageChanged', syncLanguage)

export default i18n
