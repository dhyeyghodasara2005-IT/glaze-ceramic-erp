import { useEffect, useState } from 'react'
import { Button, Card, DatePicker, Input, Segmented, Table, Tag, type TableColumnsType } from 'antd'
import { FileDoneOutlined, PlusOutlined } from '@ant-design/icons'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import type { Dayjs } from 'dayjs'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import { SALES_ORDER_STATUSES, type SalesOrderStatus, type SalesOrderSummary, type SalesOrderType } from '../../api/types'
import NoPlant from '../store/NoPlant'
import { shortDate } from '../store/format'
import { useSalesOrders } from './salesApi'
import { OrderTypeTag, SalesStatusTag } from './SalesStatusTag'

const BASE = '/sales/orders'
type StatusFilter = SalesOrderStatus | 'ALL'
type TypeFilter = SalesOrderType | 'ALL'

/** The loading slips of the current plant, newest first. */
export default function SalesOrdersPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const { can, currentPlant } = useAuth()
  const [search, setSearch] = useState<string>()
  const [status, setStatus] = useState<StatusFilter>('ALL')
  const [type, setType] = useState<TypeFilter>('ALL')
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const { data, isFetching } = useSalesOrders(currentPlant ? {
    plantId: currentPlant.id,
    status: status === 'ALL' ? undefined : status,
    type: type === 'ALL' ? undefined : type,
    from: range?.[0]?.format('YYYY-MM-DD'),
    to: range?.[1]?.format('YYYY-MM-DD'),
    search,
    page,
    size: pageSize,
  } : null)

  // ?open=12 (search palette) and ?new=1 go straight to the editor
  useEffect(() => {
    const open = params.get('open')
    if (open) navigate(`${BASE}/${open}`, { replace: true })
    else if (params.get('new') === '1') navigate(`${BASE}/new`, { replace: true })
  }, [params, navigate])

  if (!currentPlant) return <NoPlant />

  const newButton = can('SALES_ENTRY') && (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate(`${BASE}/new`)}>{t('sales.new')}</Button>
  )
  const resetPage = () => setPage(0)

  const columns: TableColumnsType<SalesOrderSummary> = [
    {
      title: t('sales.slipNo'),
      dataIndex: 'slipNo',
      width: 150,
      render: (no: string, o) => (
        <>
          <span className="code-badge">{no}</span>
          <div className="cell-sub">{shortDate(o.slipDate)}</div>
        </>
      ),
    },
    {
      title: t('sales.billedTo'),
      dataIndex: 'partyName',
      render: (name: string, o) => (
        <>
          <div className="cell-title">{name} {o.orderType === 'JOBWORK' && <OrderTypeTag type="JOBWORK" />}</div>
          <div className="cell-sub">{[o.partyCode, o.partyCity].filter(Boolean).join(' · ')}</div>
        </>
      ),
    },
    {
      title: t('sales.orderRef'),
      dataIndex: 'orderRef',
      width: 140,
      responsive: ['md'],
      render: (ref?: string) => ref ?? '–',
    },
    {
      title: t('finishing.boxes'),
      dataIndex: 'boxes',
      width: 170,
      align: 'right',
      render: (b: number, o) => (
        <>
          <b>{b.toLocaleString('en-IN')}</b>
          <div className="cell-sub">
            {t('sales.productsN', { count: o.lines })}
            {!o.matched && o.status === 'DRAFT' && <Tag color="orange" bordered={false} style={{ marginInlineStart: 6 }}>{t('sales.toMatch')}</Tag>}
          </div>
        </>
      ),
    },
    { title: t('common.status'), dataIndex: 'status', width: 130, render: (s: SalesOrderStatus) => <SalesStatusTag status={s} /> },
  ]

  return (
    <>
      <PageHeader title={t('sales.title')} subtitle={t('sales.subtitle')} icon={<FileDoneOutlined />} extra={newButton} />
      <FadeIn delay={0.05} className="toolbar">
        <Input.Search allowClear placeholder={t('sales.search')} style={{ maxWidth: 280 }}
          onSearch={(v) => {
            setSearch(v.trim() || undefined)
            resetPage()
          }} />
        <DatePicker.RangePicker format="DD-MM-YYYY" value={range} allowEmpty={[true, true]}
          onChange={(v) => {
            setRange(v)
            resetPage()
          }} />
        <Segmented<TypeFilter>
          value={type}
          onChange={(v) => {
            setType(v)
            resetPage()
          }}
          options={[{ value: 'ALL', label: t('common.all') }, { value: 'SALE', label: t('sales.type.SALE') },
            { value: 'JOBWORK', label: t('sales.type.JOBWORK') }]}
        />
        <Segmented<StatusFilter>
          value={status}
          onChange={(v) => {
            setStatus(v)
            resetPage()
          }}
          options={[{ value: 'ALL', label: t('common.all') }, ...SALES_ORDER_STATUSES.map((s) => ({ value: s, label: t(`sales.status.${s}`) }))]}
        />
      </FadeIn>
      <FadeIn delay={0.1}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<SalesOrderSummary>
            rowKey="id"
            columns={columns}
            dataSource={data?.content}
            loading={isFetching}
            rowClassName={(o) => `clickable-row${o.status === 'CANCELLED' ? ' row-cancelled' : ''}`}
            onRow={(o) => ({ onClick: () => navigate(`${BASE}/${o.id}`) })}
            locale={{ emptyText: isFetching ? ' ' : <EmptyState title={t('sales.empty')} action={newButton} /> }}
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
