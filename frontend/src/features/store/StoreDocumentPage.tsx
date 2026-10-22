import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Alert, App, Button, Col, DatePicker, Form, Input, InputNumber, Modal, Popconfirm, Row, Select, Space, Spin } from 'antd'
import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  MinusCircleOutlined,
  PlusOutlined,
  SaveOutlined,
  StopOutlined,
} from '@ant-design/icons'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import dayjs, { type Dayjs } from 'dayjs'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import { toUpper } from '../../api/validation'
import {
  ADJUSTMENT_REASONS,
  type AdjustmentReason,
  type StoreDocType,
  type StoreDocument,
  type StoreDocumentRequest,
} from '../../api/types'
import {
  useCancelDocument,
  useDeleteDocument,
  useGodowns,
  usePostDocument,
  useSaveDocument,
  useStock,
  useStoreDocument,
  useStoreMachines,
  useStoreMaterials,
  useStoreSuppliers,
} from './api'
import { DOC_TYPES } from './docTypes'
import DocStatusTag from './DocStatusTag'
import { dateTime, decimalsOf, money, qty, shortDate } from './format'
import NoPlant from './NoPlant'

// ---- form values (dates are dayjs objects while editing; strings in the API) ----

interface LineValues {
  materialId?: number
  locationId?: number
  quantity?: number
  receivedQty?: number
  rejectedQty?: number
  rate?: number
  moisturePct?: number
}

interface FormValues {
  docDate: Dayjs
  supplierId?: number
  supplierInvoiceNo?: string
  supplierInvoiceDate?: Dayjs
  vehicleNo?: string
  weighbridgeSlipNo?: string
  machineId?: number
  issuedTo?: string
  reason?: AdjustmentReason
  remarks?: string
  lines: LineValues[]
}

const DATE = 'DD-MM-YYYY'
const blank = (s?: string) => (s && s.trim() ? s.trim() : undefined)

function toForm(d: StoreDocument): FormValues {
  return {
    docDate: dayjs(d.docDate),
    supplierId: d.supplierId,
    supplierInvoiceNo: d.supplierInvoiceNo,
    supplierInvoiceDate: d.supplierInvoiceDate ? dayjs(d.supplierInvoiceDate) : undefined,
    vehicleNo: d.vehicleNo,
    weighbridgeSlipNo: d.weighbridgeSlipNo,
    machineId: d.machineId,
    issuedTo: d.issuedTo,
    reason: d.reason,
    remarks: d.remarks,
    lines: d.lines.map((l) => ({
      materialId: l.materialId,
      locationId: l.locationId,
      quantity: l.quantity,
      receivedQty: l.receivedQty,
      rejectedQty: l.rejectedQty,
      rate: l.rate,
      moisturePct: l.moisturePct,
    })),
  }
}

function toRequest(v: FormValues, type: StoreDocType, plantId: number): StoreDocumentRequest {
  return {
    plantId,
    docType: type,
    docDate: v.docDate.format('YYYY-MM-DD'),
    supplierId: v.supplierId,
    supplierInvoiceNo: blank(v.supplierInvoiceNo),
    supplierInvoiceDate: v.supplierInvoiceDate?.format('YYYY-MM-DD'),
    vehicleNo: blank(v.vehicleNo),
    weighbridgeSlipNo: blank(v.weighbridgeSlipNo),
    machineId: v.machineId,
    issuedTo: blank(v.issuedTo),
    reason: v.reason,
    remarks: blank(v.remarks),
    lines: (v.lines ?? []).map((l) => ({
      materialId: l.materialId!,
      locationId: l.locationId!,
      quantity: l.quantity,
      receivedQty: l.receivedQty,
      rejectedQty: l.rejectedQty,
      rate: l.rate,
      moisturePct: l.moisturePct,
    })),
  }
}

/** One cell of a line. On phones the label shows above the input; on PCs the header row has it. */
function Cell({ label, className, children }: { label: string; className?: string; children: ReactNode }) {
  return (
    <div className={`line-cell ${className ?? ''}`}>
      <span className="line-label">{label}</span>
      {children}
    </div>
  )
}

/**
 * /store/grn/new, /store/grn/12 (same for issues and adjustments).
 * The key makes every document start with a fresh form (no values left over from the previous one).
 */
export default function StoreDocumentPage({ type }: { type: StoreDocType }) {
  const { id } = useParams()
  return <DocumentEditor key={id} type={type} docId={id && id !== 'new' ? Number(id) : undefined} />
}

