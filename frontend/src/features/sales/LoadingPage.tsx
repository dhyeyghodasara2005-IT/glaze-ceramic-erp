import { useEffect, useMemo, useState } from 'react'
import { Alert, App, Button, Form, Input, InputNumber, Modal, Radio, Space, Spin } from 'antd'
import {
  ArrowLeftOutlined, CarOutlined, CheckCircleFilled, CheckOutlined, EditOutlined, PlusOutlined, PrinterOutlined, UndoOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import type { SalesOrder, SalesOrderLine, SalesPick, TruckRequest } from '../../api/types'
import { qty } from '../store/format'
import { useAddPick, useDispatchOrder, usePickOptions, useSalesOrder, useSetTruck, useTickPick } from './salesApi'
import { SalesStatusTag } from './SalesStatusTag'
import { LoadProgress, palletText, ReasonTag } from './loadingParts'

/**
 * /sales/orders/12/loading - the loader's checklist, made for a phone at the truck: the plan's rows in the
 * forklift's order, "Loaded ✓" or "Change…" on each, boxes from another pillar, the truck, and "Confirm loading".
 */
export default function LoadingPage() {
  const { id } = useParams()
  const orderId = Number(id)
  const { t } = useTranslation()
  const { message } = App.useApp()
  const navigate = useNavigate()
  const { can } = useAuth()
  const orderQuery = useSalesOrder(orderId)
  const order = orderQuery.data
  const tick = useTickPick()
  const dispatch = useDispatchOrder()
  const [changing, setChanging] = useState<SalesPick>()
  const [adding, setAdding] = useState<SalesOrderLine>()
  const [confirming, setConfirming] = useState(false)

  if (orderQuery.isLoading) return <div className="center-block"><Spin size="large" /></div>
  if (!order) return <div className="panel"><EmptyState title={t('store.notFound')} action={<Link to="/sales/loading">{t('common.back')}</Link>} /></div>

  const editable = order.status === 'CONFIRMED' && can('LOADING_ENTRY')
  const back = `/sales/orders/${order.id}`

  const onTick = async (pick: SalesPick, loadedBoxes?: number) => {
    try {
      await tick.mutateAsync({ id: order.id, pickId: pick.id, loadedBoxes })
      return true
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
      return false
    }
  }

  const onDispatch = async () => {
    try {
      const done = await dispatch.mutateAsync(order.id)
      message.success(t('loading.dispatchedOk', { no: done.dispatchNo }))
      setConfirming(false)
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  // each order line with its plan rows, so "take from another pillar" sits under the line it fills
  const lines = order.lines

  return (
    <>
      <PageHeader
        title={t('loading.title', { no: order.slipNo })}
        subtitle={(
          <Space size={10} wrap>
            <SalesStatusTag status={order.status} />
            <span>{order.party.name} · {t('finishing.boxesN', { n: order.totals.boxes.toLocaleString('en-IN') })}</span>
          </Space>
        )}
        icon={<CarOutlined />}
        extra={<Button icon={<ArrowLeftOutlined />} onClick={() => navigate(back)}>{t('loading.toOrder')}</Button>}
      />

      <FadeIn delay={0.04} className="panel loading-head">
        <LoadProgress order={order} />
        {order.status === 'DISPATCHED' && (
          <Alert type="success" showIcon title={t('loading.dispatchedShort', { no: order.dispatchNo })} action={(
            <Button size="small" icon={<PrinterOutlined />} onClick={() => window.open(`/print/sales-orders/${order.id}`, '_blank')}>
              {t('loading.print')}
            </Button>
          )} />
        )}
        {order.picks.some((p) => p.tooFew) && order.status === 'CONFIRMED' && (
          <Alert type="warning" showIcon title={t('loading.tooFewAlert')} />
        )}
      </FadeIn>

      <FadeIn delay={0.06}>
        <TruckForm order={order} editable={editable} />
      </FadeIn>

      {lines.map((line, li) => {
        const rows = order.picks.filter((p) => p.lineId === line.id)
        const kept = rows.reduce((s, p) => s + (p.loadedBoxes ?? p.plannedBoxes), 0)
        return (
          <FadeIn key={line.id} delay={0.08 + li * 0.03} className="panel loading-line">
            <div className="loading-line-head">
              <div>
                <div className="cell-title">{t('loading.rowN', { n: line.lineNo })} · {line.design?.name} · {line.grade?.name}</div>
                <div className="cell-sub">{line.description}</div>
              </div>
              <div className={`loading-line-count${kept < line.boxes ? ' short' : ''}`}>
                {t('loading.ofOrdered', { n: kept.toLocaleString('en-IN'), total: line.boxes.toLocaleString('en-IN') })}
              </div>
            </div>
            {rows.length === 0 && <p className="cell-sub">{t('loading.nothingPlanned')}</p>}
            <div className="pick-cards">
              {rows.map((p) => (
                <PickCard key={p.id} pick={p} editable={editable} busy={tick.isPending && tick.variables?.pickId === p.id}
                  onLoaded={() => onTick(p, p.plannedBoxes)} onChange={() => setChanging(p)} onUndo={() => onTick(p, undefined)} />
              ))}
            </div>
            {editable && kept < line.boxes && (
              <Button type="dashed" block icon={<PlusOutlined />} onClick={() => setAdding(line)}>{t('loading.another')}</Button>
            )}
          </FadeIn>
        )
      })}

      {editable && (
        <div className="loading-confirm-bar">
          <Button type="primary" size="large" block icon={<CheckOutlined />} disabled={order.totals.picksToTick > 0 || !order.truckNo}
            onClick={() => setConfirming(true)}>
            {t('loading.confirm')}
          </Button>
          {(order.totals.picksToTick > 0 || !order.truckNo) && (
            <div className="cell-sub">{!order.truckNo ? t('loading.needTruck') : t('loading.toTickN', { count: order.totals.picksToTick })}</div>
          )}
        </div>
      )}

      <ChangeModal pick={changing} onClose={() => setChanging(undefined)}
        onSave={async (n) => {
          if (changing && await onTick(changing, n)) setChanging(undefined)
        }} busy={tick.isPending} />
      <AddModal order={order} line={adding} onClose={() => setAdding(undefined)} />
      <Modal open={confirming} title={t('loading.confirmTitle', { no: order.slipNo })} okText={t('loading.confirm')}
        cancelText={t('common.cancel')} confirmLoading={dispatch.isPending} onOk={onDispatch} onCancel={() => setConfirming(false)}>
        <ConfirmSummary order={order} />
      </Modal>
    </>
  )
}

/** One plan row as a card: where, what, how many - and the loader's buttons. */
function PickCard({ pick: p, editable, busy, onLoaded, onChange, onUndo }: {
  pick: SalesPick
  editable: boolean
  busy: boolean
  onLoaded: () => void
  onChange: () => void
  onUndo: () => void
}) {
  const { t } = useTranslation()
  const done = p.loadedBoxes != null
  return (
    <div className={`pick-card${done ? ' done' : ''}${p.tooFew ? ' too-few' : ''}`}>
      <div className="pick-card-top">
        <span className="pick-card-pillar">{p.pillarCode}</span>
        <div className="pick-card-where">
          {p.walkOrder != null && <span>{t('loading.walk', { n: p.walkOrder })}</span>}
          <span>{p.godownCode}</span>
        </div>
        <ReasonTag reason={p.reason} />
      </div>
      <div className="pick-card-what">
        <div className="cell-title">{p.designName} · {p.gradeName}</div>
        <div className="cell-sub">
          {t('loading.shadeCaliber', { shade: p.shade, caliber: p.caliber })} · {t('loading.batch', { batch: p.batchCode })}
        </div>
      </div>
      <div className="pick-card-boxes">
        {p.reason === 'ADDED'
          ? <span className="cell-sub">{t('loading.addedByLoader')}</span>
          : <b>{palletText(t, p.plannedBoxes, p.boxesPerPallet)}</b>}
        {p.tooFew && <div className="line-hint short"><WarningOutlined /> {t('loading.tooFew', { n: p.boxesThere })}</div>}
      </div>
      {done ? (
        <div className="pick-card-done">
          <CheckCircleFilled />
          <span>{t('loading.loadedN', { n: p.loadedBoxes })}</span>
          {p.loadedBoxes !== p.plannedBoxes && p.reason !== 'ADDED' && <span className="changed">{t('loading.changed')}</span>}
          {editable && <Button type="link" size="small" icon={<UndoOutlined />} loading={busy} onClick={onUndo}>{t('loading.untick')}</Button>}
        </div>
      ) : editable && (
        <div className="pick-card-actions">
          <Button type="primary" icon={<CheckOutlined />} loading={busy} onClick={onLoaded}>{t('loading.loaded')}</Button>
          <Button icon={<EditOutlined />} onClick={onChange}>{t('loading.change')}</Button>
        </div>
      )}
    </div>
  )
}

/** Truck no., driver and LR no., saved as they are typed in (each field on its own). */
function TruckForm({ order, editable }: { order: SalesOrder; editable: boolean }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const [form] = Form.useForm<TruckRequest>()
  const save = useSetTruck()
  useEffect(() => {
    form.setFieldsValue({ truckNo: order.truckNo, driverName: order.driverName, driverMobile: order.driverMobile, lrNo: order.lrNo })
  }, [order.truckNo, order.driverName, order.driverMobile, order.lrNo, form])

  const onSave = async () => {
    let v: TruckRequest
    try {
      v = await form.validateFields()
    } catch {
      return
    }
    try {
      await save.mutateAsync({
        id: order.id,
        body: {
          truckNo: v.truckNo?.trim() || undefined,
          driverName: v.driverName?.trim() || undefined,
          driverMobile: v.driverMobile?.trim() || undefined,
          lrNo: v.lrNo?.trim() || undefined,
        },
      })
      message.success(t('common.saved'))
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  return (
    <Form<TruckRequest> form={form} layout="vertical" disabled={!editable} className={`panel truck-form${editable ? '' : ' doc-readonly'}`}>
      <div className="form-section">{t('loading.truckSection')}</div>
      <div className="truck-fields">
        <Form.Item name="truckNo" label={t('loading.truckNo')} rules={[{ pattern: /^[A-Za-z0-9 -]*$/, message: t('loading.truckInvalid') }]}
          normalize={(v?: string) => v?.toUpperCase()}>
          <Input maxLength={20} placeholder="GJ 36 T 4521" autoCapitalize="characters" />
        </Form.Item>
        <Form.Item name="driverName" label={t('loading.driverName')}>
          <Input maxLength={100} />
        </Form.Item>
        <Form.Item name="driverMobile" label={t('loading.driverMobile')} rules={[{ pattern: /^[0-9+\- ]{7,20}$/, message: t('loading.mobileInvalid') }]}>
          <Input maxLength={15} inputMode="tel" />
        </Form.Item>
        <Form.Item name="lrNo" label={t('loading.lrNo')}>
          <Input maxLength={30} />
        </Form.Item>
      </div>
      {editable && <Button icon={<CheckOutlined />} loading={save.isPending} onClick={onSave}>{t('loading.saveTruck')}</Button>}
    </Form>
  )
}

/** "Change…": what was really taken from this row (broken boxes, a pallet short ...). */
function ChangeModal({ pick, busy, onSave, onClose }: {
  pick?: SalesPick
  busy: boolean
  onSave: (boxes: number) => void
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [boxes, setBoxes] = useState<number | null>(null)
  useEffect(() => setBoxes(pick ? (pick.loadedBoxes ?? pick.plannedBoxes) : null), [pick])
  return (
    <Modal open={pick !== undefined} title={pick && t('loading.changeTitle', { pillar: pick.pillarCode })} okText={t('loading.loaded')}
      cancelText={t('common.cancel')} confirmLoading={busy} okButtonProps={{ disabled: boxes == null }}
      onOk={() => boxes != null && onSave(boxes)} onCancel={onClose} destroyOnHidden>
      {pick && (
        <>
          <p className="panel-hint">{t('loading.changeHint', { n: pick.plannedBoxes })}</p>
          <InputNumber<number> min={0} precision={0} value={boxes} onChange={setBoxes} size="large" style={{ width: '100%' }}
            inputMode="numeric" autoFocus suffix={t('loading.boxesWord')} />
        </>
      )}
    </Modal>
  )
}

/** "Take from another pillar": the loader used boxes the plan didn't name. */
function AddModal({ order, line, onClose }: { order: SalesOrder; line?: SalesOrderLine; onClose: () => void }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const options = usePickOptions(line ? order.id : undefined, line?.id)
  const add = useAddPick()
  const [stockId, setStockId] = useState<number>()
  const [boxes, setBoxes] = useState<number | null>(null)
  const kept = useMemo(() => order.picks.filter((p) => p.lineId === line?.id)
    .reduce((s, p) => s + (p.loadedBoxes ?? p.plannedBoxes), 0), [order.picks, line])
  const missing = line ? Math.max(0, line.boxes - kept) : 0
  useEffect(() => {
    setStockId(undefined)
    setBoxes(missing || null)
  }, [line, missing])
  const chosen = options.data?.find((o) => o.stockId === stockId)

  const onOk = async () => {
    if (!line || !stockId || !boxes) return
    try {
      await add.mutateAsync({ id: order.id, lineId: line.id, stockId, boxes })
      onClose()
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  return (
    <Modal open={line !== undefined} title={t('loading.another')} okText={t('loading.addLoaded')} cancelText={t('common.cancel')}
      confirmLoading={add.isPending} okButtonProps={{ disabled: !stockId || !boxes }} onOk={onOk} onCancel={onClose} destroyOnHidden>
      {line && <p className="panel-hint">{t('loading.anotherHint', { n: missing, design: line.design?.name, grade: line.grade?.name })}</p>}
      {options.isLoading && <Spin />}
      {options.data?.length === 0 && <Alert type="warning" showIcon title={t('loading.noOtherStock')} />}
      <Radio.Group className="pick-options" value={stockId} onChange={(e) => setStockId(e.target.value)}>
        {options.data?.map((o) => (
          <Radio key={o.stockId} value={o.stockId}>
            <b>{o.pillarCode}</b> · {t('loading.shadeCaliber', { shade: o.shade, caliber: o.caliber })} · {t('loading.batch', { batch: o.batchCode })}
            <span className="cell-sub"> · {t('loading.freeN', { n: o.free })}</span>
          </Radio>
        ))}
      </Radio.Group>
      {chosen && (
        <InputNumber<number> min={1} max={Math.min(chosen.free, missing || chosen.free)} precision={0} value={boxes} onChange={setBoxes}
          size="large" style={{ width: '100%', marginTop: 12 }} inputMode="numeric" suffix={t('loading.boxesWord')} />
      )}
    </Modal>
  )
}

/** What will leave the godown, line by line, before "Confirm loading". */
function ConfirmSummary({ order }: { order: SalesOrder }) {
  const { t } = useTranslation()
  const short = order.lines.filter((l) => (l.loaded ?? 0) < l.boxes)
  return (
    <div className="confirm-checks">
      <p>{t('loading.confirmHint', { boxes: order.totals.loadedBoxes.toLocaleString('en-IN'), truck: order.truckNo })}</p>
      <ul className="confirm-lines">
        {order.lines.map((l) => (
          <li key={l.id}>
            {l.design?.name} · {l.grade?.name}: <b>{(l.loaded ?? 0).toLocaleString('en-IN')}</b> / {l.boxes.toLocaleString('en-IN')}
            {l.weightKg != null && l.boxes > 0 && <span className="cell-sub"> · {qty(((l.loaded ?? 0) * l.weightKg) / l.boxes, 0)} kg</span>}
          </li>
        ))}
      </ul>
      {short.length > 0 && <Alert type="warning" showIcon title={t('loading.shortLoaded', { count: short.length })} />}
      <p className="cell-sub">{t('loading.confirmAfter')}</p>
    </div>
  )
}
