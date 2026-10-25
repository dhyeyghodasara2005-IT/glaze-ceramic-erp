import { Tag } from 'antd'
import { useTranslation } from 'react-i18next'
import type { BreakdownCategory, BreakdownStatus } from '../../api/types'

/** Down = red (with a pulsing dot), running again = green, cancelled = gray: the same pills as everywhere else. */
export function BreakdownStatusTag({ status }: { status: BreakdownStatus }) {
  const { t } = useTranslation()
  const pill = status === 'OPEN' ? 'bd-down' : status === 'CLOSED' ? 'doc-posted' : 'doc-cancelled'
  return (
    <span className={`status-pill ${pill}`}>
      <span className="status-dot" />
      {t(`maint.status.${status}`)}
    </span>
  )
}

export function CategoryTag({ category }: { category?: BreakdownCategory }) {
  const { t } = useTranslation()
  if (!category) return null
  return <Tag bordered={false}>{t(`maint.categories.${category}`)}</Tag>
}

/** 95 -> "1 h 35 m" */
export function minutesText(minutes?: number | null) {
  if (minutes == null) return '–'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return h > 0 ? `${h} h ${String(m).padStart(2, '0')} m` : `${m} m`
}
