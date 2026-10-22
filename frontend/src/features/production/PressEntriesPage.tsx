import { useEffect, useState } from 'react'
import { Button, Card, DatePicker, Input, Segmented, Table, type TableColumnsType } from 'antd'
import { BuildOutlined, PlusOutlined } from '@ant-design/icons'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import type { Dayjs } from 'dayjs'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import type { DocStatus, PressEntrySummary } from '../../api/types'
import NoPlant from '../store/NoPlant'
import DocStatusTag from '../store/DocStatusTag'
import { DOC_STATUSES } from '../store/docTypes'
import { qty, shortDate } from '../store/format'
import { usePressEntries } from './pressApi'

const BASE = '/production/press-entries'
type StatusFilter = DocStatus | 'ALL'

export default function PressEntriesPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const { can, currentPlant } = useAuth()
  const [search, setSearch] = useState<string>()
  const [status, setStatus] = useState<StatusFilter>('ALL')
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const { data, isFetching } = usePressEntries(currentPlant ? {
    plantId: currentPlant.id,
    status: status === 'ALL' ? undefined : status,
    from: range?.[0]?.format('YYYY-MM-DD'),
    to: range?.[1]?.format('YYYY-MM-DD'),
    search,
    page,
    size: pageSize,
  } : null)

  useEffect(() => {
    const open = params.get('open')
    if (open) navigate(`${BASE}/${open}`, { replace: true })
    else if (params.get('new') === '1') navigate(`${BASE}/new`, { replace: true })
  }, [params, navigate])

  if (!currentPlant) return <NoPlant />

  const newButton = can('PRESS_ENTRY') && (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate(`${BASE}/new`)}>{t('press.newEntry')}</Button>
  )

  const columns: TableColumnsType<PressEntrySummary> = [
    {
      title: t('press.entryNo'),
      dataIndex: 'entryNo',
      width: 200,
      render: (no?: string) => (no ? <span className="code-badge">{no}</span> : <span className="cell-sub">{t('store.status.DRAFT')}</span>),
    },
    {
      title: t('prod.prodDate'),
      dataIndex: 'prodDate',
      width: 130,
      render: (d: string, e) => (
        <>
          <div>{shortDate(d)}</div>
          <div className="cell-sub">{t('prod.shift')} {e.shiftCode}</div>
        </>
      ),
    },
    {
      title: t('press.lot'),
      key: 'lot',
      render: (_, e) => (
        <>
          <div className="cell-title">{e.designName}</div>
          <div className="cell-sub">{e.lotNo} · {e.pressCode} · {e.siloCode}</div>
        </>
      ),
    },
    {
      title: t('press.good'),
      dataIndex: 'goodCount',
      width: 150,
      align: 'right',
      render: (good: number, e) => (
        <>
          <div className="cell-title">{good.toLocaleString('en-IN')}</div>
          <div className="cell-sub">{e.pressedCount.toLocaleString('en-IN')} {t('press.pressed').toLowerCase()} · {qty(e.powderTon, 1)} T</div>
        </>
      ),
    },
    { title: t('common.status'), dataIndex: 'status', width: 120, render: (s: DocStatus) => <DocStatusTag status={s} /> },
  ]

  return (
    <>
      <PageHeader title={t('press.entriesTitle')} subtitle={t('press.entriesSubtitle')} icon={<BuildOutlined />} extra={newButton} />
      <FadeIn delay={0.05} className="toolbar">
        <Input.Search allowClear placeholder={t('press.searchEntries')} style={{ maxWidth: 280 }}
          onSearch={(v) => {
            setSearch(v.trim() || undefined)
            setPage(0)
          }} />
        <DatePicker.RangePicker format="DD-MM-YYYY" value={range} allowEmpty={[true, true]}
          onChange={(v) => {
            setRange(v)
            setPage(0)
          }} />
        <Segmented<StatusFilter>
          value={status}
          onChange={(v) => {
            setStatus(v)
            setPage(0)
          }}
          options={[{ value: 'ALL', label: t('common.all') }, ...DOC_STATUSES.map((s) => ({ value: s, label: t(`store.status.${s}`) }))]}
        />
      </FadeIn>
      <FadeIn delay={0.1}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<PressEntrySummary>
            rowKey="id"
            columns={columns}
            dataSource={data?.content}
            loading={isFetching}
            rowClassName={(e) => `clickable-row${e.status === 'CANCELLED' ? ' row-cancelled' : ''}`}
            onRow={(e) => ({ onClick: () => navigate(`${BASE}/${e.id}`) })}
            locale={{ emptyText: isFetching ? ' ' : <EmptyState title={t('press.emptyEntries')} action={newButton} /> }}
            pagination={{
              current: page + 1,
              pageSize,
              total: data?.totalElements,
              showSizeChanger: true,
              hideOnSinglePage: true,
              onChange: (p, s) => {
                setPage(s !== pageSize ? 0 : p - 1)
                setPageSize(s)
              },
            }}
          />
        </Card>
      </FadeIn>
    </>
  )
}
