import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { Alert, App, Button, DatePicker, Form, Input, InputNumber, Modal, Popconfirm, Segmented, Select, Space, Spin, Tooltip } from 'antd'
import {
  ArrowLeftOutlined, CheckCircleOutlined, DeleteOutlined, EditOutlined, FileDoneOutlined, MinusCircleOutlined, PlusOutlined,
  QuestionCircleOutlined, SaveOutlined, StopOutlined, ThunderboltOutlined, WarningOutlined,
} from '@ant-design/icons'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import dayjs, { type Dayjs } from 'dayjs'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import {
  BILLED_TO, type MatchHow, type SalesOption, type SalesOrder, type SalesOrderLine, type SalesOrderType, type SalesParty,
} from '../../api/types'
import NoPlant from '../store/NoPlant'
import { dateTime, qty, shortDate } from '../store/format'
import ReasonModal from '../production/ReasonModal'
import {
  matchProducts, useCancelSalesOrder, useConfirmSalesOrder, useDeleteSalesOrder, useReopenSalesOrder, useSalesDesigns,
  useSalesGrades, useSalesOrder, useSalesParties, useSaveSalesOrder,
} from './salesApi'
import { OrderTypeTag, SalesStatusTag } from './SalesStatusTag'

const BASE = '/sales/orders'

/** How a row got its design: Glaze's match, a person's pick, or saved on the draft but not remembered yet. */
type RowHow = MatchHow | 'CHOSEN' | 'SAVED'

/**
 * One product line. Besides what is sent to the server, each row carries its own match state (how, candidates,
 * matchedFor, *ByHand) in hidden fields, so the state moves with the row when rows are added or removed.
 */
interface LineValues {
  description?: string
  designId?: number
  gradeId?: number
  boxes?: number
  mrpBox?: number
  mrpPiece?: number
  batchRef?: string
  how?: RowHow
  candidates?: SalesOption[]
  /** the tidied description this row was last matched for */
  matchedFor?: string
  designByHand?: boolean
  gradeByHand?: boolean
}
interface FormValues {
  orderType: SalesOrderType
  slipNo: string
  slipDate: Dayjs
  orderRef?: string
  orderDate?: Dayjs
  partyId?: number
  transporterId?: number
  slipBoxes?: number
  slipWeightKg?: number
  remarks?: string
  lines: LineValues[]
}

/** Glaze's weight may differ this much from the slip's (the same 2% as SalesOrderService). */
const WEIGHT_TOLERANCE = 0.02
/** Same tidying as ProductMatcher.key(), to know whether a description changed since it was matched. */
const tidy = (v?: string) => (v ?? '').trim().toUpperCase().replace(/\s+/g, ' ').replace(/ ?- ?/g, '-')

/** Keeps a hidden value in the form (the match state of a row); renders nothing. */
const Stash = (_: { value?: unknown }) => null

function Cell({ label, className, children }: { label: string; className?: string; children: ReactNode }) {
  return (
    <div className={className}>
      <span className="line-label">{label}</span>
      {children}
    </div>
  )
}

/** /sales/orders/new and /sales/orders/12 */
export default function SalesOrderPage() {
  const { id } = useParams()
  return <OrderEditor key={id} orderId={id && id !== 'new' ? Number(id) : undefined} />
}

