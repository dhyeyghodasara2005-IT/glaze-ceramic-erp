import { useState } from 'react'
import { Alert, App, Button, Card, Popconfirm, Space, Spin, Table, Tag, type TableColumnsType } from 'antd'
import { ArrowLeftOutlined, NodeIndexOutlined, PlusOutlined, StopOutlined, TagsOutlined } from '@ant-design/icons'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import { errorMessage } from '../../api/errors'
import type { LineEntrySummary, PressEntrySummary } from '../../api/types'
import DocStatusTag from '../store/DocStatusTag'
import { dateTime, qty, shortDate } from '../store/format'
import { LINE_PATHS } from './lineApi'
import LotFunnel from './LotFunnel'
import LotStatusTag from './LotStatusTag'
import ReasonModal from './ReasonModal'
import { useCancelLot, useCloseLot, useLot } from './pressApi'

const BASE = '/production/lots'

/** One design lot: what it produced so far, its shift entries, and the trace back to the body batches. */
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
  const goBack = () => (location.key !== 'default' ? navigate(-1) : navigate(BASE))

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
      </FadeIn>

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

      {(['GLAZE', 'KILN'] as const).map((stage) => {
        const rows = stage === 'GLAZE' ? lot.glazeEntries : lot.kilnEntries
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
      })}

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
    </>
  )
}
