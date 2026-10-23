import { useMemo, useState } from 'react'
import { Alert, App, Button, Card, Popconfirm, Space, Spin, Table, Tag, type TableColumnsType } from 'antd'
import { ArrowLeftOutlined, NodeIndexOutlined, PlusOutlined, StopOutlined, TagsOutlined } from '@ant-design/icons'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import { errorMessage } from '../../api/errors'
import type { LineEntrySummary, LineStage, LotCheck, PackEntrySummary, PackedBatch, PressEntrySummary } from '../../api/types'
import DocStatusTag from '../store/DocStatusTag'
import { dateTime, qty, shortDate } from '../store/format'
import { PACK_PATH } from './finishApi'
import { LINE_PATHS } from './lineApi'
import GradeSplit from './GradeSplit'
import LotFunnel from './LotFunnel'
import QualityCheckDrawer from './QualityCheckDrawer'
import VerdictTag from './VerdictTag'
import LotStatusTag from './LotStatusTag'
import ReasonModal from './ReasonModal'
import { useCancelLot, useCloseLot, useLot } from './pressApi'

const BASE = '/production/lots'

/** One design lot: what it produced so far, its entries at every stage, its boxes, and the trace back to the body batches. */
export default function LotPage() {
  const { id } = useParams()
  const { t } = useTranslation()
  const { message } = App.useApp()
  const navigate = useNavigate()
  const location = useLocation()
  const { can } = useAuth()
  const { data: lot, isLoading } = useLot(Number(id))
  const close = useCloseLot()
  const cancel = useCancelLot()
  const [cancelling, setCancelling] = useState(false)
  const [openCheck, setOpenCheck] = useState<LotCheck>()
  const goBack = () => (location.key !== 'default' ? navigate(-1) : navigate(BASE))

  // boxes per grade (all shades and calibers together), best grade first
  const byGrade = useMemo(() => {
    const map = new Map<number, { key: string; name: string; sortOrder: number; boxes: number; pieces: number }>()
    for (const b of lot?.packed ?? []) {
      const g = map.get(b.gradeId) ?? { key: String(b.gradeId), name: b.gradeName, sortOrder: b.gradeSortOrder, boxes: 0, pieces: 0 }
      g.boxes += b.boxes
      g.pieces += b.pieces
      map.set(b.gradeId, g)
    }
    return [...map.values()]
  }, [lot?.packed])

  if (isLoading) return <div className="center-block"><Spin size="large" /></div>
  if (!lot) return <div className="panel"><EmptyState title={t('store.notFound')} action={<Link to={BASE}>{t('common.back')}</Link>} /></div>

  const canEntry = can('PRESS_ENTRY')
  const tot = lot.totals

  const onClose = async () => {
    try {
      await close.mutateAsync(lot.id)
      message.success(t('press.closedOk', { no: lot.lotNo }))
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }
  const onCancel = async (reason: string) => {
    try {
      await cancel.mutateAsync({ id: lot.id, reason })
      message.success(t('store.cancelOk', { no: lot.lotNo }))
      setCancelling(false)
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const facts: { label: string; value: string; warn?: boolean }[] = [
    { label: t('press.pressed'), value: tot.pressed.toLocaleString('en-IN') },
    { label: t('press.pressRejects'), value: tot.pressRejects.toLocaleString('en-IN') },
    { label: t('press.dryerRejects'), value: tot.dryerRejects.toLocaleString('en-IN') },
    { label: t('press.good'), value: tot.good.toLocaleString('en-IN') },
    { label: t('press.goodSqm'), value: tot.goodSqm != null ? `${qty(tot.goodSqm, 0)} m²` : '–' },
    { label: t('press.loss'), value: tot.lossPct != null ? `${qty(tot.lossPct, 2)} %` : '–' },
    { label: t('press.powderUsed'), value: `${qty(tot.powderTon, 1)} T` },
    ...(tot.untracedTon > 0 ? [{ label: t('press.untraced'), value: `${qty(tot.untracedTon, 1)} T`, warn: true }] : []),
  ]

  const columns: TableColumnsType<PressEntrySummary> = [
    {
      title: t('press.entryNo'),
      dataIndex: 'entryNo',
      render: (no?: string) => (no ? <span className="code-badge">{no}</span> : <span className="cell-sub">{t('store.status.DRAFT')}</span>),
    },
    { title: t('prod.prodDate'), dataIndex: 'prodDate', width: 140, render: (d: string, e) => `${shortDate(d)} · ${e.shiftCode}` },
    { title: t('press.silo'), dataIndex: 'siloCode', width: 100, responsive: ['md'] },
    { title: t('press.pressed'), dataIndex: 'pressedCount', width: 100, align: 'right', responsive: ['sm'], render: (n: number) => n.toLocaleString('en-IN') },
    { title: t('press.good'), dataIndex: 'goodCount', width: 100, align: 'right', render: (n: number) => <b>{n.toLocaleString('en-IN')}</b> },
    { title: t('common.status'), dataIndex: 'status', width: 120, render: (s) => <DocStatusTag status={s} /> },
  ]

  const lineColumns: TableColumnsType<LineEntrySummary> = [
    {
      title: t('press.entryNo'),
      dataIndex: 'entryNo',
      render: (no?: string) => (no ? <span className="code-badge">{no}</span> : <span className="cell-sub">{t('store.status.DRAFT')}</span>),
    },
    { title: t('prod.prodDate'), dataIndex: 'prodDate', width: 140, render: (d: string, e) => `${shortDate(d)} · ${e.shiftCode}` },
    { title: t('line.tilesIn'), dataIndex: 'tilesIn', width: 100, align: 'right', responsive: ['sm'], render: (v: number) => v.toLocaleString('en-IN') },
    { title: t('line.tilesOut'), dataIndex: 'tilesOut', width: 100, align: 'right', render: (v: number) => <b>{v.toLocaleString('en-IN')}</b> },
    { title: t('common.status'), dataIndex: 'status', width: 120, render: (s) => <DocStatusTag status={s} /> },
  ]

  const mm = (v?: number) => (v != null ? `${v > 0 ? '+' : ''}${qty(v, 2)}` : '–')
  const checkColumns: TableColumnsType<LotCheck> = [
    { title: t('finishing.checkNo'), dataIndex: 'checkNo', render: (no: string) => <span className="code-badge">{no}</span> },
    { title: t('prod.prodDate'), dataIndex: 'checkDate', width: 140, render: (d: string, c) => `${shortDate(d)} · ${c.shiftCode}` },
    {
      title: t('finishing.bend'), key: 'bend', width: 170, responsive: ['md'],
      render: (_, c) => <span className="cell-sub">{mm(c.centreBendMm)} / {mm(c.edgeBendMm)} / {mm(c.warpageMm)} mm</span>,
    },
    { title: t('finishing.verdictLabel'), dataIndex: 'verdict', width: 120, render: (v) => <VerdictTag verdict={v} /> },
    { title: t('common.status'), dataIndex: 'status', width: 120, responsive: ['sm'], render: (s) => <DocStatusTag status={s} /> },
  ]

  const packColumns: TableColumnsType<PackEntrySummary> = [
    {
      title: t('press.entryNo'),
      dataIndex: 'entryNo',
      render: (no?: string) => (no ? <span className="code-badge">{no}</span> : <span className="cell-sub">{t('store.status.DRAFT')}</span>),
    },
    { title: t('prod.prodDate'), dataIndex: 'prodDate', width: 140, render: (d: string, e) => `${shortDate(d)} · ${e.shiftCode}` },
    { title: t('finishing.sorted'), dataIndex: 'tilesSorted', width: 110, align: 'right', responsive: ['sm'], render: (v: number) => v.toLocaleString('en-IN') },
    { title: t('finishing.boxes'), dataIndex: 'boxes', width: 100, align: 'right', render: (v: number) => <b>{v.toLocaleString('en-IN')}</b> },
    { title: t('common.status'), dataIndex: 'status', width: 120, render: (s) => <DocStatusTag status={s} /> },
  ]

  const batchColumns: TableColumnsType<PackedBatch> = [
    { title: t('finishing.grade'), dataIndex: 'gradeName', render: (g: string) => <b>{g}</b> },
    { title: t('finishing.shade'), dataIndex: 'shade', width: 100, render: (s: string) => <span className="code-badge">{s}</span> },
    { title: t('finishing.caliber'), dataIndex: 'caliber', width: 100, render: (c: string) => <span className="code-badge">{c}</span> },
    { title: t('finishing.boxes'), dataIndex: 'boxes', width: 100, align: 'right', render: (v: number) => <b>{v.toLocaleString('en-IN')}</b> },
    {
      title: t('finishing.area'), dataIndex: 'pieces', width: 110, align: 'right', responsive: ['sm'],
      render: (p: number) => (lot.sqmPerTile ? `${qty(p * lot.sqmPerTile, 0)} m²` : '–'),
    },
  ]
  // one table per line stage (only when it has entries)
  const stageTable = (stage: LineStage) => {
    const rows = { GLAZE: lot.glazeEntries, KILN: lot.kilnEntries, POLISH: lot.polishEntries }[stage]
    return rows.length > 0 && (
      <FadeIn key={stage} delay={0.12}>
        <h2 className="section-title">{t(`line.listTitle.${stage}`)}</h2>
        <Card className="data-card" styles={{ body: { padding: 0 } }} style={{ marginBottom: 24 }}>
          <Table<LineEntrySummary>
            rowKey="id"
            size="small"
            columns={lineColumns}
            dataSource={rows}
            pagination={false}
            rowClassName={(e) => `clickable-row${e.status === 'CANCELLED' ? ' row-cancelled' : ''}`}
            onRow={(e) => ({ onClick: () => navigate(`${LINE_PATHS[stage]}/${e.id}`) })}
          />
        </Card>
      </FadeIn>
    )
  }

  const q = lot.quality
  const showQuality = q.topGradeName && (q.expectedTopPct != null || q.actualTopPct != null)

  return (
    <>
      <PageHeader
        title={lot.lotNo}
        subtitle={(
          <Space size={10} wrap>
            <LotStatusTag status={lot.status} />
            <span><b>{lot.designName}</b> · {lot.sizeCode} {lot.finishName}</span>
            <span>{lot.pressCode} · {shortDate(lot.openedDate)} {t('prod.shift')} {lot.openedShiftCode}</span>
          </Space>
        )}
        icon={<TagsOutlined />}
        extra={(
          <Space wrap>
            <Button icon={<ArrowLeftOutlined />} onClick={goBack}>{t('common.back')}</Button>
            {canEntry && lot.status !== 'CANCELLED' && (
              <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate(`/production/press-entries/new?lot=${lot.id}`)}>
                {t('press.shiftEntry')}
              </Button>
            )}
            {canEntry && lot.status === 'OPEN' && (
              <Popconfirm title={t('press.closeTitle', { no: lot.lotNo })} description={<div style={{ maxWidth: 260 }}>{t('press.closeHint')}</div>}
                okText={t('press.closeLot')} cancelText={t('common.cancel')} onConfirm={onClose}>
                <Button loading={close.isPending}>{t('press.closeLot')}</Button>
              </Popconfirm>
            )}
            {canEntry && lot.status !== 'CANCELLED' && tot.entries === 0 && (
              <Button danger icon={<StopOutlined />} onClick={() => setCancelling(true)}>{t('press.cancelLot')}</Button>
            )}
          </Space>
        )}
      />

      <FadeIn delay={0.04}>
        {lot.status === 'CANCELLED' && (
          <Alert className="doc-alert" type="error" showIcon
            title={t('store.cancelledInfo', { by: lot.cancelledBy, at: dateTime(lot.cancelledAt), reason: lot.cancelReason })} />
        )}
        {lot.status === 'CLOSED' && (
          <Alert className="doc-alert" type="info" showIcon title={t('press.closedInfo', { by: lot.closedBy, at: dateTime(lot.closedAt) })} />
        )}
        {tot.untracedTon > 0 && <Alert className="doc-alert" type="warning" showIcon title={t('press.untracedHint')} />}
      </FadeIn>

      <FadeIn delay={0.05} className="panel doc-panel">
        <div className="form-section">{t('line.funnel')}</div>
        <LotFunnel flow={lot.flow} />
        {showQuality && (
          <div className="check-strip" style={{ marginTop: 12 }}>
            <span>
              {t('finishing.expectedVsActual', {
                grade: q.topGradeName,
                expected: q.expectedTopPct != null ? `${qty(q.expectedTopPct, 1)} %` : '–',
                actual: q.actualTopPct != null ? `${qty(q.actualTopPct, 1)} %` : '–',
              })}
            </span>
          </div>
        )}
      </FadeIn>

      {lot.packed.length > 0 && (
        <FadeIn delay={0.055} className="panel doc-panel">
          <div className="form-section">{t('finishing.packedBatches')}</div>
          <GradeSplit grades={byGrade} />
          <Table<PackedBatch>
            rowKey={(b) => `${b.gradeId}|${b.shade}|${b.caliber}`}
            size="small"
            columns={batchColumns}
            dataSource={lot.packed}
            pagination={false}
            style={{ marginTop: 12 }}
          />
        </FadeIn>
      )}

      <FadeIn delay={0.06} className="panel doc-panel">
        <div className="form-section">{t('press.totals')}</div>
        <div className="fact-grid">
          {facts.map((f) => (
            <div key={f.label}>
              <span className="stat-label">{f.label}</span>
              <b className={f.warn ? 'warn-text' : undefined}>{f.value}</b>
            </div>
          ))}
        </div>
      </FadeIn>

      <FadeIn delay={0.1}>
        <h2 className="section-title">{t('press.entries')}</h2>
        <Card className="data-card" styles={{ body: { padding: 0 } }} style={{ marginBottom: 24 }}>
          <Table<PressEntrySummary>
            rowKey="id"
            size="small"
            columns={columns}
            dataSource={lot.entries}
            pagination={false}
            rowClassName={(e) => `clickable-row${e.status === 'CANCELLED' ? ' row-cancelled' : ''}`}
            onRow={(e) => ({ onClick: () => navigate(`/production/press-entries/${e.id}`) })}
            locale={{ emptyText: <EmptyState title={t('press.emptyEntries')} /> }}
          />
        </Card>
      </FadeIn>

      {stageTable('GLAZE')}
      {stageTable('KILN')}

      {lot.checks.length > 0 && (
        <FadeIn delay={0.12}>
          <h2 className="section-title">{t('finishing.checksTitle')}</h2>
          <Card className="data-card" styles={{ body: { padding: 0 } }} style={{ marginBottom: 24 }}>
            <Table<LotCheck>
              rowKey="id"
              size="small"
              columns={checkColumns}
              dataSource={lot.checks}
              pagination={false}
              rowClassName={(c) => `clickable-row${c.status === 'CANCELLED' ? ' row-cancelled' : ''}`}
              onRow={(c) => ({ onClick: () => setOpenCheck(c) })}
            />
          </Card>
        </FadeIn>
      )}

      {stageTable('POLISH')}


      {lot.packEntries.length > 0 && (
        <FadeIn delay={0.13}>
          <h2 className="section-title">{t('finishing.packTitle')}</h2>
          <Card className="data-card" styles={{ body: { padding: 0 } }} style={{ marginBottom: 24 }}>
            <Table<PackEntrySummary>
              rowKey="id"
              size="small"
              columns={packColumns}
              dataSource={lot.packEntries}
              pagination={false}
              rowClassName={(e) => `clickable-row${e.status === 'CANCELLED' ? ' row-cancelled' : ''}`}
              onRow={(e) => ({ onClick: () => navigate(`${PACK_PATH}/${e.id}`) })}
            />
          </Card>
        </FadeIn>
      )}

      <FadeIn delay={0.14} className="panel doc-panel">
        <div className="form-section"><NodeIndexOutlined /> {t('press.trace')}</div>
        {lot.trace.length === 0 && <div className="cell-sub">{t('press.traceEmpty')}</div>}
        <div className="trace-list">
          {lot.trace.map((r) => (
            <div key={r.runId} className="trace-row">
              <span className="trace-tonnes">{qty(r.tonnes, 1)} T</span>
              <span>
                <Link to={`/production/spray-runs/${r.runId}`} className="cell-title">{r.runNo}</Link>
                <span className="cell-sub"> · {r.siloCode} · {shortDate(r.prodDate)}</span>
                <div className="trace-batches">
                  {r.batches.map((b) => (
                    <Link key={b.id} to={`/production/body-batches/${b.id}`}><Tag>{b.batchNo}</Tag></Link>
                  ))}
                </div>
              </span>
            </div>
          ))}
        </div>
      </FadeIn>

      <ReasonModal
        open={cancelling}
        title={t('store.cancelTitle', { no: lot.lotNo })}
        hint={t('press.cancelLotHint')}
        okText={t('press.cancelLot')}
        loading={cancel.isPending}
        onOk={onCancel}
        onClose={() => setCancelling(false)}
      />
      <QualityCheckDrawer check={openCheck} topGradeName={q.topGradeName} onClose={() => setOpenCheck(undefined)} />
    </>
  )
}
