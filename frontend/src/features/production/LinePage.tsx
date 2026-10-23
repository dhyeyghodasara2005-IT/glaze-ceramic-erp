import { Button } from 'antd'
import { FireOutlined, FormatPainterOutlined, NodeExpandOutlined, ThunderboltOutlined } from '@ant-design/icons'
import { Link, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../components/PageHeader'
import CountUp from '../../components/CountUp'
import EmptyState from '../../components/EmptyState'
import { FadeIn, Stagger, StaggerItem } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import NoPlant from '../store/NoPlant'
import { qty, shortDate } from '../store/format'
import { LINE_PATHS, useLineOverview } from './lineApi'
import LotFunnel from './LotFunnel'
import LotStatusTag from './LotStatusTag'

/** The glaze & kiln home: every lot whose tiles may be in the line, with its funnel, and today's figures. */
export default function LinePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { can, currentPlant } = useAuth()
  const { data } = useLineOverview(currentPlant?.id)

  if (!currentPlant) return <NoPlant />

  const subtitle = data
    ? data.shiftCode
      ? t('prod.bodySubtitle', { date: shortDate(data.prodDate), shift: `${data.shiftCode} (${data.shiftName})` })
      : t('prod.bodySubtitleNoShift', { date: shortDate(data.prodDate) })
    : currentPlant.name
  const pct = (rejects: number, tilesIn: number) => (tilesIn > 0 ? ((rejects * 100) / tilesIn).toFixed(1) : undefined)
  const glazeLoss = data && pct(data.today.glazeRejects, data.today.glazeIn)
  const kilnLoss = data && pct(data.today.kilnRejects, data.today.kilnIn)

  const tiles = [
    { key: 'glazed', icon: <FormatPainterOutlined />, label: t('line.glazedToday'), value: data && data.today.glazeIn - data.today.glazeRejects,
      sub: glazeLoss && t('line.lossPct', { pct: glazeLoss }), tint: ['#F1EEF8', '#4B3D73'] },
    { key: 'fired', icon: <FireOutlined />, label: t('line.firedToday'), value: data && Math.round(data.today.firedSqm), suffix: ' m²',
      sub: kilnLoss && t('line.lossPct', { pct: kilnLoss }), tint: ['#FDEEE8', '#A8401F'] },
    { key: 'gas', icon: <ThunderboltOutlined />, label: t('line.gasPerSqm'), text: data?.today.gasPerSqm != null ? qty(data.today.gasPerSqm, 2) : '–',
      sub: data && `${qty(data.today.gasScm, 0)} SCM`, tint: ['#E9F3F3', '#285A5B'] },
  ]

  return (
    <>
      <PageHeader title={t('line.hubTitle')} subtitle={subtitle} icon={<NodeExpandOutlined />} />

      <Stagger className="stat-grid">
        {tiles.map((tile) => (
          <StaggerItem key={tile.key}>
            <div className="stat-card">
              <span className="stat-icon" style={{ background: tile.tint[0], color: tile.tint[1] }}>{tile.icon}</span>
              <span style={{ minWidth: 0 }}>
                <div className="stat-value">
                  {'text' in tile ? tile.text : tile.value === undefined ? '–' : <CountUp value={tile.value} />}
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
        <h2 className="section-title"><NodeExpandOutlined style={{ color: 'var(--brand-600)' }} /> {t('line.lotsInLine')}</h2>
        {data && data.lots.length === 0 && (
          <div className="panel"><EmptyState title={t('line.noLots')} action={<Link to="/production/press">{t('menu.press')}</Link>} /></div>
        )}
        <div className="lot-line-list">
          {data?.lots.map(({ lot, flow }) => (
            <div key={lot.id} className="line-card">
              <div className="mill-card-head">
                <span>
                  <span className="code-badge">{lot.pressCode}</span>{' '}
                  <Link to={`/production/lots/${lot.id}`} className="cell-title">{lot.designName}</Link>
                  <span className="cell-sub"> · {lot.lotNo}</span>
                </span>
                <LotStatusTag status={lot.status} />
              </div>
              <LotFunnel flow={flow} compact part="line" />
              <div className="line-actions">
                {can('GLAZE_ENTRY') && (
                  <Button type="primary" size="small" onClick={() => navigate(`${LINE_PATHS.GLAZE}/new?lot=${lot.id}`)}>
                    {t('line.shortEntry.GLAZE')}
                  </Button>
                )}
                {can('KILN_ENTRY') && (
                  <Button type="primary" size="small" onClick={() => navigate(`${LINE_PATHS.KILN}/new?lot=${lot.id}`)}>
                    {t('line.shortEntry.KILN')}
                  </Button>
                )}
              </div>
            </div>
          ))}
        </div>
      </FadeIn>
    </>
  )
}
