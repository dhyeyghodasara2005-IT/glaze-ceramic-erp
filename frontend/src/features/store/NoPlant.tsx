import { useTranslation } from 'react-i18next'
import EmptyState from '../../components/EmptyState'

/** Shown on store screens when the user has no plant (stock always belongs to a plant). */
export default function NoPlant() {
  const { t } = useTranslation()
  return (
    <div className="panel">
      <EmptyState title={t('common.noPlant')} hint={t('store.noPlant')} />
    </div>
  )
}
