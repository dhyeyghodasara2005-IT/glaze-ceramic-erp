import { useState } from 'react'
import { Alert, Card, DatePicker, Table, Tag, type TableColumnsType } from 'antd'
import { CalendarOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import dayjs from 'dayjs'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn, Stagger, StaggerItem } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import type { LabourContractorMonth, LabourWorkerMonth } from '../../api/types'
import NoPlant from '../store/NoPlant'
import { BarList, ChartCard, fmt } from '../dashboard/charts'
import { useLabourMonth } from './labourApi'
import { rupees } from './labourParts'

/**
 * /labour/month - the month per contractor: each worker's days (present, half, absent, leave), man-days, overtime,
 * and what it comes to at their rates. What the labour office checks against the contractor's bill.
 */
export default function LabourMonthPage() {
  const { t } = useTranslation()
  const { currentPlant } = useAuth()
  const [month, setMonth] = useState(dayjs().format('YYYY-MM'))
  const query = useLabourMonth(currentPlant ? { plantId: currentPlant.id, month } : null)
  const d = query.data
  if (!currentPlant) return <NoPlant />

  const unpriced = d?.contractors.reduce((n, c) => n + c.unpriced, 0) ?? 0
  const workers = d?.contractors.reduce((n, c) => n + c.workers, 0) ?? 0
  const tiles = d ? [
    { key: 'days', label: t('labour.manDays'), value: fmt(d.manDays, 1), sub: t('labour.manDaysSub') },
    { key: 'workers', label: t('labour.workersWorked'), value: fmt(workers), sub: t('labour.contractorsN', { count: d.contractors.filter((c) => c.contractorId != null).length }) },
    { key: 'ot', label: t('labour.otTotal'), value: fmt(d.otHours, 1), unit: 'h', sub: t('labour.otTotalSub') },
    { key: 'amount', label: t('labour.amount'), value: rupees(d.amount), sub: unpriced ? t('labour.amountMissing', { count: unpriced }) : t('labour.amountSub') },
  ] : []

  return (
    <>
      <PageHeader title={t('labour.monthTitle')} subtitle={t('labour.monthSubtitle')} icon={<CalendarOutlined />} />
      <FadeIn delay={0.04} className="toolbar">
        <DatePicker picker="month" value={dayjs(`${month}-01`)} format="MMMM YYYY" allowClear={false} style={{ width: 190 }}
          disabledDate={(m) => m.isAfter(dayjs(), 'month')} onChange={(m) => m && setMonth(m.format('YYYY-MM'))} />
        {d && <span className="cell-sub">{dayjs(d.from).format('D MMM')} – {dayjs(d.to).format('D MMM YYYY')}</span>}
      </FadeIn>

      {d && d.contractors.length === 0 && (
        <div className="panel"><EmptyState title={t('labour.monthEmpty')} hint={t('labour.monthEmptyHint')} /></div>
      )}

      {d && d.contractors.length > 0 && (
        <div className={query.isPlaceholderData ? 'refreshing' : undefined}>
          <Stagger className="dash-headline">
            {tiles.map((tile) => (
              <StaggerItem key={tile.key} className="dash-tile">
                <div className="stat-label">{tile.label}</div>
                <div className="dash-tile-value">{tile.value}{tile.unit && <span className="dash-unit"> {tile.unit}</span>}</div>
                <div className="dash-tile-sub">{tile.sub}</div>
              </StaggerItem>
            ))}
          </Stagger>

          {unpriced > 0 && (
            <Alert type="warning" showIcon className="energy-missing" title={t('labour.unpricedTitle', { count: unpriced })}
              description={t('labour.unpricedHint')} />
          )}

          {d.departments.length > 0 && (
            <FadeIn delay={0.1} className="labour-depts">
              <ChartCard title={t('labour.byDepartment')} hint={t('labour.byDepartmentHint')}
                table={{ columns: [t('labour.department'), t('labour.manDays')], rows: d.departments.map((x) => [t(`labour.dept.${x.department}`), x.manDays]) }}>
                <BarList unit={t('labour.daysWord')} format={(v) => fmt(v, 1)}
                  items={[...d.departments].sort((a, b) => b.manDays - a.manDays)
                    .map((x) => ({ key: x.department, label: t(`labour.dept.${x.department}`), value: x.manDays }))} />
              </ChartCard>
            </FadeIn>
          )}

          {d.contractors.map((c, i) => <ContractorTable key={c.contractorId ?? 'own'} c={c} delay={0.12 + i * 0.03} />)}
        </div>
      )}
    </>
  )
}

/** One contractor's workers for the month, with the total underneath. */
function ContractorTable({ c, delay }: { c: LabourContractorMonth; delay: number }) {
  const { t } = useTranslation()
  const columns: TableColumnsType<LabourWorkerMonth> = [
    { title: t('common.code'), dataIndex: 'code', width: 100, render: (code: string) => <span className="code-badge">{code}</span> },
    {
      title: t('common.name'),
      dataIndex: 'name',
      render: (name: string, w) => (
        <>
          <div className="cell-title">{name}</div>
          <div className="cell-sub">{t(`labour.dept.${w.department}`)}</div>
        </>
      ),
    },
    { title: t('labour.short.PRESENT'), dataIndex: 'present', width: 56, align: 'right', responsive: ['md'] },
    { title: t('labour.short.HALF'), dataIndex: 'half', width: 56, align: 'right', responsive: ['md'] },
    { title: t('labour.short.ABSENT'), dataIndex: 'absent', width: 56, align: 'right', responsive: ['md'] },
    { title: t('labour.short.LEAVE'), dataIndex: 'leave', width: 56, align: 'right', responsive: ['lg'] },
    { title: t('labour.manDays'), dataIndex: 'manDays', width: 100, align: 'right', render: (n: number) => <b>{fmt(n, 1)}</b> },
    { title: t('labour.ot'), dataIndex: 'otHours', width: 80, align: 'right', responsive: ['sm'], render: (n: number) => (n ? fmt(n, 1) : '–') },
    {
      title: t('labour.rates'),
      key: 'rate',
      width: 130,
      align: 'right',
      responsive: ['lg'],
      render: (_, w) => (w.dayRate == null ? <Tag color="orange" bordered={false}>{t('labour.noRate')}</Tag> : (
        <>
          <div>{rupees(w.dayRate)}</div>
          {w.otRate != null && <div className="cell-sub">{t('labour.perOtHour', { n: rupees(w.otRate) })}</div>}
        </>
      )),
    },
    { title: t('labour.amount'), dataIndex: 'amount', width: 120, align: 'right', render: (n: number) => rupees(n) },
  ]
  return (
    <FadeIn delay={delay}>
      <h2 className="section-title labour-contractor">
        {c.name ?? t('labour.ownRolls')}
        <span className="cell-sub">{t('labour.contractorSum', { count: c.workers, days: fmt(c.manDays, 1), ot: fmt(c.otHours, 1), amount: rupees(c.amount) })}</span>
        {c.unpriced > 0 && <Tag color="orange" bordered={false}>{t('labour.unpricedN', { count: c.unpriced })}</Tag>}
      </h2>
      <Card className="data-card labour-month-card" styles={{ body: { padding: 0 } }}>
        <Table<LabourWorkerMonth> rowKey="workerId" columns={columns} dataSource={c.rows} pagination={false} size="small" />
      </Card>
    </FadeIn>
  )
}
