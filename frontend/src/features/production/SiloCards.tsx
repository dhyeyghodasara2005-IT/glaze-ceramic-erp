import { App, Button, Popconfirm } from 'antd'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import EmptyState from '../../components/EmptyState'
import { useAuth } from '../../auth/AuthContext'
import { errorMessage } from '../../api/errors'
import type { SiloStatus } from '../../api/types'
import { qty } from '../store/format'
import { useMarkSiloEmpty } from './pressApi'
import { dayClock, duration } from './time'

/**
 * One card per silo: tonnes on record, how old the oldest powder is, and the runs it came from.
 * Shared by the press home screen and the Silos page (`detailed` shows the runs).
 */
export default function SiloCards({ silos, detailed = false }: { silos?: SiloStatus[]; detailed?: boolean }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const { can } = useAuth()
  const markEmpty = useMarkSiloEmpty()
  const canMark = can('BODY_PREP_ENTRY') || can('PRESS_ENTRY')

  if (silos && silos.length === 0) return <div className="panel"><EmptyState title={t('press.noSilos')} /></div>

  const onEmpty = async (s: SiloStatus) => {
    try {
      await markEmpty.mutateAsync(s.siloId)
      message.success(t('press.markedEmpty', { silo: s.code }))
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  return (
    <div className="silo-grid">
      {silos?.map((s) => (
        <div key={s.siloId} className={`silo-card${s.tonnes > 0 ? '' : ' empty'}`}>
          <div className="mill-card-head">
            <span className="code-badge">{s.code}</span>
            <span className="cell-sub">{s.name}</span>
          </div>
          <div className="mill-time">{qty(s.tonnes, 1)} <small>T</small></div>
          <div className="cell-sub">
            {s.oldestAt ? t('press.oldest', { time: duration(s.oldestAt) }) : t('press.siloEmpty')}
          </div>
          {detailed && s.runs.length > 0 && (
            <ul className="silo-runs">
              {s.runs.map((r) => (
                <li key={r.runId}>
                  <Link to={`/production/spray-runs/${r.runId}`}>{r.runNo}</Link>
                  <span className="cell-sub">{dayClock(r.madeAt)}</span>
                  <b>{qty(r.powderLeft, 1)} T</b>
                </li>
              ))}
            </ul>
          )}
          {canMark && s.tonnes > 0 && (
            <Popconfirm
              title={t('press.markEmptyTitle', { silo: s.code })}
              description={<div style={{ maxWidth: 280 }}>{t('press.markEmptyHint')}</div>}
              okText={t('press.markEmpty')}
              cancelText={t('common.cancel')}
              onConfirm={() => onEmpty(s)}
            >
              <Button size="small" style={{ marginTop: 10 }} loading={markEmpty.isPending}>{t('press.markEmpty')}</Button>
            </Popconfirm>
          )}
        </div>
      ))}
    </div>
  )
}
