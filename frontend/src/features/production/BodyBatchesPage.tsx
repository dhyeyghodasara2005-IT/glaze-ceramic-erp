import { useEffect, useState } from 'react'
import { Button, Card, DatePicker, Input, Select, Table, type TableColumnsType } from 'antd'
import { PlusOutlined, SyncOutlined } from '@ant-design/icons'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import type { Dayjs } from 'dayjs'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import { BODY_BATCH_STATUSES, type BodyBatchStatus, type BodyBatchSummary } from '../../api/types'
import NoPlant from '../store/NoPlant'
import { qty, shortDate } from '../store/format'
import { useBodyBatches } from './api'
import BatchStatusTag from './BatchStatusTag'

const BASE = '/production/body-batches'

/** Every ball mill charge of the current plant, newest first. */
export default function BodyBatchesPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const { can, currentPlant } = useAuth()

  const [search, setSearch] = useState<string>()
  const [status, setStatus] = useState<BodyBatchStatus>()
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const { data, isFetching } = useBodyBatches(currentPlant ? {
    plantId: currentPlant.id,
    status,
    from: range?.[0]?.format('YYYY-MM-DD'),
    to: range?.[1]?.format('YYYY-MM-DD'),
    search,
    page,
    size: pageSize,
  } : null)

  // From the search palette: ?open=12 or ?new=1 -> the batch page.
  useEffect(() => {
    const open = params.get('open')
    if (open) navigate(`${BASE}/${open}`, { replace: true })
    else if (params.get('new') === '1') navigate(`${BASE}/new`, { replace: true })
  }, [params, navigate])

  if (!currentPlant) return <NoPlant />

  const newButton = can('BODY_PREP_ENTRY') && (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate(`${BASE}/new`)}>{t('prod.newBatch')}</Button>
  )

  const columns: TableColumnsType<BodyBatchSummary> = [
    {
      title: t('prod.batchNo'),
      dataIndex: 'batchNo',
      width: 200,
      render: (no?: string) => (no ? <span className="code-badge">{no}</span> : <span className="cell-sub">{t('prod.status.DRAFT')}</span>),
    },
    {
      title: t('prod.prodDate'),
      dataIndex: 'prodDate',
      width: 130,
      render: (d: string, b) => (
        <>
          <div>{shortDate(d)}</div>
          <div className="cell-sub">{t('prod.shift')} {b.shiftCode}</div>
        </>
      ),
    },
    {
      title: t('prod.ballMill'),
      key: 'mill',
      render: (_, b) => (
        <>
          <div className="cell-title">{b.ballMillName}</div>
          <div className="cell-sub">{[b.recipeCode, t('prod.tonnes', { n: qty(b.chargeTon, 3) })].filter(Boolean).join(' · ')}</div>
        </>
      ),
    },
    {
      title: t('prod.slipTest'),
      key: 'slip',
      width: 170,
      responsive: ['md'],
      render: (_, b) => (b.slipDensity == null ? <span className="cell-sub">–</span> : (
        <>
          <div>{qty(b.slipDensity, 1)} g/L · {qty(b.slipResidue, 2)} %</div>
          <div className="cell-sub">{b.slipTankCode}</div>
        </>
      )),
    },
    { title: t('common.status'), dataIndex: 'status', width: 130, render: (s: BodyBatchStatus) => <BatchStatusTag status={s} /> },
  ]

  return (
    <>
      <PageHeader title={t('prod.batchesTitle')} subtitle={t('prod.batchesSubtitle')} icon={<SyncOutlined />} extra={newButton} />

      <FadeIn delay={0.05} className="toolbar">
        <Input.Search allowClear placeholder={t('prod.searchBatches')} style={{ maxWidth: 260 }}
          onSearch={(v) => {
            setSearch(v.trim() || undefined)
            setPage(0)
          }} />
        <DatePicker.RangePicker format="DD-MM-YYYY" value={range} allowEmpty={[true, true]}
          onChange={(v) => {
            setRange(v)
            setPage(0)
          }} />
        <Select allowClear placeholder={t('common.status')} aria-label={t('common.status')} value={status} style={{ minWidth: 160 }}
          onChange={(v) => {
            setStatus(v)
            setPage(0)
          }}
          options={BODY_BATCH_STATUSES.map((s) => ({ value: s, label: t(`prod.status.${s}`) }))} />
      </FadeIn>

      <FadeIn delay={0.1}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<BodyBatchSummary>
            rowKey="id"
            columns={columns}
            dataSource={data?.content}
            loading={isFetching}
            rowClassName={(b) => `clickable-row${b.status === 'CANCELLED' ? ' row-cancelled' : ''}`}
            onRow={(b) => ({ onClick: () => navigate(`${BASE}/${b.id}`) })}
            locale={{ emptyText: isFetching ? ' ' : <EmptyState title={t('prod.emptyBatches')} action={newButton} /> }}
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
