import { useCallback, useState } from 'react'
import { Button, Card, DatePicker, Input, Segmented, Table, type TableColumnsType } from 'antd'
import { AuditOutlined, PlusOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import type { Dayjs } from 'dayjs'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useOpenFromUrl } from '../../components/useOpenFromUrl'
import { useAuth } from '../../auth/AuthContext'
import type { DocStatus, LotCheck } from '../../api/types'
import NoPlant from '../store/NoPlant'
import DocStatusTag from '../store/DocStatusTag'
import { qty, shortDate } from '../store/format'
import { fetchLotCheck, useFinishingOverview, useLotChecks } from './finishApi'
import QualityCheckDrawer from './QualityCheckDrawer'
import QualityCheckModal from './QualityCheckModal'
import VerdictTag from './VerdictTag'

type StatusFilter = 'POSTED' | 'CANCELLED' | 'ALL'

/** Every quality check of the current plant. A row opens the check in a drawer. */
export default function QualityChecksPage() {
  const { t } = useTranslation()
  const { can, currentPlant } = useAuth()
  const [search, setSearch] = useState<string>()
  const [status, setStatus] = useState<StatusFilter>('ALL')
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [creating, setCreating] = useState(false)
  const [open, setOpen] = useState<LotCheck>()
  const { data, isFetching } = useLotChecks(currentPlant ? {
    plantId: currentPlant.id,
    status: status === 'ALL' ? undefined : status,
    from: range?.[0]?.format('YYYY-MM-DD'),
    to: range?.[1]?.format('YYYY-MM-DD'),
    search,
    page,
    size: pageSize,
  } : null)
  // only for the top grade's name (Premium) in the drawer
  const finishing = useFinishingOverview(currentPlant?.id)

  const canCheck = can('QUALITY_CHECK')
  // ?open=12 from the search palette opens that check; ?new=1 opens the form
  const onOpen = useCallback((c?: LotCheck) => (c ? setOpen(c) : setCreating(true)), [])
  useOpenFromUrl<LotCheck>({ fetchOne: fetchLotCheck, onOpen })

  if (!currentPlant) return <NoPlant />

  const newButton = canCheck && (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>{t('finishing.newCheck')}</Button>
  )
  const mm = (v?: number) => (v != null ? `${v > 0 ? '+' : ''}${qty(v, 2)}` : '–')

  const columns: TableColumnsType<LotCheck> = [
    { title: t('finishing.checkNo'), dataIndex: 'checkNo', width: 200, render: (no: string) => <span className="code-badge">{no}</span> },
    {
      title: t('prod.prodDate'),
      dataIndex: 'checkDate',
      width: 130,
      render: (d: string, c) => (
        <>
          <div>{shortDate(d)}</div>
          <div className="cell-sub">{t('prod.shift')} {c.shiftCode}</div>
        </>
      ),
    },
    {
      title: t('press.lot'),
      key: 'lot',
      render: (_, c) => (
        <>
          <div className="cell-title">{c.designName}</div>
          <div className="cell-sub">{c.lotNo} · {c.pressCode}</div>
        </>
      ),
    },
    {
      title: t('finishing.bend'),
      key: 'bend',
      width: 170,
      responsive: ['md'],
      render: (_, c) => <span className="cell-sub">{mm(c.centreBendMm)} / {mm(c.edgeBendMm)} / {mm(c.warpageMm)} mm</span>,
    },
    { title: t('finishing.verdictLabel'), dataIndex: 'verdict', width: 130, render: (v) => <VerdictTag verdict={v} /> },
    { title: t('common.status'), dataIndex: 'status', width: 120, responsive: ['sm'], render: (s: DocStatus) => <DocStatusTag status={s} /> },
  ]

  return (
    <>
      <PageHeader title={t('finishing.checksTitle')} subtitle={t('finishing.checksSubtitle')} icon={<AuditOutlined />} extra={newButton} />
      <FadeIn delay={0.05} className="toolbar">
        <Input.Search allowClear placeholder={t('finishing.searchChecks')} style={{ maxWidth: 280 }}
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
          options={[
            { value: 'ALL', label: t('common.all') },
            { value: 'POSTED', label: t('store.status.POSTED') },
            { value: 'CANCELLED', label: t('store.status.CANCELLED') },
          ]}
        />
      </FadeIn>
      <FadeIn delay={0.1}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<LotCheck>
            rowKey="id"
            columns={columns}
            dataSource={data?.content}
            loading={isFetching}
            rowClassName={(c) => `clickable-row${c.status === 'CANCELLED' ? ' row-cancelled' : ''}`}
            onRow={(c) => ({ onClick: () => setOpen(c) })}
            locale={{ emptyText: isFetching ? ' ' : <EmptyState title={t('finishing.checksEmpty')} action={newButton} /> }}
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
      <QualityCheckModal open={creating} plantId={currentPlant.id} onClose={() => setCreating(false)} onSaved={setOpen} />
      <QualityCheckDrawer check={open} topGradeName={finishing.data?.topGradeName} onClose={() => setOpen(undefined)} />
    </>
  )
}
