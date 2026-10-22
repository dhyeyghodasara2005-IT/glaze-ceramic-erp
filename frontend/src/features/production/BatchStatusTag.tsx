import { useTranslation } from 'react-i18next'
import type { BodyBatchStatus } from '../../api/types'

/** In mill = blue, slip ready = teal, sprayed = grey-green, draft = amber, cancelled = red. */
export default function BatchStatusTag({ status }: { status: BodyBatchStatus }) {
  const { t } = useTranslation()
  return (
    <span className={`status-pill batch-${status.toLowerCase()}`}>
      <span className="status-dot" />
      {t(`prod.status.${status}`)}
    </span>
  )
}
