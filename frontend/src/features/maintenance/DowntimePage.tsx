import { useState } from 'react'
import { Card, Segmented, Table, Tag, type TableColumnsType } from 'antd'
import { DashboardOutlined, WarningOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import dayjs from 'dayjs'
import PageHeader from '../../components/PageHeader'
import { FadeIn, Stagger, StaggerItem } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import type { DowntimeResponse } from '../../api/types'
import NoPlant from '../store/NoPlant'
import { dayClock } from '../production/time'
import { BarList, ChartCard, fmt } from '../dashboard/charts'
import { useDowntime } from './maintenanceApi'
import { minutesText } from './maintenanceParts'
import BreakdownDrawer from './BreakdownDrawer'

type Period = 'week' | 'month30' | 'thisMonth' | 'quarter'

function rangeOf(period: Period) {
  const today = dayjs()
  const from = period === 'week' ? today.subtract(6, 'day')
    : period === 'month30' ? today.subtract(29, 'day')
      : period === 'thisMonth' ? today.startOf('month') : today.subtract(89, 'day')
  return { from: from.format('YYYY-MM-DD'), to: today.format('YYYY-MM-DD') }
}

type Row = DowntimeResponse['machines'][number]

/**
 * /maintenance/downtime - which machines lose the most time, how long repairs take (MTTR) and how long machines run
 * between breakdowns (MTBF). The numbers predictive maintenance will start from.
 */
export default function DowntimePage() {
  const { t } = useTranslation()
  const { currentPlant } = useAuth()
  const [period, setPeriod] = useState<Period>('month30')
  const [openId, setOpenId] = useState<number>()
  const range = rangeOf(period)
  const query = useDowntime(currentPlant ? { plantId: currentPlant.id, ...range } : null)
  const d = query.data
  if (!currentPlant) return <NoPlant />

  const availability = d && d.machines.length
    ? 100 - (d.downtimeMinutes * 100) / (d.periodMinutes * d.machines.length) : undefined
  const columns: TableColumnsType<Row> = [
    {
      title: t('maint.machine'),
      key: 'machine',
      render: (_, r) => (
        <>
          <div className="cell-title">{r.machine.name} {r.downNow && <Tag color="red" bordered={false}>{t('maint.status.OPEN')}</Tag>}</div>
          <div className="cell-sub">{r.machine.code} · {t(`machineType.${r.machine.type}`)}</div>
        </>
      ),
    },
    { title: t('maint.breakdowns'), dataIndex: 'breakdowns', width: 110, align: 'right' },
    { title: t('maint.downtime'), dataIndex: 'downtimeMinutes', width: 120, align: 'right', render: (m: number) => <b>{minutesText(m)}</b> },
    { title: t('maint.mttr'), dataIndex: 'mttrMinutes', width: 110, align: 'right', responsive: ['md'], render: (m?: number) => minutesText(m) },
    { title: t('maint.mtbf'), dataIndex: 'mtbfHours', width: 110, align: 'right', responsive: ['md'], render: (h?: number) => (h == null ? '–' : `${fmt(h, 1)} h`) },
    { title: t('maint.availability'), dataIndex: 'availabilityPct', width: 120, align: 'right', render: (p?: number) => (p == null ? '–' : `${fmt(p, 1)}%`) },
  ]

  return (
    <>
      <PageHeader title={t('maint.downtimeTitle')} subtitle={t('maint.downtimeSubtitle')} icon={<DashboardOutlined />} />
      <FadeIn delay={0.04} className="toolbar">
        <Segmented<Period> value={period} onChange={setPeriod} options={[
          { value: 'week', label: t('dashboard.period.week') },
          { value: 'month30', label: t('dashboard.period.month30') },
          { value: 'thisMonth', label: t('dashboard.period.thisMonth') },
          { value: 'quarter', label: t('maint.period90') },
        ]} />
        <span className="cell-sub">{dayjs(range.from).format('D MMM')} – {dayjs(range.to).format('D MMM YYYY')}</span>
      </FadeIn>

      {d && (
        <div className={`dash${query.isPlaceholderData ? ' refreshing' : ''}`}>
          <Stagger className="dash-headline">
            <StaggerItem className="dash-tile">
              <div className="stat-label">{t('maint.availability')}</div>
              <div className="dash-tile-value">{availability == null ? '–' : fmt(availability, 1)}<span className="dash-unit">%</span></div>
              <div className="dash-tile-sub">{t('maint.availabilitySub')}</div>
            </StaggerItem>
            <StaggerItem className="dash-tile">
              <div className="stat-label">{t('maint.downtime')}</div>
              <div className="dash-tile-value">{minutesText(d.downtimeMinutes)}</div>
              <div className="dash-tile-sub">{t('maint.breakdownsN', { count: d.breakdowns })}</div>
            </StaggerItem>
            <StaggerItem className={`dash-tile${d.downNow.length ? ' warn' : ''}`}>
              <div className="stat-label">{t('maint.downNow')}</div>
              <div className="dash-tile-value">{d.downNow.length}</div>
              <div className="dash-tile-sub">
                {d.downNow.length ? <><WarningOutlined /> {d.downNow.map((x) => x.machine.name).join(', ')}</> : t('maint.allRunning')}
              </div>
            </StaggerItem>
          </Stagger>

          {d.downNow.length > 0 && (
            <ul className="attention bd-now-list">
              {d.downNow.map((x) => (
                <li key={x.id} className="critical">
                  <WarningOutlined />
                  <button type="button" className="link-button" onClick={() => setOpenId(x.id)}>
                    <b>{x.machine.name}: {t('maint.downFor', { time: minutesText(x.minutes) })}</b>
                    <span>{x.symptom} · {t('maint.since', { time: dayClock(x.stoppedAt), by: x.breakdownNo })}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}

          <div className="dash-grid">
            <ChartCard title={t('maint.byMachine')} hint={t('maint.byMachineHint')}
              table={{ columns: [t('maint.machine'), t('maint.breakdowns'), t('maint.minutes')],
                rows: d.machines.map((r) => [r.machine.name, r.breakdowns, r.downtimeMinutes]) }}>
              {d.downtimeMinutes > 0
                ? <BarList items={d.machines.filter((r) => r.downtimeMinutes > 0).slice(0, 8)
                    .map((r) => ({ key: String(r.machine.id), label: r.machine.name, value: r.downtimeMinutes }))}
                    format={minutesText} />
                : <p className="cell-sub viz-empty">{t('maint.noDowntime')}</p>}
            </ChartCard>
            <ChartCard title={t('maint.byCategory')} hint={t('maint.byCategoryHint')}
              table={{ columns: [t('maint.category'), t('maint.breakdowns'), t('maint.minutes')],
                rows: d.categories.map((c) => [t(`maint.categories.${c.category}`), c.breakdowns, c.minutes]) }}>
              {d.categories.length
                ? <BarList color="var(--viz-2)" items={d.categories.map((c) => ({ key: c.category, label: t(`maint.categories.${c.category}`), value: c.minutes }))}
                    format={minutesText} />
                : <p className="cell-sub viz-empty">{t('maint.noDowntime')}</p>}
            </ChartCard>
          </div>

          <FadeIn delay={0.12}>
            <Card className="data-card dash-table" styles={{ body: { padding: 0 } }}>
              <Table<Row> rowKey={(r) => r.machine.id} columns={columns} dataSource={d.machines} pagination={false} />
            </Card>
          </FadeIn>
        </div>
      )}
      <BreakdownDrawer id={openId} onClose={() => setOpenId(undefined)} />
    </>
  )
}
