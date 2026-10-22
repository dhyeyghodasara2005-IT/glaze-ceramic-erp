import { useEffect, useState } from 'react'
import { Button, Card, DatePicker, Input, Segmented, Table, type TableColumnsType } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import type { Dayjs } from 'dayjs'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import type { DocStatus, StoreDocType, StoreDocumentSummary } from '../../api/types'
import { useStoreDocuments } from './api'
import { DOC_STATUSES, DOC_TYPES } from './docTypes'
import DocStatusTag from './DocStatusTag'
import { money, shortDate } from './format'
import NoPlant from './NoPlant'

type StatusFilter = DocStatus | 'ALL'

/**
 * List of GRNs, issues or adjustments of the current plant. App.tsx mounts it once per type.
 * Clicking a row opens the document on its own page: /store/grn/12.
 */
export default function StoreDocumentsPage({ type }: { type: StoreDocType }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const { can, currentPlant } = useAuth()
  const base = DOC_TYPES[type].path

  const [search, setSearch] = useState<string>()
  const [status, setStatus] = useState<StatusFilter>('ALL')
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const { data, isFetching } = useStoreDocuments(currentPlant ? {
    plantId: currentPlant.id,
    type,
    status: status === 'ALL' ? undefined : status,
    from: range?.[0]?.format('YYYY-MM-DD'),
    to: range?.[1]?.format('YYYY-MM-DD'),
    search,
    page,
    size: pageSize,
  } : null)

  // The search palette opens "?open=12" (a search hit) or "?new=1" ("New GRN"): go to the document page.
  useEffect(() => {
    const open = params.get('open')
    if (open) navigate(`${base}/${open}`, { replace: true })
    else if (params.get('new') === '1') navigate(`${base}/new`, { replace: true })
  }, [params, base, navigate])

  if (!currentPlant) return <NoPlant />

  const newButton = can('STORE_ENTRY') && (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate(`${base}/new`)}>
      {t(`store.newDoc.${type}`)}
    </Button>
  )

  const columns: TableColumnsType<StoreDocumentSummary> = [
    {
      title: t('store.docNo'),
      dataIndex: 'docNo',
      width: 210,
      render: (no?: string) => (no ? <span className="code-badge">{no}</span> : <span className="cell-sub">{t('store.status.DRAFT')}</span>),
    },
    { title: t('store.date'), dataIndex: 'docDate', width: 120, render: shortDate },
    // The middle column depends on the type: who sent it / where it went / why.
    type === 'GRN'
      ? {
          title: t('store.supplier'),
          key: 'party',
          render: (_, d) => (
            <>
              <div className="cell-title">{d.supplierName}</div>
              {d.supplierInvoiceNo && <div className="cell-sub">{t('store.supplierInvoiceNo')}: {d.supplierInvoiceNo}</div>}
            </>
          ),
        }
      : type === 'ISSUE'
        ? {
            title: t('store.issuedTo'),
            key: 'party',
            render: (_, d) => (
              <>
                <div className="cell-title">{d.machineName ?? d.issuedTo ?? '–'}</div>
                {d.machineName && d.issuedTo && <div className="cell-sub">{d.issuedTo}</div>}
              </>
            ),
          }
        : {
            title: t('store.reason'),
            key: 'party',
            render: (_, d) => <span className="cell-title">{d.reason ? t(`store.reasons.${d.reason}`) : '–'}</span>,
          },
    {
      title: t('store.value'),
      dataIndex: 'totalValue',
      width: 150,
      align: 'right',
      responsive: ['sm'],
      // An issue's value is only known once it is posted (average rate at that moment).
      render: (v: number, d) => (type === 'ISSUE' && d.status === 'DRAFT' ? <span className="cell-sub">–</span> : money(v)),
    },
    { title: t('common.status'), dataIndex: 'status', width: 120, render: (s: DocStatus) => <DocStatusTag status={s} /> },
  ]

  return (
    <>
      <PageHeader title={t(`store.docTitle.${type}`)} subtitle={t(`store.docSubtitle.${type}`)} icon={DOC_TYPES[type].icon} extra={newButton} />

      <FadeIn delay={0.05} className="toolbar">
        <Input.Search
          allowClear
          placeholder={t('store.searchDocs')}
          onSearch={(v) => {
            setSearch(v.trim() || undefined)
            setPage(0)
          }}
          style={{ maxWidth: 300 }}
        />
        <DatePicker.RangePicker
          format="DD-MM-YYYY"
          value={range}
          onChange={(v) => {
            setRange(v)
            setPage(0)
          }}
          allowEmpty={[true, true]}
        />
        <Segmented<StatusFilter>
          value={status}
          onChange={(v) => {
            setStatus(v)
            setPage(0)
          }}
          options={[
            { value: 'ALL', label: t('common.all') },
            ...DOC_STATUSES.map((s) => ({ value: s, label: t(`store.status.${s}`) })),
          ]}
        />
      </FadeIn>

      <FadeIn delay={0.1}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<StoreDocumentSummary>
            rowKey="id"
            columns={columns}
            dataSource={data?.content}
            loading={isFetching}
            rowClassName={(d) => `clickable-row${d.status === 'CANCELLED' ? ' row-cancelled' : ''}`}
            onRow={(d) => ({ onClick: () => navigate(`${base}/${d.id}`) })}
            locale={{ emptyText: isFetching ? ' ' : <EmptyState title={t(`store.emptyDocs.${type}`)} action={newButton} /> }}
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
