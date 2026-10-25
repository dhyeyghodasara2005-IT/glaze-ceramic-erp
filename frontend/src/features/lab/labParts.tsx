import { Tag } from 'antd'
import { CheckCircleFilled, CloseCircleFilled } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import type { LabVerdict } from '../../api/types'
import { fmt } from '../dashboard/charts'

/** "≤ 8 %", "2 – 8 %", "≥ 2 %", or nothing when the plant has no range for it. */
export function rangeText(min?: number, max?: number, unit?: string) {
  const u = unit ? ` ${unit}` : ''
  if (min != null && max != null) return `${fmt(min, 3)} – ${fmt(max, 3)}${u}`
  if (max != null) return `≤ ${fmt(max, 3)}${u}`
  if (min != null) return `≥ ${fmt(min, 3)}${u}`
  return undefined
}

export function within(value: number, min?: number, max?: number) {
  return (min == null || value >= min) && (max == null || value <= max)
}

export function VerdictTag({ verdict }: { verdict: LabVerdict }) {
  const { t } = useTranslation()
  return verdict === 'ACCEPTED'
    ? <Tag color="green" bordered={false} icon={<CheckCircleFilled />}>{t('lab.verdict.ACCEPTED')}</Tag>
    : <Tag color="red" bordered={false} icon={<CloseCircleFilled />}>{t('lab.verdict.REJECTED')}</Tag>
}
