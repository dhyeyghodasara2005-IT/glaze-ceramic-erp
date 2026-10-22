import { Fragment } from 'react'
import { useTranslation } from 'react-i18next'
import type { LotFlow } from '../../api/types'
import { qty } from '../store/format'

const n = (v: number) => v.toLocaleString('en-IN')

/**
 * A lot's tiles from press to kiln, as three stage boxes with "waiting" chips between them:
 *
 *   [Press 2,000 → 1,950]  50 waiting  [Glaze 1,900 → 1,860]  60 waiting  [Kiln 1,800 → 1,764]
 *
 * A negative "waiting" means more tiles were entered than came from the stage before - shown in amber.
 */
export default function LotFunnel({ flow, compact = false }: { flow: LotFlow; compact?: boolean }) {
  const { t } = useTranslation()
  const stages = [
    { key: 'press', label: t('menu.press'), in: flow.pressed, rejects: flow.pressRejects, out: flow.pressGood },
    { key: 'glaze', label: t('line.stage.GLAZE'), in: flow.glazeIn, rejects: flow.glazeRejects, out: flow.glazeOut },
    { key: 'kiln', label: t('line.stage.KILN'), in: flow.kilnIn, rejects: flow.kilnRejects, out: flow.kilnOut },
  ]
  const waiting = [flow.waitingGlaze, flow.waitingKiln]
  const over = waiting.some((w) => w < 0)

  return (
    <div className={`funnel${compact ? ' compact' : ''}`}>
      <div className="funnel-row">
        {stages.map((s, i) => (
          <Fragment key={s.key}>
            {i > 0 && (
              <div className={`funnel-wait${waiting[i - 1] < 0 ? ' over' : waiting[i - 1] === 0 ? ' none' : ''}`}>
                <span>→</span>
                <small>{t('line.waiting', { n: n(waiting[i - 1]) })}</small>
              </div>
            )}
            <div className="funnel-stage">
              <span className="stat-label">{s.label}</span>
              <b>{n(s.out)}</b>
              <span className="cell-sub">
                {n(s.in)} {t('line.tilesIn').toLowerCase()}{s.rejects > 0 && <> · <span className="qty-out">−{n(s.rejects)}</span></>}
              </span>
            </div>
          </Fragment>
        ))}
      </div>
      {!compact && (
        <div className="funnel-foot">
          <span>{t('line.fired')}: <b>{flow.firedSqm != null ? `${qty(flow.firedSqm, 0)} m²` : '–'}</b></span>
          <span>{t('line.yieldSoFar')}: <b>{flow.yieldPct != null ? `${qty(flow.yieldPct, 1)} %` : '–'}</b></span>
        </div>
      )}
      {over && <div className="line-hint short" style={{ marginTop: 8 }}>{t('line.overEntered')}</div>}
    </div>
  )
}
