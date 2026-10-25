import { useCallback, useState } from 'react'
import { Button, Card, Input, Segmented, Table, Tag, type TableColumnsType } from 'antd'
import { ExperimentOutlined, FieldTimeOutlined, WarningOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn, Stagger, StaggerItem } from '../../components/motion'
import { useOpenFromUrl } from '../../components/useOpenFromUrl'
import { useAuth } from '../../auth/AuthContext'
import type { LabOverview, LabTest, LabTestSummary, LabVerdict } from '../../api/types'
import NoPlant from '../store/NoPlant'
import { shortDate } from '../store/format'
import { useNow } from '../production/useNow'
import { duration } from '../production/time'
import { fmt } from '../dashboard/charts'
import { fetchLabTest, useLabOverview, useLabTests, useLabWaiting } from './labApi'
import { VerdictTag } from './labParts'
import LabTestDrawer from './LabTestDrawer'

type Sample = { documentId: number; materialId: number }
type Supplier = LabOverview['suppliers'][number]

/**
 * /lab/tests - raw material tested when it arrives. First what is waiting (the trucks whose GRN can't be posted until
 * the lab has spoken), then what was tested, and which suppliers' material gets rejected.
 */
export default function LabPage() {
  const { t } = useTranslation()
  const { can, currentPlant } = useAuth()
  useNow(60_000)
  const [open, setOpen] = useState<Sample>()
  const [verdict, setVerdict] = useState<LabVerdict | 'ALL'>('ALL')
  const [search, setSearch] = useState<string>()
  const [page, setPage] = useState(0)
  const waiting = useLabWaiting(currentPlant?.id)
  const overview = useLabOverview(currentPlant?.id)
  const tests = useLabTests(currentPlant ? {
    plantId: currentPlant.id, verdict: verdict === 'ALL' ? undefined : verdict, search, page, size: 20,
  } : null)

  const openFromUrl = useCallback((item?: LabTest) => {
    if (item) setOpen({ documentId: item.sample.documentId, materialId: item.sample.materialId })
  }, [])
  useOpenFromUrl({ fetchOne: fetchLabTest, onOpen: openFromUrl })

  if (!currentPlant) return <NoPlant />
  const o = overview.data

  const columns: TableColumnsType<LabTestSummary> = [
    {
      title: t('lab.test'),
      key: 'no',
      width: 220,
      render: (_, r) => (
        <>
          <span className="code-badge">{r.testNo}</span>
          <div className="cell-sub">{shortDate(r.testedOn)}</div>
        </>
      ),
    },
    {
      title: t('lab.material'),
      key: 'material',
      render: (_, r) => (
        <>
          <div className="cell-title">{r.materialName}</div>
          <div className="cell-sub">{[r.supplierName, r.vehicleNo].filter(Boolean).join(' · ')}</div>
        </>
      ),
    },
    {
      title: t('lab.result'),
      key: 'verdict',
      width: 200,
      render: (_, r) => (
        <>
          <VerdictTag verdict={r.verdict} />
          {r.outOfRange > 0 && <div className="cell-sub"><WarningOutlined /> {t('lab.outsideShort', { count: r.outOfRange })}</div>}
        </>
      ),
    },
    { title: t('lab.grn'), dataIndex: 'docNo', width: 200, responsive: ['lg'], render: (no?: string) => no ?? t('lab.draft') },
  ]
  const supplierColumns: TableColumnsType<Supplier> = [
    { title: t('lab.supplier'), dataIndex: 'name', render: (n?: string) => n ?? t('lab.noSupplier') },
    { title: t('lab.testedCol'), dataIndex: 'tested', width: 110, align: 'right' },
    { title: t('lab.rejectedCol'), dataIndex: 'rejected', width: 110, align: 'right' },
    {
      title: t('lab.rejectedPctCol'),
      dataIndex: 'rejectedPct',
      width: 130,
      align: 'right',
      render: (p?: number) => (p == null ? '–' : p > 0 ? <Tag color="red" bordered={false}>{fmt(p, 1)}%</Tag> : `${fmt(p, 1)}%`),
    },
  ]

  return (
    <>
      <PageHeader title={t('lab.pageTitle')} subtitle={t('lab.pageSubtitle')} icon={<ExperimentOutlined />} />

      {o && (
        <Stagger className="dash-headline">
          <StaggerItem className={`dash-tile${o.waiting ? ' warn' : ''}`}>
            <div className="stat-label">{t('lab.waitingNow')}</div>
            <div className="dash-tile-value">{o.waiting}</div>
            <div className="dash-tile-sub">{t('lab.waitingSub')}</div>
          </StaggerItem>
          <StaggerItem className="dash-tile">
            <div className="stat-label">{t('lab.tested30')}</div>
            <div className="dash-tile-value">{o.tested}</div>
            <div className="dash-tile-sub">{t('lab.last30')}</div>
          </StaggerItem>
          <StaggerItem className="dash-tile">
            <div className="stat-label">{t('lab.rejected30')}</div>
            <div className="dash-tile-value">{o.rejected}</div>
            <div className="dash-tile-sub">{o.rejectedPct == null ? t('lab.nothingTested') : t('lab.rejectedSub', { pct: fmt(o.rejectedPct, 1) })}</div>
          </StaggerItem>
        </Stagger>
      )}

      <FadeIn delay={0.06}>
        <h2 className="section-title">{t('lab.waitingTitle')} {!!waiting.data?.length && <span className="pm-count">{waiting.data.length}</span>}</h2>
        {waiting.data && waiting.data.length === 0 ? (
          <div className="panel lab-none">{t('lab.noneWaiting')}</div>
        ) : (
          <div className="lab-waiting">
            {(waiting.data ?? []).map((w) => (
              <div key={`${w.documentId}/${w.materialId}`} className="lab-truck" role="button" tabIndex={0}
                onClick={() => setOpen(w)} onKeyDown={(e) => e.key === 'Enter' && setOpen(w)}>
                <div className="cell-title">{w.materialName}</div>
                <div className="lab-truck-qty">{fmt(w.receivedQty, 3)} {w.unit}</div>
                <div className="cell-sub">{[w.supplierName, w.vehicleNo].filter(Boolean).join(' · ')}</div>
                <div className="cell-sub"><FieldTimeOutlined /> {t('lab.arrivedAgo', { time: duration(w.arrivedAt) })}</div>
                {can('LAB_ENTRY') && <Button type="primary" block icon={<ExperimentOutlined />}>{t('lab.testIt')}</Button>}
              </div>
            ))}
          </div>
        )}
      </FadeIn>

      <FadeIn delay={0.1}>
        <h2 className="section-title">{t('lab.testedTitle')}</h2>
        <div className="toolbar">
          <Input.Search allowClear placeholder={t('lab.search')} style={{ maxWidth: 280 }}
            onSearch={(v) => { setSearch(v.trim() || undefined); setPage(0) }} />
          <Segmented<LabVerdict | 'ALL'> value={verdict} onChange={(v) => { setVerdict(v); setPage(0) }} options={[
            { value: 'ALL', label: t('lab.all') },
            { value: 'ACCEPTED', label: t('lab.verdict.ACCEPTED') },
            { value: 'REJECTED', label: t('lab.verdict.REJECTED') },
          ]} />
        </div>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<LabTestSummary>
            rowKey="id"
            columns={columns}
            dataSource={tests.data?.content ?? []}
            loading={tests.isLoading}
            onRow={(r) => ({ onClick: () => setOpen({ documentId: r.documentId, materialId: r.materialId }), style: { cursor: 'pointer' } })}
            pagination={{
              current: page + 1, pageSize: 20, total: tests.data?.totalElements ?? 0, showSizeChanger: false,
              hideOnSinglePage: true, onChange: (p) => setPage(p - 1),
            }}
            locale={{ emptyText: tests.isLoading ? ' ' : <EmptyState title={t('lab.noneTested')} /> }}
          />
        </Card>
      </FadeIn>

      {o && o.suppliers.length > 0 && (
        <FadeIn delay={0.14}>
          <h2 className="section-title">{t('lab.suppliersTitle')}</h2>
          <Card className="data-card" styles={{ body: { padding: 0 } }}>
            <Table<Supplier> rowKey={(r) => r.supplierId ?? 0} columns={supplierColumns} dataSource={o.suppliers} pagination={false} />
          </Card>
        </FadeIn>
      )}

      <LabTestDrawer sample={open} onClose={() => setOpen(undefined)} />
    </>
  )
}
