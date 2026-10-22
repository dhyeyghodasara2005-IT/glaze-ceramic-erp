import { useState } from 'react'
import { Button } from 'antd'
import {
  ArrowRightOutlined,
  CloudOutlined,
  ControlOutlined,
  DatabaseOutlined,
  ExperimentOutlined,
  FieldTimeOutlined,
  SyncOutlined,
} from '@ant-design/icons'
import { Link, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import CountUp from '../../components/CountUp'
import { FadeIn, Stagger, StaggerItem } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import type { BodyBatchSummary } from '../../api/types'
import NoPlant from '../store/NoPlant'
import { qty, shortDate } from '../store/format'
import { useBodyOverview } from './api'
import DischargeModal from './DischargeModal'
import { dayClock, duration } from './time'
import { useNow } from './useNow'

/**
 * The body preparation supervisor's home - built for a phone on the shop floor:
 * big tiles, the mills that are running, the slip waiting for the spray dryer.
 */
export default function BodyPrepPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { can, currentPlant } = useAuth()
  const { data } = useBodyOverview(currentPlant?.id)
  const [discharging, setDischarging] = useState<BodyBatchSummary | null>(null)
  useNow() // re-render every minute: the milling timers keep counting

  if (!currentPlant) return <NoPlant />
  const canEntry = can('BODY_PREP_ENTRY')

  const subtitle = data
    ? data.shiftCode
      ? t('prod.bodySubtitle', { date: shortDate(data.prodDate), shift: `${data.shiftCode} (${data.shiftName})` })
      : t('prod.bodySubtitleNoShift', { date: shortDate(data.prodDate) })
    : currentPlant.name

  const tiles = [
    { key: 'charged', icon: <SyncOutlined />, label: t('prod.chargedToday'), value: data?.charged.count,
      sub: data && t('prod.tonnes', { n: qty(data.charged.tonnes, 1) }), tint: ['#FBF3EE', '#A84A23'] },
    { key: 'powder', icon: <CloudOutlined />, label: t('prod.powderToday'), value: data && Math.round(data.sprayed.tonnes),
      sub: data && `${data.sprayed.count} × ${t('prod.newRun')}`, tint: ['#E9F3F3', '#285A5B'], suffix: ' T' },
    { key: 'mills', icon: <FieldTimeOutlined />, label: t('prod.inMills'), value: data?.inMills.length, tint: ['#EEF2FB', '#2F4B8A'] },
    { key: 'waiting', icon: <DatabaseOutlined />, label: t('prod.waitingShort'), value: data?.waiting.length, tint: ['#EAF6F2', '#1F6B55'] },
  ]

  return (
    <>
      <PageHeader title={t('prod.bodyTitle')} subtitle={subtitle} icon={<ControlOutlined />} />

      <Stagger className="stat-grid">
        {tiles.map((tile) => (
          <StaggerItem key={tile.key}>
            <div className="stat-card">
              <span className="stat-icon" style={{ background: tile.tint[0], color: tile.tint[1] }}>{tile.icon}</span>
              <span style={{ minWidth: 0 }}>
                <div className="stat-value">
                  {tile.value === undefined ? '–' : <CountUp value={tile.value} />}
                  {tile.suffix}
                </div>
                <div className="stat-label">{tile.label}</div>
                {tile.sub && <div className="cell-sub">{tile.sub}</div>}
              </span>
            </div>
          </StaggerItem>
        ))}
      </Stagger>

      {canEntry && (
        <Stagger className="action-grid prod-actions">
          <StaggerItem>
            <Link to="/production/body-batches/new" className="action-card">
              <span className="action-icon"><ExperimentOutlined /></span>
              <span style={{ minWidth: 0 }}>
                <div className="action-title">{t('prod.chargeMill')}</div>
                <div className="action-hint">{t('prod.chargeMillHint')}</div>
              </span>
              <ArrowRightOutlined className="action-arrow" />
            </Link>
          </StaggerItem>
          <StaggerItem>
            <Link to="/production/spray-runs/new" className="action-card">
              <span className="action-icon"><CloudOutlined /></span>
              <span style={{ minWidth: 0 }}>
                <div className="action-title">{t('prod.newRun')}</div>
                <div className="action-hint">{t('prod.newRunHint')}</div>
              </span>
              <ArrowRightOutlined className="action-arrow" />
            </Link>
          </StaggerItem>
        </Stagger>
      )}

      <FadeIn delay={0.15}>
        <h2 className="section-title"><FieldTimeOutlined style={{ color: 'var(--brand-600)' }} /> {t('prod.inMills')}</h2>
        {data && data.inMills.length === 0 && <div className="panel"><EmptyState title={t('prod.millsEmpty')} /></div>}
        <div className="mill-grid">
          {data?.inMills.map((b) => (
            <div key={b.id} className="mill-card" role="link" tabIndex={0}
              onClick={() => navigate(`/production/body-batches/${b.id}`)}
              onKeyDown={(e) => e.key === 'Enter' && navigate(`/production/body-batches/${b.id}`)}>
              <div className="mill-card-head">
                <span className="code-badge">{b.ballMillCode}</span>
                <span className="cell-sub">{b.batchNo}</span>
              </div>
              <div className="mill-spin" aria-hidden><SyncOutlined spin /></div>
              <div className="mill-time">{duration(b.millingStart)}</div>
              <div className="cell-sub">
                {dayClock(b.millingStart)} · {t('prod.tonnes', { n: qty(b.chargeTon, 3) })}{b.recipeCode ? ` · ${b.recipeCode}` : ''}
              </div>
              {canEntry && (
                <Button type="primary" block style={{ marginTop: 12 }}
                  onClick={(e) => {
                    e.stopPropagation() // don't also open the batch page
                    setDischarging(b)
                  }}>
                  {t('prod.discharge')}
                </Button>
              )}
            </div>
          ))}
        </div>
      </FadeIn>

      <FadeIn delay={0.2}>
        <h2 className="section-title" style={{ marginTop: 24 }}>
          <DatabaseOutlined style={{ color: 'var(--brand-600)' }} /> {t('prod.waiting')}
        </h2>
        <div className="panel slip-list">
          {data && data.waiting.length === 0 && <EmptyState title={t('prod.waitingEmpty')} />}
          {data?.waiting.map((b) => (
            <Link key={b.id} to={`/production/body-batches/${b.id}`} className="slip-row">
              <span>
                <div className="cell-title">{b.batchNo}</div>
                <div className="cell-sub">{b.ballMillCode} → {b.slipTankCode} · {dayClock(b.millingEnd)}</div>
              </span>
              <span className="slip-readings">
                <span>{qty(b.slipDensity, 1)} <small>g/L</small></span>
                <span>{qty(b.slipResidue, 2)} <small>%</small></span>
              </span>
            </Link>
          ))}
        </div>
      </FadeIn>

      <DischargeModal batch={discharging} plantId={currentPlant.id} onClose={() => setDischarging(null)} />
    </>
  )
}
