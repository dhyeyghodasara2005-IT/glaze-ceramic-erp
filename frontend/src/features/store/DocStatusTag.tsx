import { useTranslation } from 'react-i18next'
import type { DocStatus } from '../../api/types'

/** Draft = amber, Posted = green, Cancelled = red. Same pill style as StatusTag. */
export default function DocStatusTag({ status }: { status: DocStatus }) {
  const { t } = useTranslation()
  return (
    <span className={`status-pill doc-${status.toLowerCase()}`}>
      <span className="status-dot" />
      {t(`store.status.${status}`)}
    </span>
  )
}
