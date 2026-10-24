import { Tag, Tooltip } from 'antd'
import { CheckCircleFilled, WarningOutlined } from '@ant-design/icons'
import type { TFunction } from 'i18next'
import { useTranslation } from 'react-i18next'
import type { PickReason, SalesOrder, SalesPick } from '../../api/types'

/** "2 pallets + 12 boxes", "3 pallets", or "40 boxes" when the size doesn't say how many make a pallet. */
export function palletText(t: TFunction, boxes: number, perPallet?: number) {
  if (!perPallet || boxes < perPallet) return t('loading.boxesN', { count: boxes })
  const pallets = Math.floor(boxes / perPallet)
  const loose = boxes % perPallet
  return loose
    ? t('loading.palletsAndBoxes', { count: pallets, boxes: loose })
    : t('loading.palletsN', { count: pallets })
}

/** Why Glaze took boxes from this pillar, in a few words (the full sentence on hover). */
export function ReasonTag({ reason }: { reason: PickReason }) {
  const { t } = useTranslation()
  const color = reason === 'ADDED' ? 'blue' : reason === 'CLEARS_PILLAR' ? 'green' : undefined
  return (
    <Tooltip title={t(`loading.reasonHint.${reason}`)}>
      <Tag bordered={false} color={color} className="reason-tag">{t(`loading.reason.${reason}`)}</Tag>
    </Tooltip>
  )
}

/** Loaded boxes against the plan, as a bar that fills up. */
export function LoadProgress({ order }: { order: SalesOrder }) {
  const { t } = useTranslation()
  const total = Math.max(order.totals.boxes, 1)
  const loaded = order.totals.loadedBoxes
  const pct = Math.min(100, Math.round((loaded / total) * 100))
  return (
    <div className="load-progress" aria-label={t('loading.progress', { loaded, total: order.totals.boxes })}>
      <div className="load-progress-bar"><span style={{ width: `${pct}%` }} /></div>
      <span className="load-progress-text">
        {t('loading.progress', { loaded: loaded.toLocaleString('en-IN'), total: order.totals.boxes.toLocaleString('en-IN') })}
        {order.totals.picksToTick > 0 && order.status === 'CONFIRMED' && ` · ${t('loading.toTickN', { count: order.totals.picksToTick })}`}
      </span>
    </div>
  )
}

/** The loading plan on the order page (read only), in the forklift's order. */
export function PickList({ order }: { order: SalesOrder }) {
  const { t } = useTranslation()
  if (!order.picks.length) return <p className="cell-sub">{t('loading.nothingPlanned')}</p>
  return (
    <div className="pick-list">
      {order.picks.map((p, i) => (
        <div key={p.id} className={`pick-row${p.loadedBoxes != null ? ' done' : ''}${p.tooFew ? ' too-few' : ''}`}
          style={{ animationDelay: `${i * 0.04}s` }}>
          <span className="pick-pillar">{p.pillarCode}</span>
          <div className="pick-what">
            <div className="cell-title">{p.designName} · {p.gradeName}</div>
            <div className="cell-sub">
              {t('loading.shadeCaliber', { shade: p.shade, caliber: p.caliber })} · {t('loading.batch', { batch: p.batchCode })}
              {' · '}{t('loading.rowN', { n: p.lineNo })}
            </div>
            {p.tooFew && <div className="line-hint short"><WarningOutlined /> {t('loading.tooFew', { n: p.boxesThere })}</div>}
          </div>
          <div className="pick-boxes">
            <b>{palletText(t, p.plannedBoxes, p.boxesPerPallet)}</b>
            {p.loadedBoxes != null && (
              <div className={`pick-loaded${p.loadedBoxes !== p.plannedBoxes ? ' changed' : ''}`}>
                <CheckCircleFilled /> {t('loading.loadedN', { n: p.loadedBoxes })}
              </div>
            )}
          </div>
          <ReasonTag reason={p.reason} />
        </div>
      ))}
    </div>
  )
}

/** Boxes of a pick that went on the truck: what was ticked (a dispatched order has every row ticked). */
export const loadedOf = (p: SalesPick) => p.loadedBoxes ?? 0
