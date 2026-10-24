import { useState, type ReactNode } from 'react'
import { Segmented } from 'antd'
import {
  AlertOutlined, ApartmentOutlined, ArrowDownOutlined, ArrowRightOutlined, ArrowUpOutlined, BankOutlined,
  CheckCircleOutlined, ExclamationCircleOutlined, RocketOutlined, SafetyCertificateOutlined, ShopOutlined, TeamOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { Link } from 'react-router-dom'
import { motion } from 'motion/react'
import { useTranslation } from 'react-i18next'
import dayjs from 'dayjs'
import { useAuth } from '../auth/AuthContext'
import type { DashboardResponse, GradeShare, Permission } from '../api/types'
import CountUp from '../components/CountUp'
import { EASE, FadeIn, Stagger, StaggerItem } from '../components/motion'
import { useDashboard } from '../features/dashboard/dashboardApi'
import { BarList, ChartCard, Meter, ShareBar, TrendChart, fmt, type Series } from '../features/dashboard/charts'

// Tiles floating in the greeting banner.
const HERO_TILES = ['', 'warm', '', 'cream', 'cream', '', 'warm', '', '', 'warm', 'cream', '']

function HeroMosaic() {
  return (
    <div className="hero-mosaic" aria-hidden>
      {HERO_TILES.map((kind, i) => (
        <motion.div
          key={i}
          className={`hero-tile ${kind}`}
          initial={{ opacity: 0, scale: 0.5 }}
          animate={{ opacity: 1, scale: 1, y: [0, -6, 0] }}
          transition={{
            opacity: { duration: 0.5, delay: 0.2 + i * 0.04 },
            scale: { duration: 0.6, ease: EASE, delay: 0.2 + i * 0.04 },
            y: { duration: 4 + (i % 3), repeat: Infinity, ease: 'easeInOut', delay: i * 0.25 },
          }}
        />
      ))}
    </div>
  )
}

/** The periods the numbers can cover; "today" is in the plant's own day. */
type Period = 'today' | 'week' | 'month30' | 'thisMonth'

function rangeOf(period: Period) {
  const today = dayjs()
  const from = period === 'today' ? today
    : period === 'week' ? today.subtract(6, 'day')
      : period === 'month30' ? today.subtract(29, 'day')
        : today.startOf('month')
  return { from: from.format('YYYY-MM-DD'), to: today.format('YYYY-MM-DD'), days: today.diff(from, 'day') + 1 }
}

/** Grades best first take the terracotta ramp darkest first (an ordered scale, checked with the validator). */
const GRADE_COLORS = ['var(--viz-grade-1)', 'var(--viz-grade-2)', 'var(--viz-grade-3)', 'var(--viz-grade-4)']
const gradeParts = (grades: GradeShare[], unit: 'boxes') => grades
  .slice().sort((a, b) => a.sortOrder - b.sortOrder)
  .map((g, i) => ({ key: g.code, label: g.name, value: g[unit], pct: g.pct, color: GRADE_COLORS[Math.min(i, 3)] }))

const SETUP: { to: string; icon: ReactNode; titleKey: string; hintKey: string; permission: Permission }[] = [
  { to: '/companies', icon: <BankOutlined />, titleKey: 'menu.companies', hintKey: 'dashboard.setupCompanies', permission: 'COMPANY_VIEW' },
  { to: '/plants', icon: <ApartmentOutlined />, titleKey: 'menu.plants', hintKey: 'dashboard.setupPlants', permission: 'PLANT_VIEW' },
  { to: '/roles', icon: <SafetyCertificateOutlined />, titleKey: 'menu.roles', hintKey: 'dashboard.setupRoles', permission: 'ROLE_VIEW' },
  { to: '/users', icon: <TeamOutlined />, titleKey: 'menu.users', hintKey: 'dashboard.setupUsers', permission: 'USER_VIEW' },
]

/** What comes next on the roadmap (see docs/plans/BACKLOG.md). */
const COMING = [
  { step: '6d', key: 'dashboard.coming1' },
  { step: '6e', key: 'dashboard.coming2' },
  { step: '8', key: 'dashboard.coming3' },
  { step: '10', key: 'dashboard.coming4' },
]

/**
 * Home page: a greeting, then the owner's numbers for the current plant and period - what was packed and dispatched,
 * how the line ran, what stands in the godown and what needs a look. Each block shows only for those who may see it.
 */
export default function DashboardPage() {
  const { t } = useTranslation()
  const { user, can, currentPlant } = useAuth()
  const [period, setPeriod] = useState<Period>('week')
  const range = rangeOf(period)
  const seesFigures = can('PRODUCTION_VIEW') || can('FG_VIEW') || can('SALES_VIEW') || can('STORE_VIEW')
  const dash = useDashboard(currentPlant && seesFigures ? { plantId: currentPlant.id, from: range.from, to: range.to } : null)
  const d = dash.data

  const hour = new Date().getHours()
  const greeting = t(hour < 12 ? 'dashboard.morning' : hour < 17 ? 'dashboard.afternoon' : 'dashboard.evening')
  const firstName = user?.fullName.split(' ')[0] ?? ''
  const setup = SETUP.filter((s) => can(s.permission))

  return (
    <>
      <motion.section
        className="hero hero-compact"
        initial={{ opacity: 0, y: 16, scale: 0.99 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.55, ease: EASE }}
      >
        <HeroMosaic />
        <div className="hero-eyebrow">{dayjs().format('dddd, D MMMM YYYY')}</div>
        <h1 className="hero-title">{greeting}, {firstName}</h1>
        <p className="hero-sub">{seesFigures && currentPlant ? t('dashboard.introFigures') : t('dashboard.intro')}</p>
        {currentPlant && (
          <span className="hero-chip"><ShopOutlined /> {t('dashboard.currentPlant')}: {currentPlant.name}</span>
        )}
      </motion.section>

      {seesFigures && currentPlant && (
        <>
          <FadeIn delay={0.05} className="toolbar dash-filters">
            <Segmented<Period>
              value={period}
              onChange={setPeriod}
              options={[
                { value: 'today', label: t('dashboard.period.today') },
                { value: 'week', label: t('dashboard.period.week') },
                { value: 'month30', label: t('dashboard.period.month30') },
                { value: 'thisMonth', label: t('dashboard.period.thisMonth') },
              ]}
            />
            <span className="cell-sub">{dayjs(range.from).format('D MMM')} – {dayjs(range.to).format('D MMM YYYY')}</span>
          </FadeIn>
          {d && (
            <div className={`dash${dash.isPlaceholderData ? ' refreshing' : ''}`}>
              <Headline d={d} days={range.days} />
              <Trend d={d} />
              <div className="dash-grid">
                {d.production && <GradeCard d={d} />}
                {d.production && <FunnelCard d={d} />}
                {d.finishedGoods && <StockCard d={d} />}
                <AttentionCard d={d} />
              </div>
            </div>
          )}
        </>
      )}

      <div className="dash-grid dash-bottom">
        {setup.length > 0 && (
          <FadeIn delay={0.2}>
            <h2 className="section-title">{t('dashboard.setupTitle')}</h2>
            <Stagger className="action-grid">
              {setup.map((c) => (
                <StaggerItem key={c.to}>
                  <Link to={c.to} className="action-card">
                    <span className="action-icon">{c.icon}</span>
                    <span style={{ minWidth: 0 }}>
                      <div className="action-title">{t(c.titleKey)}</div>
                      <div className="action-hint">{t(c.hintKey)}</div>
                    </span>
                    <ArrowRightOutlined className="action-arrow" />
                  </Link>
                </StaggerItem>
              ))}
            </Stagger>
          </FadeIn>
        )}
        <FadeIn delay={0.25}>
          <h2 className="section-title"><RocketOutlined style={{ color: 'var(--brand-600)' }} /> {t('dashboard.comingTitle')}</h2>
          <div className="panel">
            <div className="panel-hint">{t('dashboard.roadmapHint')}</div>
            <ol className="roadmap">
              {COMING.map((c) => (
                <li key={c.key}><span className="roadmap-step">{c.step}</span><span>{t(c.key)}</span></li>
              ))}
            </ol>
          </div>
        </FadeIn>
      </div>
    </>
  )
}

// ------------------------------------------------------------------ the numbers

/** The one number the dashboard leads with (m² packed), and the tiles next to it. */
function Headline({ d, days }: { d: DashboardResponse; days: number }) {
  const { t } = useTranslation()
  const p = d.production
  const delta = p && p.sqmPackedBefore > 0 ? ((p.sqmPacked - p.sqmPackedBefore) / p.sqmPackedBefore) * 100 : undefined
  const tiles: { key: string; label: string; value?: number; decimals?: number; suffix?: string; sub: string; warn?: boolean }[] = []
  if (p) {
    tiles.push({ key: 'grade', label: t('dashboard.topGrade', { grade: p.topGradeName ?? '' }), value: p.topGradePct, decimals: 1, suffix: '%',
      sub: t('dashboard.ofBoxes', { n: fmt(p.boxesPacked) }) })
    tiles.push({ key: 'yield', label: t('dashboard.yield'), value: p.yieldPct, decimals: 1, suffix: '%', sub: t('dashboard.yieldSub') })
    tiles.push({ key: 'gas', label: t('dashboard.gas'), value: p.gasPerSqm, decimals: 2, suffix: ' SCM/m²',
      sub: p.gasScm != null ? t('dashboard.gasSub', { n: fmt(p.gasScm), sqm: fmt(p.sqmFired) }) : t('dashboard.noReadings') })
  }
  if (d.dispatch) {
    tiles.push({ key: 'dispatched', label: t('dashboard.dispatched'), value: d.dispatch.sqmDispatched, suffix: ' m²',
      sub: t('dashboard.dispatchedSub', { count: d.dispatch.ordersDispatched, boxes: fmt(d.dispatch.boxesDispatched) }) })
    tiles.push({ key: 'waiting', label: t('dashboard.waiting'), value: d.dispatch.ordersWaiting,
      sub: d.dispatch.boxesNotPlanned
        ? t('dashboard.waitingShort', { boxes: fmt(d.dispatch.boxesWaiting), short: fmt(d.dispatch.boxesNotPlanned) })
        : t('dashboard.waitingSub', { boxes: fmt(d.dispatch.boxesWaiting) }),
      warn: d.dispatch.boxesNotPlanned > 0 })
  }
  if (d.store) {
    tiles.push({ key: 'store', label: t('dashboard.storeValue'), value: d.store.stockValue, suffix: '', sub: t('dashboard.storeSub') })
  }

  return (
    <Stagger className="dash-headline">
      {p && (
        <StaggerItem className="dash-hero">
          <div className="stat-label">{t('dashboard.packed')}</div>
          <div className="dash-hero-value"><CountUp value={p.sqmPacked} /><span> m²</span></div>
          <div className="dash-hero-sub">
            {delta !== undefined ? (
              <span className={`delta ${delta >= 0 ? 'up' : 'down'}`}>
                {delta >= 0 ? <ArrowUpOutlined /> : <ArrowDownOutlined />} {fmt(Math.abs(delta), 1)}%
              </span>
            ) : null}
            <span>{delta !== undefined ? t('dashboard.vsBefore', { count: days }) : t('dashboard.nothingBefore', { count: days })}</span>
          </div>
          <div className="cell-sub">{t('dashboard.boxesPacked', { n: fmt(p.boxesPacked) })}</div>
        </StaggerItem>
      )}
      {tiles.map((tile) => (
        <StaggerItem key={tile.key} className={`dash-tile${tile.warn ? ' warn' : ''}`}>
          <div className="stat-label">{tile.label}</div>
          <div className="dash-tile-value">
            {tile.key === 'store' && '₹ '}
            {tile.value == null ? '–' : <CountUp value={tile.value} decimals={tile.decimals ?? 0} />}
            {tile.value != null && tile.suffix && <span className="dash-unit">{tile.suffix}</span>}
          </div>
          <div className="dash-tile-sub">{tile.warn && <WarningOutlined />} {tile.sub}</div>
        </StaggerItem>
      ))}
    </Stagger>
  )
}

/** Packed and dispatched m² per day: does the godown fill up or empty out? */
function Trend({ d }: { d: DashboardResponse }) {
  const { t } = useTranslation()
  if (d.days.length < 2) return null
  const series: Series[] = []
  if (d.production) series.push({ key: 'packed', label: t('dashboard.seriesPacked'), color: 'var(--viz-1)', values: d.days.map((x) => x.packedSqm ?? 0) })
  if (d.dispatch) series.push({ key: 'dispatched', label: t('dashboard.seriesDispatched'), color: 'var(--viz-2)', values: d.days.map((x) => x.dispatchedSqm ?? 0) })
  if (!series.length) return null
  const title = series.length === 2 ? t('dashboard.trendBoth') : series[0].key === 'packed' ? t('dashboard.trendPacked') : t('dashboard.trendDispatched')
  return (
    <FadeIn delay={0.1}>
      <ChartCard title={title} hint={t('dashboard.trendHint')}
        table={{
          columns: [t('dashboard.date'), ...series.map((s) => `${s.label} (m²)`)],
          rows: d.days.map((x, i) => [dayjs(x.date).format('D MMM'), ...series.map((s) => s.values[i])]),
        }}>
        <TrendChart dates={d.days.map((x) => x.date)} series={series} unit="m²" />
      </ChartCard>
    </FadeIn>
  )
}

function GradeCard({ d }: { d: DashboardResponse }) {
  const { t } = useTranslation()
  const grades = d.production!.grades
  return (
    <FadeIn delay={0.14}>
      <ChartCard title={t('dashboard.gradeTitle')} hint={t('dashboard.gradeHint')}
        table={{ columns: [t('finishing.grade'), t('finishing.boxes'), 'm²', '%'], rows: grades.map((g) => [g.name, g.boxes, g.sqm ?? 0, g.pct ?? 0]) }}>
        {grades.length ? <ShareBar parts={gradeParts(grades, 'boxes')} unit={t('dashboard.boxesWord')} /> : <Empty />}
      </ChartCard>
    </FadeIn>
  )
}

/** Pieces at each stage: the drop from one bar to the next is what each stage lost (or is still working on). */
function FunnelCard({ d }: { d: DashboardResponse }) {
  const { t } = useTranslation()
  const stages = d.production!.stages.filter((s) => s.recorded || s.key !== 'POLISHED')
  const first = stages[0]?.pieces || 0
  const items = stages.map((s, i) => {
    const before = i > 0 ? stages[i - 1].pieces : 0
    const lost = i > 0 && before > 0 ? ((before - s.pieces) / before) * 100 : undefined
    return { key: s.key, label: t(`dashboard.stage.${s.key}`), value: s.pieces, muted: !s.recorded,
      note: lost !== undefined && lost > 0 ? ` −${fmt(lost, 1)}%` : undefined }
  })
  return (
    <FadeIn delay={0.16}>
      <ChartCard title={t('dashboard.funnelTitle')}
        hint={first ? t('dashboard.funnelHint', { pct: fmt(d.production!.yieldPct, 1) }) : t('dashboard.funnelEmpty')}
        table={{ columns: [t('dashboard.stageCol'), t('dashboard.pieces')], rows: items.map((i) => [i.label, i.value]) }}>
        {first ? <BarList items={items} unit={t('dashboard.piecesWord')} /> : <Empty />}
      </ChartCard>
    </FadeIn>
  )
}

/** Finished goods now: how much, how full the godowns are, what is kept for trucks, and the biggest designs. */
function StockCard({ d }: { d: DashboardResponse }) {
  const { t } = useTranslation()
  const fg = d.finishedGoods!
  return (
    <FadeIn delay={0.18}>
      <ChartCard title={t('dashboard.stockTitle')} hint={t('dashboard.stockHint', { boxes: fmt(fg.boxes), sqm: fmt(fg.sqm) })}
        table={{ columns: [t('fg.design'), t('finishing.boxes'), 'm²'], rows: fg.topDesigns.map((x) => [x.name, x.boxes, x.sqm ?? 0]) }}>
        <div className="dash-meters">
          <Meter label={t('dashboard.fill')} value={fg.palletsUsed} max={fg.palletsCapacity}
            detail={fg.palletsCapacity
              ? t('dashboard.fillDetail', { used: fmt(fg.palletsUsed), cap: fmt(fg.palletsCapacity), pillars: fg.pillarsUsed, total: fg.pillarsTotal })
              : t('dashboard.fillUnknown', { used: fmt(fg.palletsUsed) })} />
          <Meter label={t('dashboard.kept')} value={fg.keptBoxes} max={fg.boxes || undefined}
            detail={t('dashboard.keptDetail', { kept: fmt(fg.keptBoxes), free: fmt(Math.max(0, fg.boxes - fg.keptBoxes)) })} />
        </div>
        {fg.topDesigns.length
          ? <BarList items={fg.topDesigns.map((x) => ({ key: x.code, label: x.name, value: x.boxes }))} unit={t('dashboard.boxesWord')} />
          : <Empty />}
      </ChartCard>
    </FadeIn>
  )
}

/** What needs a look today, most urgent first, each with where to go. Status colours always come with an icon. */
function AttentionCard({ d }: { d: DashboardResponse }) {
  const { t } = useTranslation()
  const LINKS = { SHORT_FOR_ORDERS: '/sales/loading', PLAN_TOO_FEW: '/sales/loading', PILLARS_FULL: '/fg/godowns', LOW_MATERIALS: '/store/stock' }
  return (
    <FadeIn delay={0.2}>
      <section className="panel viz-card">
        <header className="viz-head"><h3 className="viz-title"><AlertOutlined /> {t('dashboard.attentionTitle')}</h3></header>
        {d.attention.length === 0 ? (
          <div className="attention-ok"><CheckCircleOutlined /> {t('dashboard.allGood')}</div>
        ) : (
          <ul className="attention">
            {d.attention.map((a, i) => {
              const critical = a.kind === 'PLAN_TOO_FEW' || a.kind === 'SHORT_FOR_ORDERS'
              return (
                <li key={i} className={critical ? 'critical' : 'warning'}>
                  {critical ? <ExclamationCircleOutlined /> : <WarningOutlined />}
                  <Link to={LINKS[a.kind]}>
                    <b>{t(`dashboard.attention.${a.kind}`, { what: a.what, count: a.count ?? 0, boxes: fmt(a.boxes) })}</b>
                    <span>{t(`dashboard.attentionDo.${a.kind}`)}</span>
                  </Link>
                </li>
              )
            })}
          </ul>
        )}
      </section>
    </FadeIn>
  )
}

function Empty() {
  const { t } = useTranslation()
  return <p className="cell-sub viz-empty">{t('dashboard.nothingYet')}</p>
}
