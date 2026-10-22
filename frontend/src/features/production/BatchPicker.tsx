import { Checkbox, Switch } from 'antd'
import { useTranslation } from 'react-i18next'
import { qty } from '../store/format'
import { dayClock } from './time'

export interface PickableBatch {
  id: number
  batchNo?: string
  ballMillCode: string
  chargeTon: number
  slipTankCode?: string
  slipDensity?: number
  slipResidue?: number
  millingEnd?: string
}

export type BatchUse = { bodyBatchId: number; finished: boolean }

/**
 * Pick the body batches a spray dryer run used, and say which ones ran out ("used up").
 * A controlled input (value + onChange), so it plugs straight into a Form.Item - the same idea as
 * PermissionPicker on the roles screen.
 */
export default function BatchPicker({ batches, value = [], onChange, disabled }: {
  batches: PickableBatch[]
  value?: BatchUse[]
  onChange?: (value: BatchUse[]) => void
  disabled?: boolean
}) {
  const { t } = useTranslation()
  const chosen = new Map(value.map((v) => [v.bodyBatchId, v]))

  const toggle = (id: number, on: boolean) =>
    onChange?.(on ? [...value, { bodyBatchId: id, finished: true }] : value.filter((v) => v.bodyBatchId !== id))
  const setFinished = (id: number, finished: boolean) =>
    onChange?.(value.map((v) => (v.bodyBatchId === id ? { ...v, finished } : v)))

  // A posted run shows only what it used.
  const shown = disabled ? batches.filter((b) => chosen.has(b.id)) : batches

  return (
    <div className="batch-picker">
      {shown.map((b) => {
        const use = chosen.get(b.id)
        return (
          <div key={b.id} className={`batch-option${use ? ' selected' : ''}`}>
            <Checkbox checked={!!use} disabled={disabled} onChange={(e) => toggle(b.id, e.target.checked)} aria-label={b.batchNo}>
              <span className="cell-title">{b.batchNo}</span>
              <span className="cell-sub">
                {' '}{b.ballMillCode} → {b.slipTankCode ?? '–'} · {qty(b.chargeTon, 3)} T · {dayClock(b.millingEnd)}
              </span>
            </Checkbox>
            <span className="batch-option-right">
              {b.slipDensity != null && <span className="cell-sub">{qty(b.slipDensity, 1)} g/L · {qty(b.slipResidue, 2)} %</span>}
              <span className="used-up">
                <Switch size="small" checked={use?.finished ?? false} disabled={disabled || !use}
                  onChange={(on) => setFinished(b.id, on)} aria-label={t('prod.usedUpSwitch')} />
                <span>{t('prod.usedUpSwitch')}</span>
              </span>
            </span>
          </div>
        )
      })}
    </div>
  )
}
