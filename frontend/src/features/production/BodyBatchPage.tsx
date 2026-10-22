import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Alert, App, Button, Col, DatePicker, Form, Input, InputNumber, Modal, Popconfirm, Row, Select, Space, Spin, Tag } from 'antd'
import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  ExperimentOutlined,
  MinusCircleOutlined,
  PlusOutlined,
  SaveOutlined,
  StopOutlined,
  SyncOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import dayjs, { type Dayjs } from 'dayjs'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import type { BodyBatch, BodyBatchRequest, BodyBatchSummary } from '../../api/types'
import NoPlant from '../store/NoPlant'
import { dateTime, decimalsOf, qty, shortDate } from '../store/format'
import {
  useBodyBatch,
  useBodyOverview,
  useCancelBatch,
  useChargeBatch,
  useCurrentShift,
  useDeleteBatch,
  useProdGodowns,
  useProdMachines,
  useProdMaterials,
  useProdRecipes,
  useProdShifts,
  useProdStock,
  useSaveBatch,
} from './api'
import BatchStatusTag from './BatchStatusTag'
import DischargeModal from './DischargeModal'
import ReasonModal from './ReasonModal'
import { DATE_TIME, dayClock, duration, toLocalDateTime } from './time'

const BASE = '/production/body-batches'

interface LineValues {
  materialId?: number
  locationId?: number
  quantity?: number
}

interface FormValues {
  prodDate: Dayjs
  shiftId?: number
  ballMillId?: number
  recipeId?: number
  chargeTon?: number
  waterLitres?: number
  millingStart: Dayjs
  remarks?: string
  lines: LineValues[]
}

const toForm = (b: BodyBatch): FormValues => ({
  prodDate: dayjs(b.prodDate),
  shiftId: b.shiftId,
  ballMillId: b.ballMillId,
  recipeId: b.recipeId,
  chargeTon: b.chargeTon,
  waterLitres: b.waterLitres,
  millingStart: dayjs(b.millingStart),
  remarks: b.remarks,
  lines: b.lines.map((l) => ({ materialId: l.materialId, locationId: l.locationId, quantity: l.quantity })),
})

const toRequest = (v: FormValues, plantId: number): BodyBatchRequest => ({
  plantId,
  prodDate: v.prodDate.format('YYYY-MM-DD'),
  shiftId: v.shiftId!,
  ballMillId: v.ballMillId!,
  recipeId: v.recipeId,
  chargeTon: v.chargeTon!,
  waterLitres: v.waterLitres,
  millingStart: toLocalDateTime(v.millingStart),
  remarks: v.remarks?.trim() || undefined,
  lines: (v.lines ?? []).map((l) => ({ materialId: l.materialId!, locationId: l.locationId!, quantity: l.quantity! })),
})

/** The list shape the discharge dialog needs, from the full batch. */
const toSummary = (b: BodyBatch): BodyBatchSummary => ({
  id: b.id, batchNo: b.batchNo, prodDate: b.prodDate, shiftCode: b.shiftCode, ballMillCode: b.ballMillCode,
  ballMillName: b.ballMillName, recipeCode: b.recipeCode, chargeTon: b.chargeTon, status: b.status,
  millingStart: b.millingStart, millingEnd: b.millingEnd, slipTankCode: b.slipTankCode, slipDensity: b.slipDensity,
  slipViscosity: b.slipViscosity, slipResidue: b.slipResidue,
})

function Cell({ label, className, children }: { label: string; className?: string; children: ReactNode }) {
  return (
    <div className={`line-cell ${className ?? ''}`}>
      <span className="line-label">{label}</span>
      {children}
    </div>
  )
}

/** /production/body-batches/new and /production/body-batches/12 - a fresh editor per batch. */
export default function BodyBatchPage() {
  const { id } = useParams()
  return <BatchEditor key={id} batchId={id && id !== 'new' ? Number(id) : undefined} />
}

