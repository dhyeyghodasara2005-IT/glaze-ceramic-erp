import { Tag } from 'antd'
import { useTranslation } from 'react-i18next'
import type { SalesOrderStatus, SalesOrderType } from '../../api/types'

/** Draft = amber, Confirmed = green, Cancelled = red: the same pills as the store documents. */
export function SalesStatusTag({ status }: { status: SalesOrderStatus }) {
  const { t } = useTranslation()
  const pill = status === 'CONFIRMED' ? 'doc-posted' : `doc-${status.toLowerCase()}`
  return (
    <span className={`status-pill ${pill}`}>
      <span className="status-dot" />
      {t(`sales.status.${status}`)}
    </span>
  )
}

/** Jobwork orders stand out: their bill goes to the principal, not to the name on the truck. */
export function OrderTypeTag({ type }: { type: SalesOrderType }) {
  const { t } = useTranslation()
  return <Tag color={type === 'JOBWORK' ? 'purple' : undefined} bordered={false}>{t(`sales.type.${type}`)}</Tag>
}
