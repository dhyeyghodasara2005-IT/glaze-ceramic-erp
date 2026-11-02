import { Tag } from 'antd'
import { useTranslation } from 'react-i18next'
import type { AttendanceStatus } from '../../api/types'

/** ₹ 12,450 (paise only when there are some) */
export const rupees = (n?: number | null) =>
  n == null ? '–' : `₹ ${n.toLocaleString('en-IN', { maximumFractionDigits: 2 })}`

/** How each mark looks: present green, half day blue, absent red, leave grey. The letter goes with it, never colour alone. */
export const STATUS_COLOR: Record<AttendanceStatus, string> = {
  PRESENT: 'green',
  HALF: 'blue',
  ABSENT: 'red',
  LEAVE: 'default',
}

export function StatusMark({ status }: { status: AttendanceStatus }) {
  const { t } = useTranslation()
  return <Tag color={STATUS_COLOR[status]} bordered={false}>{t(`labour.status.${status}`)}</Tag>
}