function OrderEditor({ orderId }: { orderId?: number }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const navigate = useNavigate()
  const location = useLocation()
  const { can, currentPlant } = useAuth()
  const isNew = orderId === undefined
  const goBack = () => (location.key !== 'default' ? navigate(-1) : navigate(BASE))

  const orderQuery = useSalesOrder(orderId)
  const order = orderQuery.data
  const plantId = order?.plantId ?? currentPlant?.id
  const editable = can('SALES_ENTRY') && (isNew || order?.status === 'DRAFT')

  const [form] = Form.useForm<FormValues>()
  const orderType = (Form.useWatch('orderType', form) as SalesOrderType | undefined) ?? 'SALE'
  const partyId = Form.useWatch('partyId', form) as number | undefined
  const rows = Form.useWatch('lines', form) as LineValues[] | undefined
  const slipBoxes = Form.useWatch('slipBoxes', form) as number | undefined
  const slipWeight = Form.useWatch('slipWeightKg', form) as number | undefined

  const parties = useSalesParties(editable ? BILLED_TO[orderType] : undefined)
  const transporters = useSalesParties(editable ? 'TRANSPORTER' : undefined)
  const designs = useSalesDesigns(editable)
  const grades = useSalesGrades(editable)

  const save = useSaveSalesOrder()
  const confirm = useConfirmSalesOrder()
  const reopen = useReopenSalesOrder()
  const cancel = useCancelSalesOrder()
  const remove = useDeleteSalesOrder()
  const [matching, setMatching] = useState(false)
  /** The freshly saved order whose checks the confirm dialog shows. */
  const [confirming, setConfirming] = useState<SalesOrder>()
  const [cancelling, setCancelling] = useState(false)
  /** Changed since the last save: the stock shown under each line is from that save. */
  const [dirty, setDirty] = useState(false)

  useEffect(() => {
    if (order) {
      form.setFieldsValue({
        orderType: order.orderType,
        slipNo: order.slipNo,
        slipDate: dayjs(order.slipDate),
        orderRef: order.orderRef,
        orderDate: order.orderDate ? dayjs(order.orderDate) : undefined,
        partyId: order.party.id,
        transporterId: order.transporter?.id,
        slipBoxes: order.slipBoxes,
        slipWeightKg: order.slipWeightKg,
        remarks: order.remarks,
        lines: order.lines.map(fromLine),
      })
      setDirty(false)
      // lines still without a design: ask again, so "2 designs fit" shows again after a reload
      const open = order.lines.flatMap((l, i) => (l.design ? [] : [i]))
      if (order.status === 'DRAFT' && can('SALES_ENTRY') && open.length) runMatch(open)
    }
  }, [order, form])

  useEffect(() => {
    if (isNew && !form.getFieldValue('lines')?.length) {
      form.setFieldsValue({ orderType: 'SALE', slipDate: dayjs(), lines: [{}] })
    }
  }, [isNew, form])

  // ---- dropdowns (a saved order's own party / design may be inactive by now: keep it in the list) ----
  const partyList: SalesParty[] = useMemo(() => {
    const list = [...(parties.data ?? [])]
    if (order && order.orderType === orderType && !list.some((p) => p.id === order.party.id)) list.push(order.party)
    return list
  }, [parties.data, order, orderType])
  const party = partyList.find((p) => p.id === partyId) ?? (order?.party.id === partyId ? order?.party : undefined)
  const partyOptions = partyList.map((p) => ({ value: p.id, label: `${p.name} · ${p.code}${p.city ? ` · ${p.city}` : ''}` }))
  const transporterOptions = (transporters.data ?? []).map((p) => ({ value: p.id, label: `${p.name} · ${p.code}` }))
  if (order?.transporter && !transporterOptions.some((o) => o.value === order.transporter!.id)) {
    transporterOptions.push({ value: order.transporter.id, label: `${order.transporter.name} · ${order.transporter.code}` })
  }
  const designById = useMemo(() => new Map((designs.data ?? []).map((d) => [d.id, d])), [designs.data])
  const designOptions = (designs.data ?? []).map((d) => ({ value: d.id, label: `${d.name} · ${d.code} · ${d.sizeCode}` }))
  const gradeOptions = (grades.data ?? []).map((g) => ({ value: g.id, label: g.name }))
  for (const l of order?.lines ?? []) {
    if (l.design && !designOptions.some((o) => o.value === l.design!.id)) designOptions.push({ value: l.design.id, label: `${l.design.name} · ${l.design.code}` })
    if (l.grade && !gradeOptions.some((o) => o.value === l.grade!.id)) gradeOptions.push({ value: l.grade.id, label: l.grade.name })
  }

  // ---- matching product descriptions to designs ----

  /** Asks the server about these rows and fills each design / grade that wasn't picked by hand. */
  async function runMatch(indexes: number[]) {
    const lines = (form.getFieldValue('lines') as LineValues[] | undefined) ?? []
    const asked = indexes.filter((i) => lines[i]?.description?.trim())
    if (!asked.length) return
    const descriptions = asked.map((i) => lines[i].description!.trim())
    setMatching(true)
    try {
      const answers = await matchProducts(descriptions)
      // read the rows again: the person may have typed on while the server answered
      const now = [...((form.getFieldValue('lines') as LineValues[] | undefined) ?? [])]
      let changed = false
      asked.forEach((i, n) => {
        const row = now[i]
        if (!row || tidy(row.description) !== tidy(descriptions[n])) return
        const m = answers[n]
        const next: LineValues = { ...row, how: m.how, candidates: m.candidates, matchedFor: tidy(row.description) }
        if (!row.designByHand) next.designId = m.design?.id
        if (!row.gradeByHand) next.gradeId = m.grade?.id
        if (row.designByHand && row.gradeByHand) next.how = row.how
        changed ||= next.designId !== row.designId || next.gradeId !== row.gradeId
        now[i] = next
      })
      form.setFieldValue('lines', now)
      // only a new design or grade makes the last save's stock check out of date
      if (changed) setDirty(true)
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    } finally {
      setMatching(false)
    }
  }

  const matchAll = () => runMatch((rows ?? []).map((_, i) => i))

  /** A person picked a design or grade: Glaze never changes it again, and the badge says so. */
  function chosen(index: number, field: 'design' | 'grade', value?: number) {
    form.setFieldValue(['lines', index, field === 'design' ? 'designByHand' : 'gradeByHand'], value !== undefined)
    if (value !== undefined) form.setFieldValue(['lines', index, 'how'], 'CHOSEN')
  }

  // ---- totals while typing (the stock check comes from the server, on save) ----
  const live = useMemo(() => {
    const list = rows ?? []
    const boxes = list.reduce((s, r) => s + (r?.boxes ?? 0), 0)
    let sqm: number | undefined = 0
    let weight: number | undefined = 0
    for (const r of list) {
      const d = r?.designId ? designById.get(r.designId) : undefined
      sqm = sqm === undefined || d?.sqmPerBox == null ? undefined : sqm + d.sqmPerBox * (r.boxes ?? 0)
      weight = weight === undefined || d?.boxWeightKg == null ? undefined : weight + d.boxWeightKg * (r.boxes ?? 0)
    }
    const unmatched = list.filter((r) => !r?.designId || !r?.gradeId).length
    return { boxes, sqm, weight, unmatched }
  }, [rows, designById])
  const totals = editable
    ? live
    : { boxes: order?.totals.boxes ?? 0, sqm: order?.totals.sqm, weight: order?.totals.weightKg, unmatched: order?.totals.unmatchedLines ?? 0 }
  const boxesDiff = slipBoxes ? totals.boxes - slipBoxes : undefined
  const weightDiff = slipWeight && totals.weight !== undefined ? totals.weight - slipWeight : undefined
  const weightOk = weightDiff === undefined ? undefined : Math.abs(weightDiff) <= slipWeight! * WEIGHT_TOLERANCE
  const stockKnown = order && !dirty && order.status !== 'CANCELLED'

  // ---- saving ----

  async function saveDraft(): Promise<SalesOrder | undefined> {
    let v: FormValues
    try {
      v = await form.validateFields()
    } catch {
      return undefined
    }
    try {
      const saved = await save.mutateAsync({
        id: orderId,
        body: {
          plantId: plantId!,
          orderType: v.orderType,
          slipNo: v.slipNo.trim(),
          slipDate: v.slipDate.format('YYYY-MM-DD'),
          orderRef: v.orderRef?.trim() || undefined,
          orderDate: v.orderDate?.format('YYYY-MM-DD'),
          partyId: v.partyId!,
          transporterId: v.transporterId,
          slipBoxes: v.slipBoxes,
          slipWeightKg: v.slipWeightKg,
          remarks: v.remarks?.trim() || undefined,
          lines: v.lines.map((r) => ({
            description: r.description!.trim(),
            designId: r.designId,
            gradeId: r.gradeId,
            boxes: r.boxes!,
            mrpBox: r.mrpBox,
            mrpPiece: r.mrpPiece,
            batchRef: r.batchRef?.trim() || undefined,
          })),
        },
      })
      setDirty(false)
      return saved
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
      return undefined
    }
  }

  const onSave = async () => {
    const saved = await saveDraft()
    if (!saved) return
    message.success(t('common.saved'))
    if (isNew) navigate(`${BASE}/${saved.id}`, { replace: true })
  }

  /** Save first, so the dialog shows the server's checks for exactly what will be confirmed. */
  const onConfirmClick = async () => {
    const saved = await saveDraft()
    if (!saved) return
    if (isNew) {
      // the saved order opens on its own page (a new editor); it opens the dialog there
      navigate(`${BASE}/${saved.id}`, { replace: true, state: { confirm: true } })
      return
    }
    openConfirm(saved)
  }

  function openConfirm(saved: SalesOrder) {
    if (saved.totals.unmatchedLines > 0) {
      const missing = saved.lines.filter((l) => !l.design || !l.grade).map((l) => l.lineNo).join(', ')
      message.warning(t('sales.matchFirst', { rows: missing }))
      return
    }
    setConfirming(saved)
  }

  const confirmOnArrival = (location.state as { confirm?: boolean } | null)?.confirm === true
  // a ref, because React (in development) runs every effect twice when a page opens
  const arrivalHandled = useRef(false)
  useEffect(() => {
    if (confirmOnArrival && order?.status === 'DRAFT' && !arrivalHandled.current) {
      arrivalHandled.current = true
      navigate(location.pathname, { replace: true, state: null })
      openConfirm(order)
    }
  }, [confirmOnArrival, order])

  const onConfirmed = async () => {
    if (!confirming) return
    try {
      const done = await confirm.mutateAsync(confirming.id)
      message.success(t('sales.confirmedOk', { no: done.slipNo }))
      setConfirming(undefined)
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const onReopen = async () => {
    try {
      await reopen.mutateAsync(orderId!)
      message.success(t('sales.reopenedOk'))
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const onDelete = async () => {
    try {
      await remove.mutateAsync(orderId!)
      message.success(t('store.deleted'))
      navigate(BASE)
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const onCancel = async (reason: string) => {
    try {
      await cancel.mutateAsync({ id: orderId!, reason })
      message.success(t('sales.cancelledOk', { no: order?.slipNo }))
      setCancelling(false)
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  if (isNew && !currentPlant) return <NoPlant />
  if (orderQuery.isLoading) return <div className="center-block"><Spin size="large" /></div>
  if (!isNew && !order) return <div className="panel"><EmptyState title={t('store.notFound')} action={<Link to={BASE}>{t('common.back')}</Link>} /></div>

  const required = { required: true, message: t('common.required') }

  return (
    <>
      <PageHeader
        title={isNew ? t('sales.new') : t('sales.slipTitle', { no: order!.slipNo })}
        subtitle={order && (
          <Space size={10} wrap>
            <SalesStatusTag status={order.status} />
            <OrderTypeTag type={order.orderType} />
            <span>{shortDate(order.slipDate)} · {order.party.name} · {t('finishing.boxesN', { n: order.totals.boxes.toLocaleString('en-IN') })}</span>
          </Space>
        )}
        icon={<FileDoneOutlined />}
        extra={(
          <Space wrap>
            <Button icon={<ArrowLeftOutlined />} onClick={goBack}>{t('common.back')}</Button>
            {editable && !isNew && (
              <Popconfirm title={t('store.deleteTitle')} okText={t('common.delete')} cancelText={t('common.cancel')}
                okButtonProps={{ danger: true }} onConfirm={onDelete}>
                <Button danger icon={<DeleteOutlined />} loading={remove.isPending}>{t('store.deleteDraft')}</Button>
              </Popconfirm>
            )}
            {editable && <Button icon={<SaveOutlined />} loading={save.isPending && !confirming} onClick={onSave}>{t('store.saveDraft')}</Button>}
            {editable && (
              <Button type="primary" icon={<CheckCircleOutlined />} disabled={save.isPending || confirm.isPending} onClick={onConfirmClick}>
                {t('sales.confirm')}
              </Button>
            )}
            {order?.status === 'CONFIRMED' && can('SALES_ENTRY') && (
              <Popconfirm title={t('sales.reopenTitle')} description={t('sales.reopenHint')} okText={t('sales.reopen')}
                cancelText={t('common.cancel')} onConfirm={onReopen}>
                <Button icon={<EditOutlined />} loading={reopen.isPending}>{t('sales.reopen')}</Button>
              </Popconfirm>
            )}
            {order?.status === 'CONFIRMED' && can('SALES_CANCEL') && (
              <Button danger icon={<StopOutlined />} onClick={() => setCancelling(true)}>{t('sales.cancel')}</Button>
            )}
          </Space>
        )}
      />

      <FadeIn delay={0.04}>
        {editable && <Alert className="doc-alert" type="info" showIcon title={t('sales.hint')} />}
        {order?.status === 'CONFIRMED' && (
          <Alert className="doc-alert" type="success" showIcon
            title={t('sales.confirmedInfo', { by: order.confirmedBy, at: dateTime(order.confirmedAt) })} />
        )}
        {order?.status === 'CANCELLED' && (
          <Alert className="doc-alert" type="error" showIcon
            title={t('store.cancelledInfo', { by: order.cancelledBy, at: dateTime(order.cancelledAt), reason: order.cancelReason })} />
        )}
      </FadeIn>

      <Form<FormValues> form={form} layout="vertical" disabled={!editable} className={editable ? undefined : 'doc-readonly'}
        onValuesChange={() => setDirty(true)}>
        <FadeIn delay={0.08} className="panel doc-panel">
          <div className="form-section">{t('sales.slipSection')}</div>
          <Form.Item name="orderType" className="sales-type">
            <Segmented<SalesOrderType>
              options={[
                { value: 'SALE', label: t('sales.typeLong.SALE') },
                { value: 'JOBWORK', label: t('sales.typeLong.JOBWORK') },
              ]}
              // another kind of order is billed to another kind of party
              onChange={() => form.setFieldValue('partyId', undefined)}
            />
          </Form.Item>
          <div className="sales-head">
            <Form.Item name="slipNo" label={t('sales.slipNo')} rules={[required]} normalize={(v?: string) => v?.toUpperCase()}>
              <Input maxLength={30} inputMode="numeric" />
            </Form.Item>
            <Form.Item name="slipDate" label={t('sales.slipDate')} rules={[required]}>
              <DatePicker format="DD-MM-YYYY" style={{ width: '100%' }} disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
            </Form.Item>
            <Form.Item name="orderRef" label={t('sales.orderRef')} normalize={(v?: string) => v?.toUpperCase()}>
              <Input maxLength={30} />
            </Form.Item>
            <Form.Item name="orderDate" label={t('sales.orderDate')} dependencies={['slipDate']} rules={[({ getFieldValue }) => ({
              validator: async (_, v?: Dayjs) => {
                const slip = getFieldValue('slipDate') as Dayjs | undefined
                if (v && slip && v.isAfter(slip, 'day')) throw new Error(t('sales.orderAfterSlip'))
              },
            })]}>
              <DatePicker format="DD-MM-YYYY" style={{ width: '100%' }} disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
            </Form.Item>
          </div>
          <div className="sales-head">
            <Form.Item name="partyId" label={t(`sales.partyLabel.${orderType}`)} rules={[required]} className="span-2"
              extra={party && <PartyFacts party={party} />}>
              <Select options={partyOptions} loading={parties.isLoading} showSearch={{ optionFilterProp: 'label' }}
                placeholder={t(`sales.partyPlaceholder.${orderType}`)} notFoundContent={t(`sales.noParties.${orderType}`)} />
            </Form.Item>
            <Form.Item name="transporterId" label={t('sales.transporter')} tooltip={t('sales.transporterHint')}>
              <Select options={transporterOptions} loading={transporters.isLoading} allowClear showSearch={{ optionFilterProp: 'label' }} />
            </Form.Item>
            <Form.Item name="remarks" label={t('sales.narration')}>
              <Input maxLength={500} />
            </Form.Item>
          </div>
          <div className="sales-head">
            <Form.Item name="slipBoxes" label={t('sales.slipBoxes')} tooltip={t('sales.slipTotalsHint')}>
              <InputNumber<number> min={1} precision={0} style={{ width: '100%' }} inputMode="numeric" />
            </Form.Item>
            <Form.Item name="slipWeightKg" label={t('sales.slipWeight')}>
              <InputNumber<number> min={0.1} precision={1} suffix="kg" style={{ width: '100%' }} inputMode="decimal" />
            </Form.Item>
          </div>
        </FadeIn>

        <FadeIn delay={0.1} className="panel doc-panel">
          <div className="sales-lines-title">
            <div className="form-section">{t('sales.productsSection')}</div>
            {editable && (
              <Tooltip title={t('sales.matchAllHint')}>
                <Button icon={<ThunderboltOutlined />} loading={matching} onClick={matchAll}>{t('sales.matchAll')}</Button>
              </Tooltip>
            )}
          </div>
          <Form.List name="lines" rules={[{ validator: async (_, v?: LineValues[]) => { if (!v?.length) throw new Error(t('sales.noLines')) } }]}>
            {(fields, { add, remove: removeRow }, { errors }) => (
              <>
                <div className="doc-lines cols-sales">
                  <div className="doc-lines-head" aria-hidden>
                    <span>#</span>
                    <span>{t('sales.description')}</span>
                    <span>{t('fg.design')}</span>
                    <span>{t('finishing.grade')}</span>
                    <span className="num">{t('finishing.boxes')}</span>
                    <span className="num">{t('sales.mrpBox')}</span>
                    <span>{t('sales.batchRef')}</span>
                    <span />
                  </div>
                  {fields.map((field, index) => {
                    const row = rows?.[index]
                    const saved: SalesOrderLine | undefined = stockKnown ? order?.lines[index] : undefined
                    return (
                      <div key={field.key} className="doc-line">
                        <span className="line-no">{index + 1}</span>
                        <Cell label={t('sales.description')} className="span-2">
                          <Form.Item name={[field.name, 'description']} rules={[required]}>
                            <Input maxLength={200} aria-label={t('sales.description')} placeholder="O7VM 600X1200-2 FLAVOUR IVORY PDR-P"
                              onBlur={() => {
                                if (row?.description?.trim() && tidy(row.description) !== row.matchedFor) runMatch([index])
                              }} />
                          </Form.Item>
                          {row?.how && <MatchBadge how={row.how} candidates={row.candidates} />}
                        </Cell>
                        <Cell label={t('fg.design')} className="span-2">
                          <Form.Item name={[field.name, 'designId']}>
                            <Select options={designOptions} loading={designs.isLoading} allowClear showSearch={{ optionFilterProp: 'label' }}
                              popupMatchSelectWidth={false} aria-label={t('fg.design')} placeholder={t('sales.chooseDesign')}
                              onChange={(v?: number) => chosen(field.name, 'design', v)} />
                          </Form.Item>
                        </Cell>
                        <Cell label={t('finishing.grade')}>
                          <Form.Item name={[field.name, 'gradeId']}>
                            <Select options={gradeOptions} loading={grades.isLoading} allowClear aria-label={t('finishing.grade')}
                              onChange={(v?: number) => chosen(field.name, 'grade', v)} />
                          </Form.Item>
                        </Cell>
                        <Cell label={t('finishing.boxes')}>
                          <Form.Item name={[field.name, 'boxes']} rules={[required]}>
                            <InputNumber<number> min={1} precision={0} style={{ width: '100%' }} inputMode="numeric" aria-label={t('finishing.boxes')} />
                          </Form.Item>
                          {saved?.inStock != null && <StockHint line={saved} />}
                        </Cell>
                        <Cell label={t('sales.mrpBox')}>
                          <Form.Item name={[field.name, 'mrpBox']}>
                            <InputNumber<number> min={0} precision={2} prefix="₹" style={{ width: '100%' }} inputMode="decimal" aria-label={t('sales.mrpBox')} />
                          </Form.Item>
                        </Cell>
                        <Cell label={t('sales.batchRef')}>
                          <Form.Item name={[field.name, 'batchRef']} tooltip={t('sales.batchRefHint')} normalize={(v?: string) => v?.toUpperCase()}>
                            <Input maxLength={30} aria-label={t('sales.batchRef')} />
                          </Form.Item>
                        </Cell>
                        <div className="line-remove">
                          {editable && fields.length > 1 && (
                            <Button type="text" danger icon={<MinusCircleOutlined />} aria-label={t('common.delete')} onClick={() => removeRow(field.name)} />
                          )}
                        </div>
                        {(['mrpPiece', 'how', 'candidates', 'matchedFor', 'designByHand', 'gradeByHand'] as const).map((name) => (
                          <Form.Item key={name} name={[field.name, name]} hidden noStyle><Stash /></Form.Item>
                        ))}
                      </div>
                    )
                  })}
                </div>
                <Form.ErrorList errors={errors} />
                {editable && (
                  <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add({})}>{t('finishing.addRow')}</Button>
                )}
              </>
            )}
          </Form.List>

          <div className="sales-checks">
            <Check label={t('finishing.boxes')} value={totals.boxes.toLocaleString('en-IN')}
              note={boxesDiff === undefined ? undefined : boxesDiff === 0 ? t('sales.sameAsSlip') : t('sales.vsSlip', { diff: signed(boxesDiff), n: slipBoxes })}
              tone={boxesDiff === undefined ? undefined : boxesDiff === 0 ? 'ok' : 'warn'} />
            <Check label="m²" value={totals.sqm === undefined ? '–' : qty(totals.sqm, 2)} />
            <Check label={t('sales.weight')} value={totals.weight === undefined ? '–' : `${qty(totals.weight, 1)} kg`}
              note={totals.weight === undefined ? t('sales.weightUnknown')
                : weightDiff === undefined ? undefined : t('sales.weightVsSlip', { diff: signed(Math.round(weightDiff * 10) / 10) })}
              tone={weightOk === undefined ? undefined : weightOk ? 'ok' : 'warn'} />
            <Check label={t('sales.matched')} value={`${(rows?.length ?? 0) - totals.unmatched} / ${rows?.length ?? 0}`}
              tone={totals.unmatched ? 'warn' : 'ok'}
              note={totals.unmatched ? t('sales.unmatchedN', { count: totals.unmatched }) : t('sales.allMatched')} />
            <Check label={t('sales.stock')}
              value={!stockKnown ? '–' : order!.totals.shortLines ? t('sales.shortLinesN', { count: order!.totals.shortLines }) : t('sales.allInStock')}
              tone={!stockKnown ? undefined : order!.totals.shortLines ? 'warn' : 'ok'}
              note={!stockKnown && order?.status !== 'CANCELLED' ? t('sales.saveToCheck') : undefined} />
          </div>
        </FadeIn>
      </Form>

      <Modal open={confirming !== undefined} title={t('sales.confirmTitle', { no: confirming?.slipNo })} okText={t('sales.confirm')}
        cancelText={t('common.cancel')} confirmLoading={confirm.isPending} onOk={onConfirmed} onCancel={() => setConfirming(undefined)}>
        {confirming && <ConfirmChecks order={confirming} />}
      </Modal>
      <ReasonModal
        open={cancelling}
        title={t('sales.cancelTitle', { no: order?.slipNo })}
        hint={t('sales.cancelHint')}
        okText={t('sales.cancel')}
        loading={cancel.isPending}
        onOk={onCancel}
        onClose={() => setCancelling(false)}
      />
    </>
  )
}

/** A saved line, as the editor's form holds it. */
function fromLine(l: SalesOrderLine): LineValues {
  return {
    description: l.description,
    designId: l.design?.id,
    gradeId: l.grade?.id,
    boxes: l.boxes,
    mrpBox: l.mrpBox,
    mrpPiece: l.mrpPiece,
    batchRef: l.batchRef,
    how: !l.design ? 'NONE' : l.remembered ? 'REMEMBERED' : 'SAVED',
    matchedFor: tidy(l.description),
    // what was saved stays as it is; only empty fields are filled by matching
    designByHand: !!l.design,
    gradeByHand: !!l.grade,
  }
}

/** +5 / −813.5 (a real minus sign, like money()) */
const signed = (n: number) => (n > 0 ? '+' : n < 0 ? '−' : '') + Math.abs(n).toLocaleString('en-IN')
/** ₹ 5,00,000 - credit limits are whole rupees */
const rupees = (n: number) => `₹ ${n.toLocaleString('en-IN')}`

/** What the badge under a description says. */
function MatchBadge({ how, candidates }: { how: RowHow; candidates?: SalesOption[] }) {
  const { t } = useTranslation()
  const badge = (
    <span className={`match-badge match-${how.toLowerCase()}`}>
      {how === 'REMEMBERED' || how === 'CHOSEN' || how === 'SAVED' ? <CheckCircleOutlined />
        : how === 'GUESSED' ? <QuestionCircleOutlined /> : <WarningOutlined />}
      {t(`sales.how.${how}`, { count: candidates?.length ?? 0 })}
    </span>
  )
  return how === 'SEVERAL' && candidates?.length
    ? <Tooltip title={candidates.map((c) => `${c.name} · ${c.code}`).join(', ')}>{badge}</Tooltip>
    : badge
}

/** Under the boxes: what the plant has of this design and grade (as of the last save). */
function StockHint({ line }: { line: SalesOrderLine }) {
  const { t } = useTranslation()
  const inStock = line.inStock!.toLocaleString('en-IN')
  return line.shortBy
    ? <div className="line-hint short">{t('sales.shortBy', { n: line.shortBy.toLocaleString('en-IN'), stock: inStock })}</div>
    : <div className="line-hint">{t('sales.inStock', { n: inStock })}</div>
}

/** City, GSTIN and credit terms of the party the bill goes to. */
function PartyFacts({ party }: { party: SalesParty }) {
  const { t } = useTranslation()
  const credit = party.creditLimit != null
    ? t('sales.credit', { limit: rupees(party.creditLimit), days: party.creditDays ?? '–' })
    : t('sales.noCreditLimit')
  return (
    <span className="party-facts">
      {[party.city, party.gstin, credit].filter(Boolean).join(' · ')}
      {!party.active && <b> · {t('common.inactive')}</b>}
    </span>
  )
}

function Check({ label, value, note, tone }: { label: string; value: string; note?: string; tone?: 'ok' | 'warn' }) {
  return (
    <div className={`sales-check${tone ? ` ${tone}` : ''}`}>
      <span className="stat-label">{label}</span>
      <span className="sales-check-value">{value}</span>
      {note && <span className="sales-check-note">{note}</span>}
    </div>
  )
}

/** The confirm dialog: what the office should look at once more, from the server's checks. */
function ConfirmChecks({ order }: { order: SalesOrder }) {
  const { t } = useTranslation()
  const short = order.lines.filter((l) => l.shortBy)
  const totals = order.totals
  return (
    <div className="confirm-checks">
      <p>{t('sales.confirmHint', { party: order.party.name, boxes: totals.boxes.toLocaleString('en-IN') })}</p>
      {short.length > 0 && (
        <Alert type="warning" showIcon title={t('sales.shortTitle')} description={(
          <ul>
            {short.map((l) => (
              <li key={l.id}>{t('sales.shortLine', {
                design: l.design?.name, grade: l.grade?.name, n: l.shortBy!.toLocaleString('en-IN'), stock: (l.inStock ?? 0).toLocaleString('en-IN'),
              })}</li>
            ))}
          </ul>
        )} />
      )}
      {totals.boxesDiff != null && totals.boxesDiff !== 0 && (
        <Alert type="warning" showIcon title={t('sales.boxesMismatch', { slip: order.slipBoxes, ours: totals.boxes })} />
      )}
      {totals.weightOk === false && (
        <Alert type="warning" showIcon title={t('sales.weightMismatch', { slip: qty(order.slipWeightKg, 1), ours: qty(totals.weightKg, 1) })} />
      )}
      <Alert type="info" showIcon title={order.party.creditLimit != null
        ? t('sales.creditCheck', { limit: rupees(order.party.creditLimit), days: order.party.creditDays ?? '–' })
        : t('sales.noCreditLimit')} />
      <p className="cell-sub">{t('sales.rememberHint')}</p>
    </div>
  )
}
