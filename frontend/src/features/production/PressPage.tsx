import { useMemo, useState } from 'react'
import { Button, Space } from 'antd'
import { AppstoreOutlined, BuildOutlined, DatabaseOutlined, FallOutlined, PlusOutlined, SwapOutlined } from '@ant-design/icons'
import { Link, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../components/PageHeader'
import CountUp from '../../components/CountUp'
import EmptyState from '../../components/EmptyState'
import { FadeIn, Stagger, StaggerItem } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import type { LotSummary } from '../../api/types'
import NoPlant from '../store/NoPlant'
import { qty, shortDate } from '../store/format'
import LotStatusTag from './LotStatusTag'
import OpenLotModal from './OpenLotModal'
import { usePressOverview } from './pressApi'
import SiloCards from './SiloCards'

/** The press & dryer supervisor's home: each line and its design, today's numbers, the silos. */
export default function PressPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { can, currentPlant } = useAuth()
  const { data } = usePressOverview(currentPlant?.id)
  const [starting, setStarting] = useState<{ pressId?: number } | null>(null)
  const running = useMemo(
    () => new Map<number, LotSummary>((data?.lines ?? []).filter((l) => l.lot).map((l) => [l.pressId, l.lot!])),
    [data],
  )

  if (!currentPlant) return <NoPlant />
  const canEntry = can('PRESS_ENTRY')

  const subtitle = data
    ? data.shiftCode
      ? t('prod.bodySubtitle', { date: shortDate(data.prodDate), shift: `${data.shiftCode} (${data.shiftName})` })
      : t('prod.bodySubtitleNoShift', { date: shortDate(data.prodDate) })
    : currentPlant.name
  const lossPct = data && data.today.pressed > 0 ? (data.today.rejects * 100) / data.today.pressed : undefined

  const tiles = [
    { key: 'pressed', icon: <BuildOutlined />, label: t('press.pressedToday'), value: data?.today.pressed,
      sub: data && t('press.entriesToday', { count: data.today.entries }), tint: ['#FBF3EE', '#A84A23'] },
    { key: 'good', icon: <AppstoreOutlined />, label: t('press.goodToday'), value: data && Math.round(data.today.goodSqm),
      suffix: ' m²', sub: data && `${data.today.good.toLocaleString('en-IN')} ${t('press.tiles').toLowerCase()}`, tint: ['#E9F3F3', '#285A5B'] },
    { key: 'loss', icon: <FallOutlined />, label: t('press.lossToday'), value: data?.today.rejects,
      sub: lossPct !== undefined ? `${lossPct.toFixed(1)} %` : undefined, tint: ['#FDF1E4', '#B45309'] },
  ]

  return (
    <>
      <PageHeader title={t('press.hubTitle')} subtitle={subtitle} icon={<BuildOutlined />}
        extra={canEntry && (
          <Space wrap>
            <Button icon={<SwapOutlined />} onClick={() => setStarting({})}>{t('press.startDesign')}</Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/production/press-entries/new')}>{t('press.shiftEntry')}</Button>
          </Space>
        )} />

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

      <FadeIn delay={0.12}>
        <h2 className="section-title"><BuildOutlined style={{ color: 'var(--brand-600)' }} /> {t('press.lines')}</h2>
        {data && data.lines.length === 0 && (
          <div className="panel"><EmptyState title={t('prod.noMachines', { type: t('machineType.PRESS') })} /></div>
        )}
        <div className="mill-grid">
          {data?.lines.map((line) => (
            <div key={line.pressId} className={`line-card${line.lot ? '' : ' idle'}`}>
              <div className="mill-card-head">
                <span className="code-badge">{line.pressCode}</span>
                {line.lot && <LotStatusTag status={line.lot.status} />}
              </div>
              {line.lot ? (
                <>
                  <Link to={`/production/lots/${line.lot.id}`} className="line-design">{line.lot.designName}</Link>
                  <div className="cell-sub">{line.lot.lotNo} · {line.lot.sizeCode} {line.lot.finishName}</div>
                  <div className="line-figures">
                    <span><b>{line.lot.good.toLocaleString('en-IN')}</b> {t('press.good').toLowerCase()}</span>
                    {line.lot.goodSqm != null && <span><b>{qty(line.lot.goodSqm, 0)}</b> m²</span>}
                  </div>
                </>
              ) : (
                <div className="cell-sub line-idle">{t('press.idle')}</div>
              )}
              {canEntry && (
                <div className="line-actions">
                  {line.lot && (
                    <Button type="primary" size="small" onClick={() => navigate(`/production/press-entries/new?lot=${line.lot!.id}`)}>
                      {t('press.shiftEntry')}
                    </Button>
                  )}
                  <Button size="small" onClick={() => setStarting({ pressId: line.pressId })}>
                    {line.lot ? t('press.changeDesign') : t('press.startDesign')}
                  </Button>
                </div>
              )}
            </div>
          ))}
        </div>
      </FadeIn>

      <FadeIn delay={0.18}>
        <h2 className="section-title" style={{ marginTop: 24 }}>
          <DatabaseOutlined style={{ color: 'var(--brand-600)' }} /> {t('press.silosTitle')}
        </h2>
        <SiloCards silos={data?.silos} />
      </FadeIn>

      <OpenLotModal open={starting !== null} plantId={currentPlant.id} pressId={starting?.pressId} running={running}
        onClose={() => setStarting(null)} />
    </>
  )
}
