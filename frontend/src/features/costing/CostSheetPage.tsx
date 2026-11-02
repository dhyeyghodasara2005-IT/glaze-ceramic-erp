import { useState } from 'react'
import { Alert, Button, Card, DatePicker, Table, Tag, type TableColumnsType } from 'antd'
import { CalculatorOutlined, EditOutlined, WarningOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import dayjs from 'dayjs'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn, Stagger, StaggerItem } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import { COST_SECTIONS, type CostLine, type CostSheet } from '../../api/types'
import NoPlant from '../store/NoPlant'
import { BarList, ChartCard, fmt } from '../dashboard/charts'
import { useUnit } from '../energy/energyParts'
import { rupees } from '../labour/labourParts'
import { useCostSheet, useCostTrend } from './costingApi'
import MonthlyCostDrawer from './MonthlyCostDrawer'

/**
 * /costing/sheet - what a m² cost in a month: what left the store, the energy at the rate of its day, the workers'
 * days and the monthly costs, divided by the m² packed. Whatever makes the figure too low is said next to it.
 */
export default function CostSheetPage() {
  const { t } = useTranslation()
  const { can, currentPlant } = useAuth()
  const unit = useUnit()
  const [month, setMonth] = useState(dayjs().format('YYYY-MM'))
  const [editing, setEditing] = useState(false)
  const query = useCostSheet(currentPlant ? { plantId: currentPlant.id, month } : null)
  const trend = useCostTrend(currentPlant?.id)
  const s = query.data
  if (!currentPlant) return <NoPlant />

  const label = (l: CostLine) => (l.section === 'MATERIAL' ? l.name ?? l.code
    : l.section === 'ENERGY' ? t(`energy.kinds.${l.code}`)
      : l.section === 'LABOUR' ? t(`costing.labour.${l.code}`) : t(`costing.heads.${l.code}`))

  const columns: TableColumnsType<CostLine> = [
    {
      title: t('costing.head'),
      key: 'head',
      render: (_, l) => (
        <>
          <div className="cell-title">{label(l)}</div>
          <LineFlags line={l} />
        </>
      ),
    },
    {
      title: t('costing.used'),
      key: 'used',
      width: 170,
      align: 'right',
      responsive: ['md'],
      render: (_, l) => (l.quantity == null ? '–'
        : l.section === 'LABOUR' ? t('costing.manDaysN', { n: fmt(l.quantity, 1) }) : `${fmt(l.quantity, 2)} ${l.unit ? unit(l.unit) : ''}`),
    },
    { title: t('costing.rate'), key: 'rate', width: 110, align: 'right', responsive: ['lg'], render: (_, l) => (l.rate == null ? '–' : `₹ ${fmt(l.rate, 4)}`) },
    { title: t('costing.amount'), dataIndex: 'amount', width: 140, align: 'right', render: (n: number) => <b>{rupees(n)}</b> },
    { title: t('costing.perSqm'), dataIndex: 'perSqm', width: 100, align: 'right', render: (n?: number) => (n == null ? '–' : rupees(n)) },
    { title: t('costing.share'), dataIndex: 'sharePct', width: 80, align: 'right', responsive: ['sm'], render: (n?: number) => (n == null ? '–' : `${fmt(n, 1)}%`) },
  ]

  const trendPoints = (trend.data ?? []).filter((p) => p.perSqm != null)
  return (
    <>
      <PageHeader title={t('costing.sheetTitle')} subtitle={t('costing.sheetSubtitle')} icon={<CalculatorOutlined />}
        extra={can('COST_MANAGE') && <Button type="primary" icon={<EditOutlined />} onClick={() => setEditing(true)}>{t('costing.enterMonthly')}</Button>} />
      <FadeIn delay={0.04} className="toolbar">
        <DatePicker picker="month" value={dayjs(`${month}-01`)} format="MMMM YYYY" allowClear={false} style={{ width: 190 }}
          disabledDate={(m) => m.isAfter(dayjs(), 'month')} onChange={(m) => m && setMonth(m.format('YYYY-MM'))} />
        {s && <span className="cell-sub">{dayjs(s.from).format('D MMM')} – {dayjs(s.to).format('D MMM YYYY')}</span>}
        <Link to="/costing/rates"><Button>{t('costing.ratesTitle')}</Button></Link>
      </FadeIn>

      {s && s.lines.length === 0 && (
        <div className="panel"><EmptyState title={t('costing.empty')} hint={t('costing.emptyHint')} /></div>
      )}
      {s && s.lines.length > 0 && <Sheet s={s} columns={columns} label={label} />}

      {trendPoints.length > 1 && (
        <FadeIn delay={0.2} className="cost-trend">
          <ChartCard title={t('costing.trendTitle')} hint={t('costing.trendHint')}
            table={{ columns: [t('costing.month'), t('costing.perSqm')], rows: trendPoints.map((p) => [dayjs(`${p.month}-01`).format('MMM YYYY'), p.perSqm ?? 0]) }}>
            <BarList unit="₹/m²" format={(v) => fmt(v, 2)}
              items={trendPoints.map((p) => ({ key: p.month, label: dayjs(`${p.month}-01`).format('MMM YYYY'), value: p.perSqm ?? 0 }))} />
          </ChartCard>
        </FadeIn>
      )}
      <MonthlyCostDrawer open={editing} plantId={currentPlant.id} month={month} onClose={() => setEditing(false)} />
    </>
  )
}

