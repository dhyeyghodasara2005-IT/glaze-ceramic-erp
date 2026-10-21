import { Segmented } from 'antd'
import { useTranslation } from 'react-i18next'
import { LANGUAGES } from '../i18n'
import { useAuth } from '../auth/AuthContext'
import type { Language } from '../api/types'

/** EN | ગુ toggle. When logged in, the choice is also saved to the user's profile on the server. */
export default function LanguageSwitch() {
  const { i18n } = useTranslation()
  const { setLanguage } = useAuth()
  return (
    <Segmented
      size="small"
      value={i18n.language}
      options={LANGUAGES.map((l) => ({ value: l.value, label: l.short, title: l.label }))}
      onChange={(value) => setLanguage(value as Language)}
    />
  )
}
