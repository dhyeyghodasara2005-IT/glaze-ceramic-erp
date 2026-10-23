import { Fragment } from 'react'
import { useTranslation } from 'react-i18next'
import type { LotFlow } from '../../api/types'
import { qty } from '../store/format'

const n = (v: number) => v.toLocaleString('en-IN')

/** Which stages to show: the glaze & kiln screen, the finishing screen, or the whole journey (lot page). */
export type FunnelPart = 'line' | 'finish' | 'all'

/**
 * A lot's tiles stage by stage, as boxes with "waiting" chips between them:
 *
 *   [Press 2,000 → 1,950]  50 waiting  [Glaze …]  60 waiting  [Kiln …]  64 in cooling  [Polish …]  84 waiting  [Packed …]
 *
 * A negative "waiting" means more tiles were entered than came from the stage before - shown in amber.
 */
export default function LotFunnel({ flow, compact = false, part = 'all' }: { flow: LotFlow; compact?: boolean; part?: FunnelPart }) {
  const { t } = useTranslation()
  const all = [
    { key: 'press', label: t('menu.press'), in: flow.pressed, rejects: flow.pressRejects, out: flow.pressGood },
    { key: 'glaze', label: t('line.stage.GLAZE'), in: flow.glazeIn, rejects: flow.glazeRejects, out: flow.glazeOut },
    { key: 'kiln', label: t('line.stage.KILN'), in: flow.kilnIn, rejects: flow.kilnRejects, out: flow.kilnOut },
    { key: 'polish', label: t('line.stage.POLISH'), in: flow.polishIn, rejects: flow.polishRejects, out: flow.polishOut },
    { key: 'pack', label: t('finishing.packed'), in: flow.sorted, rejects: flow.broken, out: flow.packed, boxes: flow.boxes },
  ]
  // waiting[i] sits between stage i and stage i + 1
  const allWaiting = [
    { n: flow.waitingGlaze, label: 'line.waiting' },
    { n: flow.waitingKiln, label: 'line.waiting' },
    { n: flow.waitingPolish, label: 'finishing.inCooling' },
    { n: flow.waitingSort, label: 'line.waiting' },
  ]
  const [from, to] = part === 'line' ? [0, 3] : part === 'finish' ? [2, 5] : [0, 5]
  const stages = all.slice(from, to)
  const waiting = allWaiting.slice(from, to - 1)
  const over = waiting.some((w) => w.n < 0)

  return (
    <div className={`funnel${compact ? ' compact' : ''}${stages.length > 3 ? ' wide' : ''}`}>
      <div className={`funnel-row s${stages.length}`}>
        {stages.map((s, i) => (
          <Fragment key={s.key}>
            {i > 0 && (
              <div className={`funnel-wait${waiting[i - 1].n < 0 ? ' over' : waiting[i - 1].n === 0 ? ' none' : ''}`}>
                <span>→</span>
                <small>{t(waiting[i - 1].label, { n: n(waiting[i - 1].n) })}</small>
              </div>
            )}
            <div className="funnel-stage">
              <span className="stat-label">{s.label}</span>
              <b>{n(s.out)}</b>
              <span className="cell-sub">
                {s.boxes !== undefined
                  ? <>{t('finishing.boxesN', { n: n(s.boxes) })}{s.rejects > 0 && <> · <span className="qty-out">−{n(s.rejects)}</span></>}</>
                  : <>{n(s.in)} {t('line.tilesIn').toLowerCase()}{s.rejects > 0 && <> · <span className="qty-out">−{n(s.rejects)}</span></>}</>}
              </span>
            </div>
          </Fragment>
        ))}
      </div>
      {!compact && (
        <div className="funnel-foot">
          <span>{t('line.fired')}: <b>{flow.firedSqm != null ? `${qty(flow.firedSqm, 0)} m²` : '–'}</b></span>
          {flow.packed > 0 && <span>{t('finishing.packed')}: <b>{flow.packedSqm != null ? `${qty(flow.packedSqm, 0)} m²` : '–'}</b></span>}
          <span>{t('line.yieldSoFar')}: <b>{flow.yieldPct != null ? `${qty(flow.yieldPct, 1)} %` : '–'}</b></span>
          {flow.packed > 0 && (
            <span>{t('finishing.packedYield')}: <b>{flow.packedYieldPct != null ? `${qty(flow.packedYieldPct, 1)} %` : '–'}</b></span>
          )}
        </div>
      )}
      {over && <div className="line-hint short" style={{ marginTop: 8 }}>{t('line.overEntered')}</div>}
    </div>
  )
}
