import { useState } from 'react'
import { Button } from 'antd'
import { AppstoreOutlined, AuditOutlined, GoldOutlined, SketchOutlined, TrophyOutlined } from '@ant-design/icons'
import { Link, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../components/PageHeader'
import CountUp from '../../components/CountUp'
import EmptyState from '../../components/EmptyState'
import { FadeIn, Stagger, StaggerItem } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import NoPlant from '../store/NoPlant'
import { qty, shortDate } from '../store/format'
import { PACK_PATH, useFinishingOverview } from './finishApi'
import { LINE_PATHS } from './lineApi'
import GradeSplit from './GradeSplit'
import LotFunnel from './LotFunnel'
import LotStatusTag from './LotStatusTag'
import QualityCheckModal from './QualityCheckModal'
import VerdictTag from './VerdictTag'

/**
 * The finishing home: every lot with fired tiles still on their way to the box (cooling area ->
 * polishing -> sorting & packing), its latest quality check, and today's figures.
 */
export default function FinishingPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { can, currentPlant } = useAuth()
  const { data } = useFinishingOverview(currentPlant?.id)
  // the lot a quality check is being entered for (null = modal closed)
  const [checking, setChecking] = useState<number | null>(null)

  if (!currentPlant) return <NoPlant />

  const today = data?.today
  const top = data?.topGradeName ?? ''
  const subtitle = data
    ? data.shiftCode
      ? t('prod.bodySubtitle', { date: shortDate(data.prodDate), shift: `${data.shiftCode} (${data.shiftName})` })
      : t('prod.bodySubtitleNoShift', { date: shortDate(data.prodDate) })
    : currentPlant.name
  const pct = (part: number, whole: number) => (whole > 0 ? ((part * 100) / whole).toFixed(1) : undefined)
  const polishLoss = today && pct(today.polishRejects, today.polishIn)
  const brokenPct = today && pct(today.broken, today.sorted)

  const tiles = [
    { key: 'polished', icon: <SketchOutlined />, label: t('finishing.polishedToday'), value: today && today.polishIn - today.polishRejects,
      sub: polishLoss && t('line.lossPct', { pct: polishLoss }), tint: ['#EEF2F8', '#34507A'] },
    { key: 'packed', icon: <GoldOutlined />, label: t('finishing.packedToday'), value: today?.boxes, suffix: ` ${t('finishing.boxes').toLowerCase()}`,
      sub: today && `${qty(today.packedSqm, 0)} m²`, tint: ['#FDEEE8', '#A8401F'] },
    { key: 'top', icon: <TrophyOutlined />, label: t('finishing.topToday', { grade: top }),
      text: today?.topPct != null ? `${qty(today.topPct, 1)} %` : '–',
      sub: brokenPct && t('finishing.brokenPct', { pct: brokenPct }), tint: ['#F3F1E6', '#6B5A16'] },
    { key: 'checks', icon: <AuditOutlined />, label: t('finishing.checksToday'), value: today?.checks, tint: ['#E9F3F3', '#285A5B'] },
  ]

  return (
    <>
      <PageHeader title={t('finishing.hubTitle')} subtitle={subtitle} icon={<AppstoreOutlined />} />

      <Stagger className="stat-grid">
        {tiles.map((tile) => (
          <StaggerItem key={tile.key}>
            <div className="stat-card">
              <span className="stat-icon" style={{ background: tile.tint[0], color: tile.tint[1] }}>{tile.icon}</span>
              <span style={{ minWidth: 0 }}>
                <div className="stat-value">
                  {'text' in tile ? tile.text : tile.value === undefined ? '–' : <CountUp value={tile.value} />}
                  {tile.suffix && <small className="stat-suffix">{tile.suffix}</small>}
                </div>
                <div className="stat-label">{tile.label}</div>
                {tile.sub && <div className="cell-sub">{tile.sub}</div>}
              </span>
            </div>
          </StaggerItem>
        ))}
      </Stagger>

      {today && today.grades.length > 0 && (
        <FadeIn delay={0.08} className="panel doc-panel">
          <div className="form-section">{t('finishing.gradesToday')}</div>
          <GradeSplit grades={today.grades.map((g) => ({ key: String(g.gradeId), name: g.name, sortOrder: g.sortOrder, boxes: g.boxes, pieces: g.pieces }))} />
        </FadeIn>
      )}

      <FadeIn delay={0.12}>
        <h2 className="section-title"><AppstoreOutlined style={{ color: 'var(--brand-600)' }} /> {t('finishing.lotsInFinishing')}</h2>
        {data && data.lots.length === 0 && (
          <div className="panel"><EmptyState title={t('finishing.noLots')} action={<Link to="/production/line">{t('menu.line')}</Link>} /></div>
        )}
        <div className="lot-line-list">
          {data?.lots.map(({ lot, flow, lastCheck, topPct }) => (
            <div key={lot.id} className="line-card">
              <div className="mill-card-head">
                <span>
                  <span className="code-badge">{lot.pressCode}</span>{' '}
                  <Link to={`/production/lots/${lot.id}`} className="cell-title">{lot.designName}</Link>
                  <span className="cell-sub"> · {lot.lotNo}</span>
                </span>
                <LotStatusTag status={lot.status} />
              </div>
              <div className="check-strip">
                {lastCheck ? (
                  <>
                    <VerdictTag verdict={lastCheck.verdict} />
                    <span className="cell-sub">
                      {lastCheck.checkNo} · {shortDate(lastCheck.checkDate)}
                      {lastCheck.expectedTopPct != null && <> · {top} {t('finishing.expected', { pct: qty(lastCheck.expectedTopPct, 0) })}</>}
                    </span>
                  </>
                ) : (
                  <span className="cell-sub">{t('finishing.notChecked')}</span>
                )}
                {topPct != null && flow.sorted > 0 && (
                  <span className="check-actual">{t('finishing.actual', { grade: top, pct: qty(topPct, 1) })}</span>
                )}
              </div>
              <LotFunnel flow={flow} compact part="finish" />
              <div className="line-actions">
                {can('QUALITY_CHECK') && (
                  <Button size="small" icon={<AuditOutlined />} onClick={() => setChecking(lot.id)}>{t('finishing.checkShort')}</Button>
                )}
                {can('POLISH_ENTRY') && (
                  <Button type="primary" size="small" onClick={() => navigate(`${LINE_PATHS.POLISH}/new?lot=${lot.id}`)}>
                    {t('line.shortEntry.POLISH')}
                  </Button>
                )}
                {can('PACKING_ENTRY') && (
                  <Button type="primary" size="small" onClick={() => navigate(`${PACK_PATH}/new?lot=${lot.id}`)}>
                    {t('finishing.shortPack')}
                  </Button>
                )}
              </div>
            </div>
          ))}
        </div>
      </FadeIn>

      <QualityCheckModal open={checking !== null} plantId={currentPlant.id} lotId={checking ?? undefined} onClose={() => setChecking(null)} />
    </>
  )
}