/** The tiles, what makes the figures unsure, what the money went on, and every line by section. */
function Sheet({ s, columns, label }: { s: CostSheet; columns: TableColumnsType<CostLine>; label: (l: CostLine) => string }) {
  const { t } = useTranslation()
  const unsure = s.lines.filter((l) => l.unpricedQuantity != null || l.unpricedWorkers != null || l.missingReadings != null || l.fromSections)
  const tiles = [
    { key: 'sqm', label: t('costing.tilePerSqm'), value: s.perSqm == null ? '–' : rupees(s.perSqm), sub: t('costing.tilePerSqmSub', { sqm: fmt(s.sqmPacked, 1) }) },
    { key: 'box', label: t('costing.tilePerBox'), value: s.perBox == null ? '–' : rupees(s.perBox), sub: t('costing.tilePerBoxSub', { boxes: fmt(s.boxesPacked) }) },
    { key: 'total', label: t('costing.tileTotal'), value: rupees(s.total), sub: t('costing.tileTotalSub') },
  ]
  const bySection = COST_SECTIONS.flatMap((section) => {
    const lines = s.lines.filter((l) => l.section === section)
    return lines.length ? [{ section, lines, amount: lines.reduce((n, l) => n + l.amount, 0) }] : []
  })
  return (
    <>
      <Stagger className="dash-headline">
        {tiles.map((tile) => (
          <StaggerItem key={tile.key} className="dash-tile">
            <div className="stat-label">{tile.label}</div>
            <div className="dash-tile-value">{tile.value}</div>
            <div className="dash-tile-sub">{tile.sub}</div>
          </StaggerItem>
        ))}
      </Stagger>

      {unsure.length > 0 && (
        <Alert type="warning" showIcon className="energy-missing" title={t('costing.unsureTitle')}
          description={t('costing.unsureHint', { what: unsure.map(label).join(', ') })} />
      )}
      {s.perSqm == null && <Alert type="info" showIcon className="energy-missing" title={t('costing.nothingPacked')} />}
      {s.running && <p className="round-note">{t('costing.runningNote')}</p>}
      {s.fuelInStore != null && <p className="round-note">{t('costing.fuelNote', { n: rupees(s.fuelInStore) })}</p>}

      <FadeIn delay={0.1} className="cost-trend">
        <ChartCard title={t('costing.bySection')} hint={t('costing.bySectionHint')}
          table={{ columns: [t('costing.section'), t('costing.amount')], rows: bySection.map((x) => [t(`costing.sections.${x.section}`), x.amount]) }}>
          <BarList format={(v) => rupees(v)}
            items={bySection.map((x) => ({ key: x.section, label: t(`costing.sections.${x.section}`), value: x.amount }))} />
        </ChartCard>
      </FadeIn>

      {bySection.map((x, i) => (
        <FadeIn key={x.section} delay={0.12 + i * 0.03}>
          <h2 className="section-title labour-contractor">
            {t(`costing.sections.${x.section}`)} <span className="cell-sub">{rupees(x.amount)}</span>
          </h2>
          <Card className="data-card labour-month-card" styles={{ body: { padding: 0 } }}>
            <Table<CostLine> rowKey={(l) => `${l.code}/${l.unit ?? ''}`} columns={columns} dataSource={x.lines} pagination={false} size="small" />
          </Card>
        </FadeIn>
      ))}
    </>
  )
}

/** Under a line: why its amount may be low. Always a word beside the colour. */
function LineFlags({ line }: { line: CostLine }) {
  const { t } = useTranslation()
  const unit = useUnit()
  return (
    <>
      {line.unpricedQuantity != null && (
        <Tag color="orange" bordered={false} icon={<WarningOutlined />}>
          {t('costing.noRateFor', { n: fmt(line.unpricedQuantity, 2), unit: line.unit ? unit(line.unit) : '' })}
        </Tag>
      )}
      {line.unpricedWorkers != null && <Tag color="orange" bordered={false} icon={<WarningOutlined />}>{t('labour.unpricedN', { count: line.unpricedWorkers })}</Tag>}
      {line.missingReadings != null && <Tag color="orange" bordered={false} icon={<WarningOutlined />}>{t('costing.missingN', { count: line.missingReadings })}</Tag>}
      {line.fromSections && <Tag color="orange" bordered={false} icon={<WarningOutlined />}>{t('costing.fromSections')}</Tag>}
    </>
  )
}
