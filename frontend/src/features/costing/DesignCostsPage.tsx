import { useState } from 'react'
import { Alert, Button, Card, DatePicker, Table, Tag, type TableColumnsType } from 'antd'
import { PictureOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import dayjs from 'dayjs'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn, Stagger, StaggerItem } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import type { DesignCostRow } from '../../api/types'
import NoPlant from '../store/NoPlant'
import { BarList, ChartCard, fmt } from '../dashboard/charts'
import { rupees } from '../labour/labourParts'
import { useDesignCosts } from './costingApi'

/** An amount as ₹ per m² of the design, or a dash when none of it was packed. */
const perSqm = (amount: number, r: DesignCostRow) => (r.perSqm == null || !r.sqm ? '–' : fmt(amount / r.sqm, 2))

/**
 * /costing/designs - the month's cost shared out per design, each cost by what shows who used it. An estimate, and
 * the page says so: the designs add up to the plant's total, but nobody weighs the glaze per design.
 */
export default function DesignCostsPage() {
  const { t } = useTranslation()
  const { currentPlant } = useAuth()
  const [month, setMonth] = useState(dayjs().format('YYYY-MM'))
  const query = useDesignCosts(currentPlant ? { plantId: currentPlant.id, month } : null)
  const d = query.data
  if (!currentPlant) return <NoPlant />

  const compared = (r: DesignCostRow) => {
    if (r.perSqm == null || !d?.perSqm) return null
    const pct = ((r.perSqm - d.perSqm) / d.perSqm) * 100
    const text = `${pct > 0 ? '+' : pct < 0 ? '−' : ''}${fmt(Math.abs(pct), 1)}%`
    return <Tag color={pct > 0 ? 'orange' : 'green'} bordered={false}>{text}</Tag>
  }
  const columns: TableColumnsType<DesignCostRow> = [
    {
      title: t('costing.design'),
      key: 'design',
      render: (_, r) => (
        <>
          <div className="cell-title">{r.name}</div>
          <div className="cell-sub">
            {r.code} · {r.perSqm == null ? t('costing.notPackedYet') : t('costing.packedN', { sqm: fmt(r.sqm, 1), boxes: fmt(r.boxes) })}
          </div>
        </>
      ),
    },
    { title: t('costing.partBody'), key: 'body', width: 76, align: 'right', responsive: ['lg'], render: (_, r) => perSqm(r.body, r) },
    { title: t('costing.partGlaze'), key: 'glaze', width: 76, align: 'right', responsive: ['lg'], render: (_, r) => perSqm(r.glaze, r) },
    { title: t('costing.partInk'), key: 'ink', width: 76, align: 'right', responsive: ['lg'], render: (_, r) => perSqm(r.ink, r) },
    { title: t('costing.partPacking'), key: 'packing', width: 80, align: 'right', responsive: ['lg'], render: (_, r) => perSqm(r.packing, r) },
    {
      title: t('costing.partRest'),
      key: 'rest',
      width: 86,
      align: 'right',
      responsive: ['lg'],
      render: (_, r) => perSqm(r.otherMaterial + r.energy + r.labour + r.monthly, r),
    },
    { title: t('costing.perSqm'), dataIndex: 'perSqm', width: 104, align: 'right', render: (n?: number) => <b>{n == null ? '–' : rupees(n)}</b> },
    { title: t('costing.perBoxCol'), dataIndex: 'perBox', width: 104, align: 'right', responsive: ['sm'], render: (n?: number) => (n == null ? '–' : rupees(n)) },
    { title: t('costing.vsPlant'), key: 'vs', width: 92, align: 'right', responsive: ['md'], render: (_, r) => compared(r) },
    { title: t('costing.amount'), dataIndex: 'total', width: 124, align: 'right', responsive: ['xl'], render: (n: number) => rupees(n) },
  ]

  const priced = (d?.designs ?? []).filter((r) => r.perSqm != null)
  const dearest = priced[0]
  const cheapest = priced.length > 1 ? priced[priced.length - 1] : undefined
  return (
    <>
      <PageHeader title={t('costing.designsTitle')} subtitle={t('costing.designsSubtitle')} icon={<PictureOutlined />} />
      <FadeIn delay={0.04} className="toolbar">
        <DatePicker picker="month" value={dayjs(`${month}-01`)} format="MMMM YYYY" allowClear={false} style={{ width: 190 }}
          disabledDate={(m) => m.isAfter(dayjs(), 'month')} onChange={(m) => m && setMonth(m.format('YYYY-MM'))} />
        {d && <span className="cell-sub">{dayjs(d.from).format('D MMM')} – {dayjs(d.to).format('D MMM YYYY')}</span>}
        <Link to="/costing/sheet"><Button>{t('costing.sheetTitle')}</Button></Link>
      </FadeIn>

      {d && d.designs.length === 0 && (
        <div className="panel"><EmptyState title={t('costing.designsEmpty')} hint={t('costing.designsEmptyHint')} /></div>
      )}

      {d && d.designs.length > 0 && (
        <div className={query.isPlaceholderData ? 'refreshing' : undefined}>
          <Stagger className="dash-headline">
            <StaggerItem className="dash-tile">
              <div className="stat-label">{t('costing.tilePlant')}</div>
              <div className="dash-tile-value">{d.perSqm == null ? '–' : rupees(d.perSqm)}</div>
              <div className="dash-tile-sub">{t('costing.tilePlantSub', { n: d.designs.length })}</div>
            </StaggerItem>
            {dearest && (
              <StaggerItem className="dash-tile">
                <div className="stat-label">{t('costing.tileDearest')}</div>
                <div className="dash-tile-value">{rupees(dearest.perSqm)}</div>
                <div className="dash-tile-sub">{dearest.name}</div>
              </StaggerItem>
            )}
            {cheapest && (
              <StaggerItem className="dash-tile">
                <div className="stat-label">{t('costing.tileCheapest')}</div>
                <div className="dash-tile-value">{rupees(cheapest.perSqm)}</div>
                <div className="dash-tile-sub">{cheapest.name}</div>
              </StaggerItem>
            )}
          </Stagger>

          <Alert type="info" showIcon className="energy-missing" title={t('costing.estimateTitle')} description={t('costing.estimateHint')} />
          {(d.sharedByArea.length > 0 || d.glazeEntriesWithoutUsage > 0) && (
            <Alert type="warning" showIcon className="energy-missing" title={t('costing.lessSureTitle')}
              description={
                <>
                  {d.sharedByArea.length > 0 && <div>{t('costing.sharedByArea', { names: d.sharedByArea.join(', ') })}</div>}
                  {d.glazeEntriesWithoutUsage > 0 && <div>{t('costing.glazeMissing', { count: d.glazeEntriesWithoutUsage })}</div>}
                </>
              } />
          )}
          {d.unshared > 0 && <p className="round-note">{t('costing.unsharedNote', { n: rupees(d.unshared) })}</p>}
          {d.running && <p className="round-note">{t('costing.runningNote')}</p>}

          {priced.length > 1 && (
            <FadeIn delay={0.1} className="cost-trend">
              <ChartCard title={t('costing.designsChart')} hint={t('costing.designsChartHint')}
                table={{ columns: [t('costing.design'), t('costing.perSqm')], rows: priced.map((r) => [r.name, r.perSqm ?? 0]) }}>
                <BarList unit="₹/m²" format={(v) => fmt(v, 2)}
                  items={priced.map((r) => ({ key: r.code, label: r.name, value: r.perSqm ?? 0 }))} />
              </ChartCard>
            </FadeIn>
          )}

          <FadeIn delay={0.14}>
            <h2 className="section-title">{t('costing.designsTable')}</h2>
            <Card className="data-card labour-month-card" styles={{ body: { padding: 0 } }}>
              <Table<DesignCostRow> rowKey="designId" columns={columns} dataSource={d.designs} pagination={false} size="small" />
            </Card>
          </FadeIn>
        </div>
      )}
    </>
  )
}