function DocumentEditor({ type, docId }: { type: StoreDocType; docId?: number }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const navigate = useNavigate()
  const { can, currentPlant } = useAuth()
  const base = DOC_TYPES[type].path
  const isNew = docId === undefined

  const docQuery = useStoreDocument(docId)
  const doc = docQuery.data
  // An existing document always stays in its own plant, whatever the header says.
  const plantId = doc?.plantId ?? currentPlant?.id
  const editable = can('STORE_ENTRY') && (isNew || doc?.status === 'DRAFT')

  const materials = useStoreMaterials()
  const godowns = useGodowns(plantId)
  const suppliers = useStoreSuppliers(type === 'GRN')
  const machines = useStoreMachines(plantId, type === 'ISSUE')
  const stock = useStock(type === 'GRN' ? undefined : plantId) // for "Available …" under issue quantities

  const save = useSaveDocument()
  const post = usePostDocument()
  const cancel = useCancelDocument()
  const remove = useDeleteDocument()
  const [cancelOpen, setCancelOpen] = useState(false)
  const [cancelForm] = Form.useForm<{ reason: string }>()

  const [form] = Form.useForm<FormValues>()
  const lines = Form.useWatch('lines', form) as LineValues[] | undefined
  const reason = Form.useWatch('reason', form) as AdjustmentReason | undefined

  useEffect(() => {
    if (doc) form.setFieldsValue(toForm(doc))
  }, [doc, form])

  // A new document starts with one line in the plant's first godown.
  const defaultGodown = godowns.data?.find((g) => g.active)?.id
  useEffect(() => {
    if (!isNew || !defaultGodown) return
    if (!form.getFieldValue(['lines', 0, 'locationId'])) form.setFieldValue(['lines', 0, 'locationId'], defaultGodown)
  }, [isNew, defaultGodown, form])

  // ---- dropdown options. Values already on the document are added even if no longer active. ----

  const materialById = useMemo(() => {
    const map = new Map<number, { code: string; name: string; unit: string; decimals: number }>()
    for (const m of materials.data ?? []) map.set(m.id, { code: m.code, name: m.name, unit: m.extra ?? '', decimals: m.decimals })
    for (const l of doc?.lines ?? []) {
      if (!map.has(l.materialId)) map.set(l.materialId, { code: l.materialCode, name: l.materialName, unit: l.unitCode, decimals: l.unitDecimals })
    }
    return map
  }, [materials.data, doc])
  const materialOptions = [...materialById].map(([value, m]) => ({ value, label: `${m.code} · ${m.name}` }))

  const godownOptions = useMemo(() => {
    const opts = (godowns.data ?? []).filter((g) => g.active).map((g) => ({ value: g.id, label: `${g.code} · ${g.name}` }))
    for (const l of doc?.lines ?? []) {
      if (!opts.some((o) => o.value === l.locationId)) opts.push({ value: l.locationId, label: `${l.locationCode} · ${l.locationName}` })
    }
    return opts
  }, [godowns.data, doc])

  const supplierOptions = useMemo(() => {
    const opts = (suppliers.data ?? []).map((s) => ({ value: s.id, label: s.extra ? `${s.name} (${s.extra})` : s.name }))
    if (doc?.supplierId && !opts.some((o) => o.value === doc.supplierId)) opts.push({ value: doc.supplierId, label: doc.supplierName ?? '' })
    return opts
  }, [suppliers.data, doc])

  const machineOptions = useMemo(() => {
    const opts = (machines.data ?? []).map((m) => ({ value: m.id, label: `${m.code} · ${m.name}` }))
    if (doc?.machineId && !opts.some((o) => o.value === doc.machineId)) opts.push({ value: doc.machineId, label: `${doc.machineCode} · ${doc.machineName}` })
    return opts
  }, [machines.data, doc])

  const available = (materialId?: number, locationId?: number) => {
    if (!materialId || !locationId || !stock.data) return undefined
    const row = stock.data.find((r) => r.materialId === materialId)
    return row?.locations.find((l) => l.locationId === locationId)?.quantity ?? 0
  }

  // ---- values shown while typing (the server works them out again when saving) ----

  const posted = doc && doc.status !== 'DRAFT'
  const lineValue = (l: LineValues | undefined, index: number): number | undefined => {
    if (posted) return doc.lines[index]?.value
    if (!l) return undefined
    if (type === 'GRN') return l.rate != null ? ((l.receivedQty ?? 0) - (l.rejectedQty ?? 0)) * l.rate : undefined
    if (type === 'ADJUSTMENT' && (l.quantity ?? 0) > 0 && l.rate != null) return l.quantity! * l.rate
    return undefined // issues and reductions go out at the average rate, known only when posted
  }
  const total = posted
    ? doc.totalValue
    : type === 'ISSUE' ? undefined : (lines ?? []).reduce((sum, l, i) => sum + (lineValue(l, i) ?? 0), 0)

  // ---- actions ----

  const busy = save.isPending || post.isPending

  /** Validates and saves as draft. Returns the saved document, or undefined if something was wrong. */
  async function saveDraft(): Promise<StoreDocument | undefined> {
    let values: FormValues
    try {
      values = await form.validateFields()
    } catch {
      return undefined // antd already shows the messages under the fields
    }
    try {
      return await save.mutateAsync({ id: docId, body: toRequest(values, type, plantId!) })
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
      return undefined
    }
  }

  const onSave = async () => {
    const saved = await saveDraft()
    if (!saved) return
    message.success(t('common.saved'))
    if (isNew) navigate(`${base}/${saved.id}`, { replace: true })
  }

  const [confirmPost, setConfirmPost] = useState(false)
  const onPostClick = async () => {
    try {
      await form.validateFields() // don't ask "post?" for a form that still has mistakes
      setConfirmPost(true)
    } catch {
      /* messages are shown under the fields */
    }
  }
  const onPostConfirmed = async () => {
    const saved = await saveDraft() // posting always saves the latest edits first
    if (!saved) return
    try {
      const done = await post.mutateAsync(saved.id)
      message.success(t('store.postedOk', { no: done.docNo }))
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
    setConfirmPost(false)
    if (isNew) navigate(`${base}/${saved.id}`, { replace: true }) // the draft exists now either way
  }

  const onDelete = async () => {
    try {
      await remove.mutateAsync(docId!)
      message.success(t('store.deleted'))
      navigate(base)
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const onCancelDoc = async ({ reason }: { reason: string }) => {
    try {
      await cancel.mutateAsync({ id: docId!, reason })
      message.success(t('store.cancelOk', { no: doc?.docNo }))
      setCancelOpen(false)
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  // Ctrl+S / Cmd+S saves the draft, like a desktop program.
  useEffect(() => {
    if (!editable) return
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
        e.preventDefault()
        void onSave()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })

  // ---- render ----

  if (isNew && !currentPlant) return <NoPlant />
  if (docQuery.isLoading) return <div className="center-block"><Spin size="large" /></div>
  if (!isNew && !doc) {
    return (
      <div className="panel">
        <EmptyState title={t('store.notFound')} action={<Link to={base}>{t('common.back')}</Link>} />
      </div>
    )
  }

  const required = { required: true, message: t('common.required') }
  const decimalsRule = (decimals: number, unit?: string) => ({
    validator: async (_: unknown, v?: number | null) => {
      if (v != null && decimalsOf(v) > decimals) throw new Error(t('store.decimals', { unit, n: decimals }))
    },
  })

  const title = isNew ? t(`store.newDoc.${type}`) : (doc!.docNo ?? t(`store.draftDoc.${type}`))
  const subtitle = doc && (
    <Space size={10} wrap>
      <DocStatusTag status={doc.status} />
      <span>{shortDate(doc.docDate)}</span>
      <span>{t('store.createdBy', { by: doc.createdBy })}</span>
    </Space>
  )

  const actions = (
    <Space wrap>
      <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(base)}>{t('common.back')}</Button>
      {editable && !isNew && (
        <Popconfirm title={t('store.deleteTitle')} okText={t('common.delete')} cancelText={t('common.cancel')}
          okButtonProps={{ danger: true }} onConfirm={onDelete}>
          <Button danger icon={<DeleteOutlined />} loading={remove.isPending}>{t('store.deleteDraft')}</Button>
        </Popconfirm>
      )}
      {editable && <Button icon={<SaveOutlined />} loading={save.isPending && !confirmPost} onClick={onSave}>{t('store.saveDraft')}</Button>}
      {editable && <Button type="primary" icon={<CheckCircleOutlined />} disabled={busy} onClick={onPostClick}>{t('store.post')}</Button>}
      {doc?.status === 'POSTED' && can('STORE_CANCEL') && !doc.origin && (
        <Button danger icon={<StopOutlined />} onClick={() => setCancelOpen(true)}>{t('store.cancelDoc')}</Button>
      )}
    </Space>
  )

  const noGodowns = editable && godowns.data !== undefined && !godowns.data.some((g) => g.active)
  const qtyLabel = type === 'ADJUSTMENT' ? t('store.adjustQty') : t('store.quantity')

  return (
    <>
      <PageHeader title={title} subtitle={subtitle} icon={DOC_TYPES[type].icon} extra={actions} />

      <FadeIn delay={0.04}>
        {doc?.origin && (
          <Alert className="doc-alert" type="info" showIcon title={t('store.fromProductionInfo', { by: doc.issuedTo })} />
        )}
        {doc?.status === 'POSTED' && (
          <Alert className="doc-alert" type="success" showIcon
            title={t('store.postedInfo', { by: doc.postedBy, at: dateTime(doc.postedAt) })} />
        )}
        {doc?.status === 'CANCELLED' && (
          <Alert className="doc-alert" type="error" showIcon
            title={t('store.cancelledInfo', { by: doc.cancelledBy, at: dateTime(doc.cancelledAt), reason: doc.cancelReason })} />
        )}
        {editable && !noGodowns && <Alert className="doc-alert" type="info" showIcon title={t('store.draftInfo')} />}
        {noGodowns && (
          <Alert className="doc-alert" type="warning" showIcon title={t('store.noGodowns')}
            action={<Link to="/store/godowns?new=1">{t('store.addGodown')}</Link>} />
        )}
      </FadeIn>

      <Form<FormValues>
        form={form}
        layout="vertical"
        disabled={!editable}
        className={editable ? undefined : 'doc-readonly'}
        initialValues={{ docDate: dayjs(), lines: [{}] }}
      >
        <FadeIn delay={0.08} className="panel doc-panel">
          <div className="form-section">{t('store.details')}</div>
          <Row gutter={16}>
            <Col xs={12} md={6}>
              <Form.Item name="docDate" label={t('store.date')} rules={[required]}>
                <DatePicker format={DATE} style={{ width: '100%' }} disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
              </Form.Item>
            </Col>

            {type === 'GRN' && (
              <>
                <Col xs={24} md={10}>
                  <Form.Item name="supplierId" label={t('store.supplier')} rules={[required]}>
                    <Select showSearch={{ optionFilterProp: 'label' }} options={supplierOptions} loading={suppliers.isLoading} />
                  </Form.Item>
                </Col>
                <Col xs={12} md={4}>
                  <Form.Item name="supplierInvoiceNo" label={t('store.supplierInvoiceNo')} normalize={toUpper}>
                    <Input maxLength={40} />
                  </Form.Item>
                </Col>
                <Col xs={12} md={4}>
                  <Form.Item name="supplierInvoiceDate" label={t('store.supplierInvoiceDate')}>
                    <DatePicker format={DATE} style={{ width: '100%' }} disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
                  </Form.Item>
                </Col>
                <Col xs={12} md={6}>
                  <Form.Item name="vehicleNo" label={t('store.vehicleNo')} normalize={toUpper}>
                    <Input maxLength={20} placeholder="GJ36T1234" />
                  </Form.Item>
                </Col>
                <Col xs={12} md={6}>
                  <Form.Item name="weighbridgeSlipNo" label={t('store.weighbridgeSlipNo')}>
                    <Input maxLength={30} />
                  </Form.Item>
                </Col>
              </>
            )}

            {type === 'ISSUE' && (
              <>
                <Col xs={24} md={9}>
                  <Form.Item name="machineId" label={t('store.machine')} extra={editable && t('store.machineHint')}>
                    <Select allowClear showSearch={{ optionFilterProp: 'label' }} options={machineOptions} loading={machines.isLoading} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={9}>
                  <Form.Item name="issuedTo" label={t('store.issuedTo')}>
                    <Input maxLength={100} placeholder={t('store.issuedToPlaceholder')} />
                  </Form.Item>
                </Col>
              </>
            )}

            {type === 'ADJUSTMENT' && (
              <Col xs={12} md={6}>
                <Form.Item name="reason" label={t('store.reason')} rules={[required]}>
                  <Select options={ADJUSTMENT_REASONS.map((r) => ({ value: r, label: t(`store.reasons.${r}`) }))} />
                </Form.Item>
              </Col>
            )}

            <Col xs={24} md={type === 'GRN' ? 12 : type === 'ISSUE' ? 24 : 12}>
              <Form.Item name="remarks" label={t('store.remarks')}>
                <Input.TextArea maxLength={500} autoSize={{ minRows: 1, maxRows: 4 }} />
              </Form.Item>
            </Col>
          </Row>
        </FadeIn>

        <FadeIn delay={0.12} className="panel doc-panel">
          <div className="form-section">{t('store.materials')}</div>
          <Form.List
            name="lines"
            rules={[{ validator: async (_, value?: LineValues[]) => {
              if (!value?.length) throw new Error(t('store.noLines'))
            } }]}
          >
            {(fields, { add, remove: removeLine }, { errors }) => (
              <>
                <div className={`doc-lines cols-${type.toLowerCase()}`}>
                  <div className="doc-lines-head" aria-hidden>
                    <span>#</span>
                    <span>{t('store.material')}</span>
                    <span>{t('store.godown')}</span>
                    {type === 'GRN' ? (
                      <>
                        <span>{t('store.received')}</span>
                        <span>{t('store.rejected')}</span>
                        <span className="num">{t('store.accepted')}</span>
                      </>
                    ) : (
                      <span>{qtyLabel}</span>
                    )}
                    <span className="num">{t('store.rate')}</span>
                    <span className="num">{t('store.value')}</span>
                    {type === 'GRN' && <span>{t('store.moisture')}</span>}
                    <span />
                  </div>

                  {fields.map((field, index) => {
                    const line = lines?.[index]
                    const m = line?.materialId ? materialById.get(line.materialId) : undefined
                    const unit = m?.unit
                    const decimals = m?.decimals ?? 3
                    const value = lineValue(line, index)
                    const avail = type === 'GRN' || posted ? undefined : available(line?.materialId, line?.locationId)
                    const needed = type === 'ISSUE' ? line?.quantity : line?.quantity != null && line.quantity < 0 ? -line.quantity : undefined
                    const reducing = type === 'ADJUSTMENT' && (line?.quantity ?? 0) < 0

                    return (
                      <div key={field.key} className="doc-line">
                        <span className="line-no">{index + 1}</span>

                        <Cell label={t('store.material')} className="span-2">
                          <Form.Item name={[field.name, 'materialId']} rules={[required]}>
                            <Select showSearch={{ optionFilterProp: 'label' }} options={materialOptions}
                              loading={materials.isLoading} popupMatchSelectWidth={false} aria-label={t('store.material')} />
                          </Form.Item>
                        </Cell>

                        <Cell label={t('store.godown')} className="span-2">
                          <Form.Item name={[field.name, 'locationId']} rules={[required]}>
                            <Select options={godownOptions} loading={godowns.isLoading} popupMatchSelectWidth={false}
                              aria-label={t('store.godown')} />
                          </Form.Item>
                        </Cell>

                        {type === 'GRN' ? (
                          <>
                            <Cell label={t('store.received')}>
                              <Form.Item name={[field.name, 'receivedQty']} rules={[required, decimalsRule(decimals, unit)]}>
                                <InputNumber<number> min={0} suffix={unit} style={{ width: '100%' }} aria-label={t('store.received')} />
                              </Form.Item>
                            </Cell>
                            <Cell label={t('store.rejected')}>
                              <Form.Item
                                name={[field.name, 'rejectedQty']}
                                dependencies={[['lines', field.name, 'receivedQty']]}
                                rules={[decimalsRule(decimals, unit), {
                                  validator: async (_, v?: number | null) => {
                                    if (v != null && v > (form.getFieldValue(['lines', field.name, 'receivedQty']) ?? 0)) {
                                      throw new Error(t('store.moreThanReceived'))
                                    }
                                  },
                                }]}
                              >
                                <InputNumber<number> min={0} suffix={unit} style={{ width: '100%' }} aria-label={t('store.rejected')} />
                              </Form.Item>
                            </Cell>
                            <Cell label={t('store.accepted')}>
                              <div className="line-static">
                                {line?.receivedQty != null ? `${qty((line.receivedQty ?? 0) - (line.rejectedQty ?? 0), decimals)} ${unit ?? ''}` : '–'}
                              </div>
                            </Cell>
                          </>
                        ) : (
                          <Cell label={qtyLabel}>
                            <Form.Item
                              name={[field.name, 'quantity']}
                              rules={[required, decimalsRule(decimals, unit), {
                                validator: async (_, v?: number | null) => {
                                  if (v != null && (type === 'ISSUE' ? v <= 0 : v === 0)) throw new Error(t('common.required'))
                                },
                              }]}
                            >
                              <InputNumber<number> min={type === 'ISSUE' ? 0 : undefined} suffix={unit} style={{ width: '100%' }}
                                aria-label={qtyLabel} />
                            </Form.Item>
                            {avail !== undefined && editable && (
                              <div className={`line-hint${needed != null && needed > avail ? ' short' : ''}`}>
                                {t('store.available', { qty: `${qty(avail, decimals)} ${unit ?? ''}` })}
                              </div>
                            )}
                          </Cell>
                        )}

                        <Cell label={t('store.rate')}>
                          {type === 'ISSUE' || posted || reducing ? (
                            // Issues and reductions go out at the average rate - nothing to type.
                            <div className="line-static">{posted ? money(doc.lines[index]?.rate) : '–'}</div>
                          ) : (
                            <Form.Item
                              name={[field.name, 'rate']}
                              rules={type === 'GRN' || reason === 'OPENING' ? [required] : []}
                            >
                              {/* Adjustment: an empty rate means "use the current average" (the placeholder says so). */}
                              <InputNumber<number> min={0} prefix="₹" style={{ width: '100%' }} aria-label={t('store.rate')}
                                placeholder={type === 'ADJUSTMENT' ? t('store.avgRate') : undefined}
                                title={type === 'ADJUSTMENT' ? t('store.rateOptional') : undefined} />
                            </Form.Item>
                          )}
                        </Cell>

                        <Cell label={t('store.value')}>
                          <div className="line-static">{value != null ? money(value) : '–'}</div>
                        </Cell>

                        {type === 'GRN' && (
                          <Cell label={t('store.moisture')}>
                            <Form.Item name={[field.name, 'moisturePct']}>
                              <InputNumber<number> min={0} max={100} suffix="%" style={{ width: '100%' }} aria-label={t('store.moisture')} />
                            </Form.Item>
                          </Cell>
                        )}

                        <div className="line-remove">
                          {editable && fields.length > 1 && (
                            <Button type="text" danger icon={<MinusCircleOutlined />} aria-label={t('common.delete')}
                              onClick={() => removeLine(field.name)} />
                          )}
                        </div>
                      </div>
                    )
                  })}
                </div>

                <Form.ErrorList errors={errors} />
                {editable && (
                  <Button
                    type="dashed"
                    block
                    icon={<PlusOutlined />}
                    onClick={() => add({ locationId: lines?.[lines.length - 1]?.locationId ?? defaultGodown })}
                  >
                    {t('store.addLine')}
                  </Button>
                )}
              </>
            )}
          </Form.List>

          <div className="doc-total">
            <span className="stat-label">{t('store.total')}</span>
            {total !== undefined ? (
              <span className="doc-total-value">{money(total)}</span>
            ) : (
              <span className="cell-sub">{t('store.valueOnPost')}</span>
            )}
          </div>
        </FadeIn>
      </Form>

      <Modal
        open={confirmPost}
        title={t('store.postTitle', { doc: t(`store.docName.${type}`) })}
        okText={t('store.post')}
        cancelText={t('common.cancel')}
        confirmLoading={busy}
        onOk={onPostConfirmed}
        onCancel={() => setConfirmPost(false)}
      >
        {t('store.postHint')}
      </Modal>

      <Modal
        open={cancelOpen}
        title={t('store.cancelTitle', { no: doc?.docNo })}
        okText={t('store.cancelDoc')}
        okButtonProps={{ danger: true }}
        cancelText={t('store.keep')}
        confirmLoading={cancel.isPending}
        onOk={() => cancelForm.submit()}
        onCancel={() => setCancelOpen(false)}
        destroyOnHidden
      >
        <p className="panel-hint" style={{ margin: '0 0 12px' }}>{t('store.cancelHint')}</p>
        <Form form={cancelForm} layout="vertical" onFinish={onCancelDoc}>
          <Form.Item name="reason" label={t('store.cancelReason')} rules={[{ required: true, whitespace: true, message: t('common.required') }]}>
            <Input maxLength={255} autoFocus />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
