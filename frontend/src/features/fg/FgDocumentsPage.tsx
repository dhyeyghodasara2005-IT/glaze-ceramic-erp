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
import type { DocStatus, FgDocType, FgDocumentSummary } from '../../api/types'
import NoPlant from '../store/NoPlant'
import DocStatusTag from '../store/DocStatusTag'
import { DOC_STATUSES } from '../store/docTypes'
import { shortDate } from '../store/format'
import { useFgDocuments } from './fgApi'
import { FG_DOC_TYPES } from './fgDocTypes'

type StatusFilter = DocStatus | 'ALL'

/** Opening stock, breakage or regrading documents of the current plant. App.tsx mounts it once per type. */
export default function FgDocumentsPage({ type }: { type: FgDocType }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const { can, currentPlant } = useAuth()
  const base = FG_DOC_TYPES[type].path
  const [search, setSearch] = useState<string>()
  const [status, setStatus] = useState<StatusFilter>('ALL')
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const { data, isFetching } = useFgDocuments(currentPlant ? {
    plantId: currentPlant.id,
    type,
    status: status === 'ALL' ? undefined : status,
    from: range?.[0]?.format('YYYY-MM-DD'),
    to: range?.[1]?.format('YYYY-MM-DD'),
    search,
    page,
    size: pageSize,
  } : null)

  // ?open=12 (search palette) and ?new=1 go straight to the editor
  useEffect(() => {
    const open = params.get('open')
    if (open) navigate(`${base}/${open}`, { replace: true })
    else if (params.get('new') === '1') navigate(`${base}/new`, { replace: true })
  }, [params, base, navigate])

  if (!currentPlant) return <NoPlant />

  const newButton = can('FG_ENTRY') && (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate(`${base}/new`)}>{t(`fg.doc.new.${type}`)}</Button>
  )

  const columns: TableColumnsType<FgDocumentSummary> = [
    {
      title: t('store.docNo'),
      dataIndex: 'docNo',
      width: 200,
      render: (no?: string) => (no ? <span className="code-badge">{no}</span> : <span className="cell-sub">{t('store.status.DRAFT')}</span>),
    },
    { title: t('fg.doc.date'), dataIndex: 'docDate', width: 130, render: (d: string) => shortDate(d) },
    {
      title: t('store.remarks'),
      dataIndex: 'remarks',
      render: (r: string | undefined, d) => (
        <>
          <div className="cell-title">{r ?? '–'}</div>
          <div className="cell-sub">{t('fg.doc.rowsN', { count: d.lines })} · {d.createdBy}</div>
        </>
      ),
    },
    { title: t('finishing.boxes'), dataIndex: 'boxes', width: 110, align: 'right', render: (b: number) => <b>{b.toLocaleString('en-IN')}</b> },
    { title: t('common.status'), dataIndex: 'status', width: 120, render: (s: DocStatus) => <DocStatusTag status={s} /> },
  ]

  return (
    <>
      <PageHeader title={t(`fg.doc.title.${type}`)} subtitle={t(`fg.doc.subtitle.${type}`)} icon={FG_DOC_TYPES[type].icon} extra={newButton} />
      <FadeIn delay={0.05} className="toolbar">
        <Input.Search allowClear placeholder={t('fg.doc.search')} style={{ maxWidth: 280 }}
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
          <Table<FgDocumentSummary>
            rowKey="id"
            columns={columns}
            dataSource={data?.content}
            loading={isFetching}
            rowClassName={(d) => `clickable-row${d.status === 'CANCELLED' ? ' row-cancelled' : ''}`}
            onRow={(d) => ({ onClick: () => navigate(`${base}/${d.id}`) })}
            locale={{ emptyText: isFetching ? ' ' : <EmptyState title={t(`fg.doc.empty.${type}`)} action={newButton} /> }}
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
