import { useEffect, useMemo, useState } from 'react'
import { Button, Card, DatePicker, Input, Select, Table, type TableColumnsType } from 'antd'
import { SwapOutlined, TagsOutlined } from '@ant-design/icons'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import type { Dayjs } from 'dayjs'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import { LOT_STATUSES, type LotStatus, type LotSummary } from '../../api/types'
import NoPlant from '../store/NoPlant'
import { qty, shortDate } from '../store/format'
import LotStatusTag from './LotStatusTag'
import OpenLotModal from './OpenLotModal'
import { useLots } from './pressApi'

const BASE = '/production/lots'

export default function LotsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const { can, currentPlant } = useAuth()
  const [search, setSearch] = useState<string>()
  const [status, setStatus] = useState<LotStatus>()
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [starting, setStarting] = useState(false)
  const { data, isFetching } = useLots(currentPlant ? {
    plantId: currentPlant.id,
    status,
    from: range?.[0]?.format('YYYY-MM-DD'),
    to: range?.[1]?.format('YYYY-MM-DD'),
    search,
    page,
    size: pageSize,
  } : null)
  // Lots running now, so the dialog can say which one a design change closes.
  const running = useMemo(() => new Map((data?.content ?? []).filter((l) => l.status === 'OPEN').map((l) => [l.pressId, l])), [data])

  // Search palette: ?open=12 -> the lot page; ?new=1 -> "Start a design".
  useEffect(() => {
    const open = params.get('open')
    if (open) navigate(`${BASE}/${open}`, { replace: true })
    else if (params.get('new') === '1') {
      setStarting(true)
      setParams({}, { replace: true })
    }
  }, [params, navigate, setParams])

  if (!currentPlant) return <NoPlant />

  const startButton = can('PRESS_ENTRY') && (
    <Button type="primary" icon={<SwapOutlined />} onClick={() => setStarting(true)}>{t('press.startDesign')}</Button>
  )

  const columns: TableColumnsType<LotSummary> = [
    { title: t('press.lotNo'), dataIndex: 'lotNo', width: 200, render: (no: string) => <span className="code-badge">{no}</span> },
    {
      title: t('press.design'),
      dataIndex: 'designName',
      render: (name: string, l) => (
        <>
          <div className="cell-title">{name}</div>
          <div className="cell-sub">{l.designCode} · {l.sizeCode} {l.finishName}</div>
        </>
      ),
    },
    { title: t('press.press'), dataIndex: 'pressCode', width: 110, responsive: ['md'] },
    {
      title: t('press.started'),
      dataIndex: 'openedDate',
      width: 130,
      responsive: ['sm'],
      render: (d: string, l) => (
        <>
          <div>{shortDate(d)}</div>
          <div className="cell-sub">{t('prod.shift')} {l.openedShiftCode}</div>
        </>
      ),
    },
    {
      title: t('press.good'),
      dataIndex: 'good',
      width: 150,
      align: 'right',
      render: (good: number, l) => (
        <>
          <div className="cell-title">{good.toLocaleString('en-IN')}</div>
          {l.goodSqm != null && <div className="cell-sub">{qty(l.goodSqm, 0)} m²</div>}
        </>
      ),
    },
    { title: t('common.status'), dataIndex: 'status', width: 120, render: (s: LotStatus) => <LotStatusTag status={s} /> },
  ]

  return (
    <>
      <PageHeader title={t('press.lotsTitle')} subtitle={t('press.lotsSubtitle')} icon={<TagsOutlined />} extra={startButton} />
      <FadeIn delay={0.05} className="toolbar">
        <Input.Search allowClear placeholder={t('press.searchLots')} style={{ maxWidth: 260 }}
          onSearch={(v) => {
            setSearch(v.trim() || undefined)
            setPage(0)
          }} />
        <DatePicker.RangePicker format="DD-MM-YYYY" value={range} allowEmpty={[true, true]}
          onChange={(v) => {
            setRange(v)
            setPage(0)
          }} />
        <Select allowClear placeholder={t('common.status')} aria-label={t('common.status')} value={status} style={{ minWidth: 150 }}
          onChange={(v) => {
            setStatus(v)
            setPage(0)
          }}
          options={LOT_STATUSES.map((s) => ({ value: s, label: t(`press.lotStatus.${s}`) }))} />
      </FadeIn>
      <FadeIn delay={0.1}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<LotSummary>
            rowKey="id"
            columns={columns}
            dataSource={data?.content}
            loading={isFetching}
            rowClassName={(l) => `clickable-row${l.status === 'CANCELLED' ? ' row-cancelled' : ''}`}
            onRow={(l) => ({ onClick: () => navigate(`${BASE}/${l.id}`) })}
            locale={{ emptyText: isFetching ? ' ' : <EmptyState title={t('press.emptyLots')} action={startButton} /> }}
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
      <OpenLotModal open={starting} plantId={currentPlant.id} running={running} onClose={() => setStarting(false)}
        onOpened={(id) => navigate(`${BASE}/${id}`)} />
    </>
  )
}
