import { useTranslation } from 'react-i18next'
import type { LotStatus } from '../../api/types'

/** Running = green, closed = grey, cancelled = red. */
export default function LotStatusTag({ status }: { status: LotStatus }) {
  const { t } = useTranslation()
  return (
    <span className={`status-pill lot-${status.toLowerCase()}`}>
      <span className="status-dot" />
      {t(`press.lotStatus.${status}`)}
    </span>
  )
}
