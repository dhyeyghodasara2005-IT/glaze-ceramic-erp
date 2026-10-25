import { Tag } from 'antd'
import { useTranslation } from 'react-i18next'
import type { Meter, MeterKind, MeterUnit } from '../../api/types'

const KIND_COLOR: Record<MeterKind, string> = {
  GAS: 'orange', POWER: 'gold', RUN_HOURS: 'default', COAL: 'default', PROPANE: 'cyan', LNG: 'blue', DIESEL: 'purple',
}

/** "m³", "kWh", "L": a unit as it is written after a number. */
export function useUnit() {
  const { t } = useTranslation()
  return (unit?: MeterUnit) => (unit ? t(`energy.unitShort.${unit}`) : '')
}

/** What a meter measures, and in what: "Gas · m³". */
export function KindTag({ meter }: { meter: Pick<Meter, 'kind' | 'unit'> }) {
  const { t } = useTranslation()
  const unit = useUnit()
  return <Tag color={KIND_COLOR[meter.kind]} bordered={false}>{t(`energy.kinds.${meter.kind}`)} · {unit(meter.unit)}</Tag>
}
