import { useCallback, useState } from 'react'
import { Button, Card, DatePicker, Input, Segmented, Table, type TableColumnsType } from 'antd'
import { AlertOutlined, CheckCircleOutlined, ToolOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import type { Dayjs } from 'dayjs'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useOpenFromUrl } from '../../components/useOpenFromUrl'
import { useAuth } from '../../auth/AuthContext'
import { BREAKDOWN_STATUSES, type BreakdownStatus, type BreakdownSummary } from '../../api/types'
import NoPlant from '../store/NoPlant'
import { dayClock, duration } from '../production/time'
import { useNow } from '../production/useNow'
import { api } from '../../api/client'
import { useBreakdowns } from './maintenanceApi'
import { BreakdownStatusTag, CategoryTag, minutesText } from './maintenanceParts'
import BreakdownDrawer from './BreakdownDrawer'
import ReportBreakdownModal from './ReportBreakdownModal'

type StatusFilter = BreakdownStatus | 'ALL'

const fetchBreakdown = async (id: number) => (await api.get<BreakdownSummary>(`/maintenance/breakdowns/${id}`)).data

/**
 * /maintenance/breakdowns - machines down right now (big red cards that keep counting), then every breakdown.
 * Supervisors report from here; the maintenance team opens a card to close it.
 */
export default function BreakdownsPage() {
  const { t } = useTranslation()
  const { can, currentPlant } = useAuth()
  useNow(30_000)
  const [status, setStatus] = useState<StatusFilter>('ALL')
  const [search, setSearch] = useState<string>()
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null)
  const [page, setPage] = useState(0)
  const [reporting, setReporting] = useState(false)
  const [openId, setOpenId] = useState<number>()
  const mayReport = can('BREAKDOWN_REPORT') || can('MAINTENANCE_ENTRY')

  const down = useBreakdowns(currentPlant ? { plantId: currentPlant.id, status: 'OPEN', page: 0, size: 50 } : null)
  const list = useBreakdowns(currentPlant ? {
    plantId: currentPlant.id,
    status: status === 'ALL' ? undefined : status,
    from: range?.[0]?.format('YYYY-MM-DD'),
    to: range?.[1]?.format('YYYY-MM-DD'),
    search,
    page,
    size: 20,
  } : null)

  // ?new=1 (search palette) opens the report; ?open=12 opens that breakdown
  const openFromUrl = useCallback((item?: BreakdownSummary) => {
    if (item) setOpenId(item.id)
    else if (mayReport) setReporting(true)
  }, [mayReport])
  useOpenFromUrl({ fetchOne: fetchBreakdown, onOpen: openFromUrl })

  if (!currentPlant) return <NoPlant />

  const reportButton = mayReport && (
    <Button type="primary" danger icon={<AlertOutlined />} onClick={() => setReporting(true)}>{t('maint.report')}</Button>
  )
  const columns: TableColumnsType<BreakdownSummary> = [
    {
      title: t('maint.no'),
      dataIndex: 'breakdownNo',
      width: 230,
      render: (no: string, b) => (<><span className="code-badge">{no}</span><div className="cell-sub">{dayClock(b.stoppedAt)}</div></>),
    },
    {
      title: t('maint.machine'),
      key: 'machine',
      render: (_, b) => (
        <>
          <div className="cell-title">{b.machine.name}</div>
          <div className="cell-sub">{b.symptom}{b.failedPart ? ` · ${b.failedPart}` : ''}</div>
        </>
      ),
    },
    { title: t('maint.category'), dataIndex: 'category', width: 140, responsive: ['md'], render: (c) => <CategoryTag category={c} /> },
    {
      title: t('maint.downtime'),
      dataIndex: 'downtimeMinutes',
      width: 120,
      align: 'right',
      render: (m: number, b) => <b>{b.status === 'OPEN' ? duration(b.stoppedAt) : minutesText(m)}</b>,
    },
    { title: t('common.status'), dataIndex: 'status', width: 130, render: (s: BreakdownStatus) => <BreakdownStatusTag status={s} /> },
  ]

  return (
    <>
      <PageHeader title={t('maint.title')} subtitle={t('maint.subtitle')} icon={<ToolOutlined />} extra={reportButton} />

      <FadeIn delay={0.04}>
        <h2 className="section-title">{t('maint.downNow')}</h2>
        {down.data && down.data.content.length === 0 ? (
          <div className="panel bd-all-running"><CheckCircleOutlined /> {t('maint.allRunning')}</div>
        ) : (
          <div className="bd-down-cards">
            {down.data?.content.map((b) => (
              <button key={b.id} type="button" className="bd-down-card" onClick={() => setOpenId(b.id)}>
                <span className="bd-pulse" aria-hidden />
                <div className="bd-down-machine">{b.machine.name}</div>
                <div className="bd-down-time">{duration(b.stoppedAt)}</div>
                <div className="cell-sub">{t('maint.since', { time: dayClock(b.stoppedAt), by: b.reportedBy })}</div>
                <div className="bd-down-symptom">{b.symptom}</div>
                {can('MAINTENANCE_ENTRY') && <span className="bd-down-cta">{t('maint.openToClose')}</span>}
              </button>
            ))}
          </div>
        )}
      </FadeIn>

      <FadeIn delay={0.08} className="toolbar">
        <Input.Search allowClear placeholder={t('maint.search')} style={{ maxWidth: 280 }}
          onSearch={(v) => { setSearch(v.trim() || undefined); setPage(0) }} />
        <DatePicker.RangePicker format="DD-MM-YYYY" value={range} allowEmpty={[true, true]}
          onChange={(v) => { setRange(v); setPage(0) }} />
        <Segmented<StatusFilter> value={status} onChange={(v) => { setStatus(v); setPage(0) }}
          options={[{ value: 'ALL', label: t('common.all') }, ...BREAKDOWN_STATUSES.map((s) => ({ value: s, label: t(`maint.status.${s}`) }))]} />
      </FadeIn>
      <FadeIn delay={0.12}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<BreakdownSummary>
            rowKey="id"
            columns={columns}
            dataSource={list.data?.content}
            loading={list.isFetching}
            rowClassName={(b) => `clickable-row${b.status === 'CANCELLED' ? ' row-cancelled' : ''}`}
            onRow={(b) => ({ onClick: () => setOpenId(b.id) })}
            locale={{ emptyText: list.isFetching ? ' ' : <EmptyState title={t('maint.empty')} action={reportButton} /> }}
            pagination={{ current: page + 1, pageSize: 20, total: list.data?.totalElements, hideOnSinglePage: true, onChange: (p) => setPage(p - 1) }}
          />
        </Card>
      </FadeIn>

      <ReportBreakdownModal open={reporting} plantId={currentPlant.id} onClose={() => setReporting(false)} />
      <BreakdownDrawer id={openId} onClose={() => setOpenId(undefined)} />
    </>
  )
}
