import { useTranslation } from 'react-i18next'

/** Soft pill with a coloured dot: green = active, grey = inactive. */
export default function StatusTag({ active }: { active: boolean }) {
  const { t } = useTranslation()
  return (
    <span className={`status-pill ${active ? 'on' : 'off'}`}>
      <span className="status-dot" />
      {active ? t('common.active') : t('common.inactive')}
    </span>
  )
}