function BatchEditor({ batchId }: { batchId?: number }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const navigate = useNavigate()
  const location = useLocation()
  const { can, currentPlant } = useAuth()
  const isNew = batchId === undefined
  // Back to where the user came from (the home screen or the list); straight to the list if opened directly.
  const goBack = () => (location.key !== 'default' ? navigate(-1) : navigate(BASE))

  const batchQuery = useBodyBatch(batchId)
  const batch = batchQuery.data
  const plantId = batch?.plantId ?? currentPlant?.id
  const canEntry = can('BODY_PREP_ENTRY')
  const editable = canEntry && (isNew || batch?.status === 'DRAFT')

  const current = useCurrentShift()
  const shifts = useProdShifts()
  const recipes = useProdRecipes()
  const machines = useProdMachines(plantId)
  const materials = useProdMaterials()
  const godowns = useProdGodowns(plantId)
  const stock = useProdStock(plantId)
  const overview = useBodyOverview(plantId)

  const save = useSaveBatch()
  const charge = useChargeBatch()
  const cancel = useCancelBatch()
  const remove = useDeleteBatch()
  const [confirmCharge, setConfirmCharge] = useState(false)
  const [discharging, setDischarging] = useState(false)
  const [cancelling, setCancelling] = useState(false)

  const [form] = Form.useForm<FormValues>()
  const lines = Form.useWatch('lines', form) as LineValues[] | undefined
  const ballMillId = Form.useWatch('ballMillId', form) as number | undefined

  useEffect(() => {
    if (batch) form.setFieldsValue(toForm(batch))
  }, [batch, form])

  // A new charge starts in the shift running now (night shift after midnight -> yesterday's date).
  useEffect(() => {
    if (!isNew || !current.data) return
    if (!form.isFieldTouched('prodDate')) form.setFieldValue('prodDate', dayjs(current.data.prodDate))
    if (!form.isFieldTouched('shiftId') && current.data.shiftId) form.setFieldValue('shiftId', current.data.shiftId)
  }, [isNew, current.data, form])

  const firstGodown = godowns.data?.[0]?.id
  useEffect(() => {
    if (isNew && firstGodown && !form.getFieldValue(['lines', 0, 'locationId'])) {
      form.setFieldValue(['lines', 0, 'locationId'], firstGodown)
    }
  }, [isNew, firstGodown, form])

  // ---- options (anything already on the batch stays visible even if retired since) ----

  const busy = useMemo(() => new Map((overview.data?.inMills ?? []).map((b) => [b.ballMillCode, b.batchNo])), [overview.data])
  const millOptions = useMemo(() => {
    const opts = (machines.data ?? []).filter((m) => m.type === 'BALL_MILL').map((m) => {
      const holds = busy.get(m.code)
      return { value: m.id, label: `${m.code} · ${m.name}${holds && holds !== batch?.batchNo ? ` (${t('prod.millBusy', { no: holds })})` : ''}` }
    })
    if (batch && !opts.some((o) => o.value === batch.ballMillId)) opts.push({ value: batch.ballMillId, label: `${batch.ballMillCode} · ${batch.ballMillName}` })
    return opts
  }, [machines.data, busy, batch, t])

  const shiftOptions = useMemo(() => {
    const opts = (shifts.data ?? []).map((s) => ({ value: s.id, label: `${s.code} · ${s.name}` }))
    if (batch && !opts.some((o) => o.value === batch.shiftId)) opts.push({ value: batch.shiftId, label: `${batch.shiftCode} · ${batch.shiftName}` })
    return opts
  }, [shifts.data, batch])

  const recipeOptions = useMemo(() => {
    const opts = (recipes.data ?? []).map((r) => ({ value: r.id, label: `${r.code} · ${r.name}` }))
    if (batch?.recipeId && !opts.some((o) => o.value === batch.recipeId)) opts.push({ value: batch.recipeId, label: `${batch.recipeCode} · ${batch.recipeName}` })
    return opts
  }, [recipes.data, batch])

  const materialById = useMemo(() => {
    const map = new Map<number, { code: string; name: string; unit: string; decimals: number }>()
    for (const m of materials.data ?? []) map.set(m.id, { code: m.code, name: m.name, unit: m.extra ?? '', decimals: m.decimals })
    for (const l of batch?.lines ?? []) {
      if (!map.has(l.materialId)) map.set(l.materialId, { code: l.materialCode, name: l.materialName, unit: l.unitCode, decimals: l.unitDecimals })
    }
    return map
  }, [materials.data, batch])
  const materialOptions = [...materialById].map(([value, m]) => ({ value, label: `${m.code} · ${m.name}` }))

  const godownOptions = useMemo(() => {
    const opts = (godowns.data ?? []).map((g) => ({ value: g.id, label: `${g.code} · ${g.name}` }))
    for (const l of batch?.lines ?? []) {
      if (!opts.some((o) => o.value === l.locationId)) opts.push({ value: l.locationId, label: `${l.locationCode} · ${l.locationName}` })
    }
    return opts
  }, [godowns.data, batch])

  const available = (materialId?: number, locationId?: number) => {
    if (!materialId || !locationId || !stock.data) return undefined
    return stock.data.find((s) => s.materialId === materialId && s.locationId === locationId)?.quantity ?? 0
  }

  /** Godown holding the most of a material - the natural place to take it from. */
  const bestGodown = (materialId: number) =>
    [...(stock.data ?? [])].filter((s) => s.materialId === materialId).sort((a, b) => b.quantity - a.quantity)[0]?.locationId
    ?? firstGodown

  const fillFromRecipe = () => {
    const recipe = recipes.data?.find((r) => r.id === form.getFieldValue('recipeId'))
    const charge = form.getFieldValue('chargeTon') as number | undefined
    if (!recipe || !charge) {
      message.warning(t('prod.fillNeeds'))
      return
    }
    // toFixed rounds to what the unit allows (0.3 x 40 = 12.000000000000002 -> 12)
    form.setFieldValue('lines', recipe.lines.map((l) => ({
      materialId: l.materialId,
      locationId: bestGodown(l.materialId),
      quantity: Number((l.qtyPerTon * charge).toFixed(l.unitDecimals)),
    })))
  }

  // ---- actions ----

  async function saveDraft(): Promise<BodyBatch | undefined> {
    let values: FormValues
    try {
      values = await form.validateFields()
    } catch {
      return undefined
    }
    try {
      return await save.mutateAsync({ id: batchId, body: toRequest(values, plantId!) })
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

  const onChargeClick = async () => {
    try {
      await form.validateFields()
      setConfirmCharge(true)
    } catch {
      /* messages are under the fields */
    }
  }

  const onChargeConfirmed = async () => {
    const saved = await saveDraft()
    if (!saved) return
    try {
      const done = await charge.mutateAsync(saved.id)
      message.success(t('prod.chargedOk', { no: done.batchNo }))
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
    setConfirmCharge(false)
    if (isNew) navigate(`${BASE}/${saved.id}`, { replace: true })
  }

  const onDelete = async () => {
    try {
      await remove.mutateAsync(batchId!)
      message.success(t('store.deleted'))
      navigate(BASE)
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const onCancel = async (reason: string) => {
    try {
      await cancel.mutateAsync({ id: batchId!, reason })
      message.success(t('store.cancelOk', { no: batch?.batchNo }))
      setCancelling(false)
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  // ---- render ----

  if (isNew && !currentPlant) return <NoPlant />
  if (batchQuery.isLoading) return <div className="center-block"><Spin size="large" /></div>
  if (!isNew && !batch) {
    return <div className="panel"><EmptyState title={t('store.notFound')} action={<Link to={BASE}>{t('common.back')}</Link>} /></div>
  }

  const required = { required: true, message: t('common.required') }
  const decimalsRule = (decimals: number, unit?: string) => ({
    validator: async (_: unknown, v?: number | null) => {
      if (v != null && decimalsOf(v) > decimals) throw new Error(t('store.decimals', { unit, n: decimals }))
    },
  })
  const chosenMill = millOptions.find((o) => o.value === ballMillId)
  const noMills = editable && machines.data !== undefined && !machines.data.some((m) => m.type === 'BALL_MILL')
  const status = batch?.status

  const actions = (
    <Space wrap>
      <Button icon={<ArrowLeftOutlined />} onClick={goBack}>{t('common.back')}</Button>
      {editable && !isNew && (
        <Popconfirm title={t('store.deleteTitle')} okText={t('common.delete')} cancelText={t('common.cancel')}
          okButtonProps={{ danger: true }} onConfirm={onDelete}>
          <Button danger icon={<DeleteOutlined />} loading={remove.isPending}>{t('store.deleteDraft')}</Button>
        </Popconfirm>
      )}
      {editable && <Button icon={<SaveOutlined />} loading={save.isPending && !confirmCharge} onClick={onSave}>{t('store.saveDraft')}</Button>}
      {editable && <Button type="primary" icon={<ThunderboltOutlined />} disabled={save.isPending || charge.isPending} onClick={onChargeClick}>{t('prod.charge')}</Button>}
      {canEntry && status === 'CHARGED' && (
        <Button type="primary" icon={<CheckCircleOutlined />} onClick={() => setDischarging(true)}>{t('prod.discharge')}</Button>
      )}
      {canEntry && status === 'DISCHARGED' && <Button onClick={() => setDischarging(true)}>{t('prod.updateTest')}</Button>}
      {can('PRODUCTION_CANCEL') && (status === 'CHARGED' || status === 'DISCHARGED') && (
        <Button danger icon={<StopOutlined />} onClick={() => setCancelling(true)}>{t('prod.cancelBatch')}</Button>
      )}
    </Space>
  )

  return (
    <>
      <PageHeader
        title={isNew ? t('prod.newBatch') : (batch!.batchNo ?? t('prod.draftBatch'))}
        subtitle={batch && (
          <Space size={10} wrap>
            <BatchStatusTag status={batch.status} />
            <span>{shortDate(batch.prodDate)} · {t('prod.shift')} {batch.shiftCode}</span>
            <span>{t('store.createdBy', { by: batch.createdBy })}</span>
          </Space>
        )}
        icon={<SyncOutlined />}
        extra={actions}
      />

      <FadeIn delay={0.04}>
        {editable && !noMills && <Alert className="doc-alert" type="info" showIcon title={t('prod.draftInfo')} />}
        {noMills && <Alert className="doc-alert" type="warning" showIcon title={t('prod.noMachines', { type: t('machineType.BALL_MILL') })} />}
        {batch?.chargedAt && status !== 'CANCELLED' && (
          <Alert className="doc-alert" type="success" showIcon
            title={t('prod.chargedInfo', { by: batch.chargedBy, at: dateTime(batch.chargedAt) })} />
        )}
        {status === 'CANCELLED' && (
          <Alert className="doc-alert" type="error" showIcon
            title={t('prod.cancelledInfo', { by: batch!.cancelledBy, at: dateTime(batch!.cancelledAt), reason: batch!.cancelReason })} />
        )}
      </FadeIn>

      {/* Slip test, store issue and trace - once the batch has been charged */}
      {batch && status !== 'DRAFT' && (
        <FadeIn delay={0.06} className="panel doc-panel">
          <div className="form-section">{t('prod.slipTest')}</div>
          <div className="fact-grid">
            <div>
              <span className="stat-label">{t('prod.millingStart')}</span>
              <b>{dayClock(batch.millingStart)}</b>
              {status === 'CHARGED' && <span className="cell-sub">{t('prod.millingFor', { time: duration(batch.millingStart) })}</span>}
            </div>
            <div>
              <span className="stat-label">{t('prod.millingEnd')}</span>
              <b>{batch.millingEnd ? dayClock(batch.millingEnd) : '–'}</b>
              {batch.millingEnd && <span className="cell-sub">{t('prod.milled', { time: duration(batch.millingStart, batch.millingEnd) })}</span>}
            </div>
            <div><span className="stat-label">{t('prod.slipTank')}</span><b>{batch.slipTankCode ?? '–'}</b></div>
            <div><span className="stat-label">{t('prod.density')}</span><b>{batch.slipDensity != null ? `${qty(batch.slipDensity, 1)} g/L` : '–'}</b></div>
            <div><span className="stat-label">{t('prod.viscosity')}</span><b>{batch.slipViscosity != null ? `${qty(batch.slipViscosity, 1)} s` : '–'}</b></div>
            <div><span className="stat-label">{t('prod.residue')}</span><b>{batch.slipResidue != null ? `${qty(batch.slipResidue, 2)} %` : '–'}</b></div>
            {batch.issueDocNo && (
              <div>
                <span className="stat-label">{t('prod.storeIssue')}</span>
                {can('STORE_VIEW')
                  ? <Link to={`/store/issues/${batch.issueDocumentId}`}><b>{batch.issueDocNo}</b></Link>
                  : <b>{batch.issueDocNo}</b>}
              </div>
            )}
            {batch.sprayRuns.length > 0 && (
              <div>
                <span className="stat-label">{t('prod.usedIn')}</span>
                {batch.sprayRuns.map((r) => (
                  <div key={r.runId}>
                    <Link to={`/production/spray-runs/${r.runId}`}><b>{r.runNo}</b></Link>{' '}
                    <Tag color={r.finished ? 'green' : 'default'}>{r.finished ? t('prod.usedUp') : t('prod.partly')}</Tag>
                  </div>
                ))}
              </div>
            )}
          </div>
        </FadeIn>
      )}

      <Form<FormValues> form={form} layout="vertical" disabled={!editable} className={editable ? undefined : 'doc-readonly'}
        initialValues={{ millingStart: dayjs(), lines: [{}] }}>
        <FadeIn delay={0.08} className="panel doc-panel">
          <div className="form-section">{t('store.details')}</div>
          <Row gutter={16}>
            <Col xs={12} md={6}>
              <Form.Item name="prodDate" label={t('prod.prodDate')} rules={[required]}>
                <DatePicker format="DD-MM-YYYY" style={{ width: '100%' }} disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item name="shiftId" label={t('prod.shift')} rules={[required]}>
                <Select options={shiftOptions} loading={shifts.isLoading} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="ballMillId" label={t('prod.ballMill')} rules={[required]}>
                <Select options={millOptions} loading={machines.isLoading} showSearch={{ optionFilterProp: 'label' }} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="recipeId" label={t('prod.recipe')}>
                <Select allowClear options={recipeOptions} loading={recipes.isLoading} showSearch={{ optionFilterProp: 'label' }} />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item name="chargeTon" label={t('prod.chargeTon')} rules={[required]}>
                <InputNumber<number> min={0} step={1} suffix="T" style={{ width: '100%' }} inputMode="decimal" />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item name="waterLitres" label={t('prod.water')}>
                <InputNumber<number> min={0} step={100} suffix="L" style={{ width: '100%' }} inputMode="decimal" />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="millingStart" label={t('prod.millingStart')} rules={[required]}>
                <DatePicker showTime={{ format: 'HH:mm' }} format={DATE_TIME} style={{ width: '100%' }} needConfirm={false}
                  disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="remarks" label={t('store.remarks')}>
                <Input.TextArea maxLength={500} autoSize={{ minRows: 1, maxRows: 4 }} />
              </Form.Item>
            </Col>
          </Row>
        </FadeIn>

        <FadeIn delay={0.12} className="panel doc-panel">
          <div className="panel-title-row">
            <div className="form-section" style={{ flex: 1 }}>{t('prod.rawMaterials')}</div>
            {editable && (
              <Button size="small" icon={<ExperimentOutlined />} onClick={fillFromRecipe} title={t('prod.fillHint')}>
                {t('prod.fillFromRecipe')}
              </Button>
            )}
          </div>
          <Form.List name="lines" rules={[{ validator: async (_, v?: LineValues[]) => {
            if (!v?.length) throw new Error(t('store.noLines'))
          } }]}>
            {(fields, { add, remove: removeLine }, { errors }) => (
              <>
                <div className="doc-lines cols-batch">
                  <div className="doc-lines-head" aria-hidden>
                    <span>#</span>
                    <span>{t('store.material')}</span>
                    <span>{t('store.godown')}</span>
                    <span>{t('store.quantity')}</span>
                    <span />
                  </div>
                  {fields.map((field, index) => {
                    const line = lines?.[index]
                    const m = line?.materialId ? materialById.get(line.materialId) : undefined
                    const decimals = m?.decimals ?? 3
                    const avail = editable ? available(line?.materialId, line?.locationId) : undefined
                    return (
                      <div key={field.key} className="doc-line">
                        <span className="line-no">{index + 1}</span>
                        <Cell label={t('store.material')} className="span-2">
                          <Form.Item name={[field.name, 'materialId']} rules={[required]}>
                            <Select showSearch={{ optionFilterProp: 'label' }} options={materialOptions} loading={materials.isLoading}
                              popupMatchSelectWidth={false} aria-label={t('store.material')} />
                          </Form.Item>
                        </Cell>
                        <Cell label={t('store.godown')}>
                          <Form.Item name={[field.name, 'locationId']} rules={[required]}>
                            <Select options={godownOptions} loading={godowns.isLoading} popupMatchSelectWidth={false} aria-label={t('store.godown')} />
                          </Form.Item>
                        </Cell>
                        <Cell label={t('store.quantity')}>
                          <Form.Item name={[field.name, 'quantity']} rules={[required, decimalsRule(decimals, m?.unit)]}>
                            <InputNumber<number> min={0} suffix={m?.unit} style={{ width: '100%' }} inputMode="decimal" aria-label={t('store.quantity')} />
                          </Form.Item>
                          {avail !== undefined && (
                            <div className={`line-hint${line?.quantity != null && line.quantity > avail ? ' short' : ''}`}>
                              {t('store.available', { qty: `${qty(avail, decimals)} ${m?.unit ?? ''}` })}
                            </div>
                          )}
                        </Cell>
                        <div className="line-remove">
                          {editable && fields.length > 1 && (
                            <Button type="text" danger icon={<MinusCircleOutlined />} aria-label={t('common.delete')} onClick={() => removeLine(field.name)} />
                          )}
                        </div>
                      </div>
                    )
                  })}
                </div>
                <Form.ErrorList errors={errors} />
                {editable && (
                  <Button type="dashed" block icon={<PlusOutlined />}
                    onClick={() => add({ locationId: lines?.[lines.length - 1]?.locationId ?? firstGodown })}>
                    {t('store.addLine')}
                  </Button>
                )}
              </>
            )}
          </Form.List>
        </FadeIn>
      </Form>

      <Modal
        open={confirmCharge}
        title={t('prod.chargeTitle', { mill: chosenMill?.label ?? '' })}
        okText={t('prod.charge')}
        cancelText={t('common.cancel')}
        confirmLoading={save.isPending || charge.isPending}
        onOk={onChargeConfirmed}
        onCancel={() => setConfirmCharge(false)}
      >
        <p>{t('prod.chargeHint')}</p>
        <ul className="charge-list">
          {(lines ?? []).map((l, i) => {
            const m = l?.materialId ? materialById.get(l.materialId) : undefined
            return m ? <li key={i}><span>{m.code} · {m.name}</span><b>{qty(l.quantity, m.decimals)} {m.unit}</b></li> : null
          })}
        </ul>
      </Modal>

      {batch && plantId !== undefined && (
        <DischargeModal batch={discharging ? toSummary(batch) : null} plantId={plantId} onClose={() => setDischarging(false)} />
      )}
      <ReasonModal
        open={cancelling}
        title={t('store.cancelTitle', { no: batch?.batchNo })}
        hint={t('prod.cancelBatchHint')}
        okText={t('prod.cancelBatch')}
        loading={cancel.isPending}
        onOk={onCancel}
        onClose={() => setCancelling(false)}
      />
    </>
  )
}
