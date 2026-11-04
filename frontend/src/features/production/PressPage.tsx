import { Button } from 'antd'
import { AppstoreOutlined, BuildOutlined, DatabaseOutlined, FallOutlined, PlusOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../components/PageHeader'
import CountUp from '../../components/CountUp'
import EmptyState from '../../components/EmptyState'
import { FadeIn, Stagger, StaggerItem } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import NoPlant from '../store/NoPlant'
import { shortDate } from '../store/format'
import { usePressOverview } from './pressApi'
import SiloCards from './SiloCards'

/**
 * The press & dryer supervisor's home: each press and what it pressed today, the day's numbers, the silos.
 * Only green tiles here: the design goes on at the printer, so it is started on the Glaze & kiln screen.
 */
export default function PressPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { can, currentPlant } = useAuth()
  const { data } = usePressOverview(currentPlant?.id)

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
    { key: 'good', icon: <AppstoreOutlined />, label: t('press.goodToday'), value: data?.today.good,
      sub: data && t('press.tiles').toLowerCase(), tint: ['#E9F3F3', '#285A5B'] },
    { key: 'loss', icon: <FallOutlined />, label: t('press.lossToday'), value: data?.today.rejects,
      sub: lossPct !== undefined ? `${lossPct.toFixed(1)} %` : undefined, tint: ['#FDF1E4', '#B45309'] },
  ]

  return (
    <>
      <PageHeader title={t('press.hubTitle')} subtitle={subtitle} icon={<BuildOutlined />}
        extra={canEntry && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/production/press-entries/new')}>{t('press.shiftEntry')}</Button>
        )} />

      <Stagger className="stat-grid">
        {tiles.map((tile) => (
          <StaggerItem key={tile.key}>
            <div className="stat-card">
              <span className="stat-icon" style={{ background: tile.tint[0], color: tile.tint[1] }}>{tile.icon}</span>
              <span style={{ minWidth: 0 }}>
                <div className="stat-value">
                  {tile.value === undefined ? '–' : <CountUp value={tile.value} />}
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
        <p className="panel-hint" style={{ margin: '-4px 0 12px' }}>{t('press.greenOnly')}</p>
        {data && data.lines.length === 0 && (
          <div className="panel"><EmptyState title={t('prod.noMachines', { type: t('machineType.PRESS') })} /></div>
        )}
        <div className="mill-grid">
          {data?.lines.map((line) => (
            <div key={line.pressId} className={`line-card${line.entries > 0 ? '' : ' idle'}`}>
              <div className="mill-card-head">
                <span className="code-badge">{line.pressCode}</span>
              </div>
              <div className="line-design">{line.pressName}</div>
              {line.entries > 0 ? (
                <div className="line-figures">
                  <span><b>{line.pressed.toLocaleString('en-IN')}</b> {t('press.pressed').toLowerCase()}</span>
                  <span><b>{line.good.toLocaleString('en-IN')}</b> {t('press.good').toLowerCase()}</span>
                </div>
              ) : (
                <div className="cell-sub line-idle">{t('press.noneToday')}</div>
              )}
              {canEntry && (
                <div className="line-actions">
                  <Button type="primary" size="small" onClick={() => navigate(`/production/press-entries/new?press=${line.pressId}`)}>
                    {t('press.shiftEntry')}
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
    </>
  )
}
