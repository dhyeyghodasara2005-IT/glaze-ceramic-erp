import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Alert, App, Button, DatePicker, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Spin } from 'antd'
import { ArrowLeftOutlined, ArrowRightOutlined, CheckCircleOutlined, DeleteOutlined, MinusCircleOutlined, PlusOutlined, SaveOutlined, StopOutlined } from '@ant-design/icons'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import dayjs, { type Dayjs } from 'dayjs'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import { BREAKAGE_REASONS, type BreakageReason, type FgDocType, type FgDocument, type FgStockRow } from '../../api/types'
import NoPlant from '../store/NoPlant'
import DocStatusTag from '../store/DocStatusTag'
import { dateTime, qty, shortDate } from '../store/format'
import ReasonModal from '../production/ReasonModal'
import {
  useCancelFgDocument, useDeleteFgDocument, useFgDesigns, useFgDocument, useFgGrades, useFgPillars, useFgStock,
  usePostFgDocument, useSaveFgDocument,
} from './fgApi'
import { FG_DOC_TYPES } from './fgDocTypes'

interface RowValues {
  fromStockId?: number
  pillarId?: number
  designId?: number
  batchCode?: string
  gradeId?: number
  shade?: string
  caliber?: string
  boxes?: number
  reason?: BreakageReason
  remarks?: string
}
interface FormValues {
  docDate: Dayjs
  remarks?: string
  lines: RowValues[]
}

/** Shade / caliber / batch as printed on the stickers - the server checks the same rule. */
const SHORT = /^[A-Z0-9][A-Z0-9./-]{0,9}$/
const BATCH = /^[A-Z0-9][A-Z0-9./-]{0,39}$/
const tidy = (v?: string) => v?.toUpperCase().replace(/\s+/g, '')

function Cell({ label, className, children }: { label: string; className?: string; children: ReactNode }) {
  return (
    <div className={className}>
      <span className="line-label">{label}</span>
      {children}
    </div>
  )
}

/** /fg/opening/new, /fg/breakage/12, /fg/regrading/3 ... - one editor, the type decides the row's fields. */
export default function FgDocumentPage({ type }: { type: FgDocType }) {
  const { id } = useParams()
  return <DocEditor key={id} type={type} docId={id && id !== 'new' ? Number(id) : undefined} />
}

function DocEditor({ type, docId }: { type: FgDocType; docId?: number }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const { can, currentPlant } = useAuth()
  const base = FG_DOC_TYPES[type].path
  const isNew = docId === undefined
  const goBack = () => (location.key !== 'default' ? navigate(-1) : navigate(base))

  const docQuery = useFgDocument(docId)
  const doc = docQuery.data
  const plantId = doc?.plantId ?? currentPlant?.id
  const editable = can('FG_ENTRY') && (isNew || doc?.status === 'DRAFT')
  const takesFrom = type !== 'OPENING'          // breakage and regrade take boxes from a stock row
  const putsTo = type !== 'BREAKAGE'            // opening and regrade put boxes at a pillar

  const stock = useFgStock(editable && takesFrom && plantId ? { plantId } : null)
  const pillars = useFgPillars(editable && putsTo ? plantId : undefined)
  const grades = useFgGrades(editable && putsTo)
  const designs = useFgDesigns(editable && type === 'OPENING')

  const save = useSaveFgDocument()
  const post = usePostFgDocument()
  const cancel = useCancelFgDocument()
  const remove = useDeleteFgDocument()
  const [confirmPost, setConfirmPost] = useState(false)
  const [cancelling, setCancelling] = useState(false)

  const [form] = Form.useForm<FormValues>()
  const rows = Form.useWatch('lines', form) as RowValues[] | undefined

  // Every stock row the document can take from: the plant's stock now, plus (on a saved document) its own rows.
  const stockRows = useMemo(() => {
    const map = new Map<number, FgStockRow>()
    for (const r of stock.data ?? []) map.set(r.id, r)
    for (const l of doc?.lines ?? []) if (l.from && !map.has(l.from.id)) map.set(l.from.id, l.from)
    return map
  }, [stock.data, doc])
  const stockOptions = useMemo(() => [...stockRows.values()].map((r) => ({
    value: r.id,
    label: `${r.designName} · ${r.gradeName} ${r.shade}/${r.caliber} · ${r.pillarCode} (${r.boxes.toLocaleString('en-IN')})`,
  })), [stockRows])

  useEffect(() => {
    if (doc) {
      form.setFieldsValue({
        docDate: dayjs(doc.docDate),
        remarks: doc.remarks,
        lines: doc.lines.map((l) => ({
          fromStockId: l.fromStockId, pillarId: l.pillarId, designId: l.designId, batchCode: l.batchCode, gradeId: l.gradeId,
          shade: l.shade, caliber: l.caliber, boxes: l.boxes, reason: l.reason, remarks: l.remarks,
        })),
      })
    }
  }, [doc, form])

  // New document: today, one empty row - or, from a batch's "Breakage / Regrade" button (?stock=), that batch.
  useEffect(() => {
    if (!isNew || form.getFieldValue('lines')?.length) return
    form.setFieldValue('docDate', dayjs())
    const fromUrl = Number(params.get('stock'))
    const source = fromUrl ? stockRows.get(fromUrl) : undefined
    if (fromUrl && takesFrom && !source) return       // wait for the stock list
    form.setFieldValue('lines', [source ? fromSource(source) : {}])
  }, [isNew, params, stockRows, takesFrom, form])

  /** A regrade row starts as a copy of where the boxes are, so only what changes needs choosing. */
  function fromSource(s: FgStockRow): RowValues {
    return type === 'REGRADE'
      ? { fromStockId: s.id, gradeId: s.gradeId, shade: s.shade, caliber: s.caliber, pillarId: s.pillarId }
      : { fromStockId: s.id }
  }

  const pillarOptions = (pillars.data ?? []).map((p) => ({ value: p.id, label: `${p.godownCode} · ${p.code}` }))
  for (const l of doc?.lines ?? []) {
    if (l.pillarId && !pillarOptions.some((o) => o.value === l.pillarId)) pillarOptions.push({ value: l.pillarId, label: `${l.godownCode} · ${l.pillarCode}` })
  }
  const gradeOptions = (grades.data ?? []).map((g) => ({ value: g.id, label: g.name }))
  for (const l of doc?.lines ?? []) {
    if (l.gradeId && !gradeOptions.some((o) => o.value === l.gradeId)) gradeOptions.push({ value: l.gradeId, label: l.gradeName ?? '' })
  }
  const designOptions = (designs.data ?? []).map((d) => ({ value: d.id, label: `${d.name} · ${d.code}` }))
  for (const l of doc?.lines ?? []) {
    if (l.designId && !designOptions.some((o) => o.value === l.designId)) designOptions.push({ value: l.designId, label: l.designName ?? '' })
  }
  const totalBoxes = (rows ?? []).reduce((s, r) => s + (r?.boxes ?? 0), 0)

  async function saveDraft(): Promise<FgDocument | undefined> {
    let v: FormValues
    try {
      v = await form.validateFields()
    } catch {
      return undefined
    }
    try {
      return await save.mutateAsync({
        id: docId,
        body: {
          plantId: plantId!,
          docType: type,
          docDate: v.docDate.format('YYYY-MM-DD'),
          remarks: v.remarks?.trim() || undefined,
          lines: v.lines.map((r) => ({ ...r, boxes: r.boxes!, remarks: r.remarks?.trim() || undefined })),
        },
      })
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

  const onPostConfirmed = async () => {
    const saved = await saveDraft()
    if (!saved) {
      setConfirmPost(false)
      return
    }
    try {
      const done = await post.mutateAsync(saved.id)
      message.success(t('store.postedOk', { no: done.docNo }))
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
    setConfirmPost(false)
    if (isNew) navigate(`${base}/${saved.id}`, { replace: true })
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

  const onCancel = async (reason: string) => {
    try {
      await cancel.mutateAsync({ id: docId!, reason })
      message.success(t('store.cancelOk', { no: doc?.docNo }))
      setCancelling(false)
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  if (isNew && !currentPlant) return <NoPlant />
  if (docQuery.isLoading) return <div className="center-block"><Spin size="large" /></div>
  if (!isNew && !doc) return <div className="panel"><EmptyState title={t('store.notFound')} action={<Link to={base}>{t('common.back')}</Link>} /></div>

  const required = { required: true, message: t('common.required') }
  const shortRules = [required, { pattern: SHORT, message: t('finishing.codeInvalid') }]
  const noPillars = editable && putsTo && pillars.data !== undefined && pillars.data.length === 0

  return (
    <>
      <PageHeader
        title={isNew ? t(`fg.doc.new.${type}`) : (doc!.docNo ?? t(`fg.doc.draft.${type}`))}
        subtitle={doc && (
          <Space size={10} wrap>
            <DocStatusTag status={doc.status} />
            <span>{shortDate(doc.docDate)} · {t('finishing.boxesN', { n: doc.boxes.toLocaleString('en-IN') })}</span>
          </Space>
        )}
        icon={FG_DOC_TYPES[type].icon}
        extra={(
          <Space wrap>
            <Button icon={<ArrowLeftOutlined />} onClick={goBack}>{t('common.back')}</Button>
            {editable && !isNew && (
              <Popconfirm title={t('store.deleteTitle')} okText={t('common.delete')} cancelText={t('common.cancel')}
                okButtonProps={{ danger: true }} onConfirm={onDelete}>
                <Button danger icon={<DeleteOutlined />} loading={remove.isPending}>{t('store.deleteDraft')}</Button>
              </Popconfirm>
            )}
            {editable && <Button icon={<SaveOutlined />} loading={save.isPending && !confirmPost} onClick={onSave}>{t('store.saveDraft')}</Button>}
            {editable && (
              <Button type="primary" icon={<CheckCircleOutlined />} disabled={save.isPending || post.isPending}
                onClick={async () => {
                  try {
                    await form.validateFields()
                    setConfirmPost(true)
                  } catch {
                    /* shown under the fields */
                  }
                }}>
                {t('store.post')}
              </Button>
            )}
            {doc?.status === 'POSTED' && can('FG_CANCEL') && (
              <Button danger icon={<StopOutlined />} onClick={() => setCancelling(true)}>{t('fg.doc.cancel')}</Button>
            )}
          </Space>
        )}
      />

      <FadeIn delay={0.04}>
        {editable && <Alert className="doc-alert" type="info" showIcon title={t(`fg.doc.hint.${type}`)} />}
        {noPillars && <Alert className="doc-alert" type="warning" showIcon title={t('fg.noPillarsForPack')} />}
        {doc?.status === 'POSTED' && (
          <Alert className="doc-alert" type="success" showIcon title={t('store.postedInfo', { by: doc.postedBy, at: dateTime(doc.postedAt) })} />
        )}
        {doc?.status === 'CANCELLED' && (
          <Alert className="doc-alert" type="error" showIcon
            title={t('store.cancelledInfo', { by: doc.cancelledBy, at: dateTime(doc.cancelledAt), reason: doc.cancelReason })} />
        )}
      </FadeIn>

      <Form<FormValues> form={form} layout="vertical" disabled={!editable} className={editable ? undefined : 'doc-readonly'}>
        <FadeIn delay={0.08} className="panel doc-panel">
          <div className="fg-doc-head">
            <Form.Item name="docDate" label={t('fg.doc.date')} rules={[required]}>
              <DatePicker format="DD-MM-YYYY" style={{ width: 180 }} disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
            </Form.Item>
            <Form.Item name="remarks" label={t('store.remarks')} style={{ flex: 1 }}>
              <Input maxLength={500} />
            </Form.Item>
          </div>
        </FadeIn>

        <FadeIn delay={0.1} className="panel doc-panel">
          <div className="form-section">{t('finishing.boxesSection')}</div>
          <Form.List name="lines" rules={[{ validator: async (_, v?: RowValues[]) => { if (!v?.length) throw new Error(t('fg.doc.noRows')) } }]}>
            {(fields, { add, remove: removeRow }, { errors }) => (
              <>
                <div className={`doc-lines cols-fg-${type.toLowerCase()}`}>
                  <div className="doc-lines-head" aria-hidden>
                    <span>#</span>
                    {type === 'OPENING' && <>
                      <span>{t('fg.pillar')}</span><span>{t('fg.design')}</span><span>{t('fg.batch')}</span>
                      <span>{t('finishing.grade')}</span><span>{t('finishing.shade')}</span><span>{t('finishing.caliber')}</span>
                      <span>{t('finishing.boxes')}</span>
                    </>}
                    {type === 'BREAKAGE' && <>
                      <span>{t('fg.doc.from')}</span><span>{t('finishing.boxes')}</span><span>{t('fg.doc.reason')}</span><span>{t('store.remarks')}</span>
                    </>}
                    {type === 'REGRADE' && <>
                      <span>{t('fg.doc.from')}</span><span>{t('finishing.boxes')}</span><span />
                      <span>{t('finishing.grade')}</span><span>{t('finishing.shade')}</span><span>{t('finishing.caliber')}</span><span>{t('fg.pillar')}</span>
                    </>}
                    <span />
                  </div>

                  {fields.map((field, index) => {
                    const row = rows?.[index]
                    const source = row?.fromStockId ? stockRows.get(row.fromStockId) : undefined
                    const fromCell = (
                      <Cell label={t('fg.doc.from')} className="span-2">
                        <Form.Item name={[field.name, 'fromStockId']} rules={[required]}>
                          <Select options={stockOptions} loading={stock.isLoading} showSearch={{ optionFilterProp: 'label' }}
                            popupMatchSelectWidth={false} aria-label={t('fg.doc.from')}
                            onChange={(id: number) => {
                              // regrade: start the "to" side as a copy of where the boxes are now
                              const s = stockRows.get(id)
                              if (s && type === 'REGRADE') form.setFieldValue(['lines', field.name], { ...form.getFieldValue(['lines', field.name]), ...fromSource(s) })
                            }} />
                        </Form.Item>
                        {source && editable && <div className="line-hint">{t('fg.doc.thereNow', { n: source.boxes.toLocaleString('en-IN'), batch: source.batchCode })}</div>}
                      </Cell>
                    )
                    const boxesCell = (
                      <Cell label={t('finishing.boxes')}>
                        <Form.Item name={[field.name, 'boxes']} rules={[required, {
                          validator: async (_, v?: number | null) => {
                            if (editable && source && v && v > source.boxes) throw new Error(t('fg.doc.tooMany', { n: source.boxes }))
                          },
                        }]}>
                          <InputNumber<number> min={1} precision={0} style={{ width: '100%' }} inputMode="numeric" aria-label={t('finishing.boxes')} />
                        </Form.Item>
                      </Cell>
                    )
                    const target = (
                      <>
                        <Cell label={t('finishing.grade')}>
                          <Form.Item name={[field.name, 'gradeId']} rules={[required]}>
                            <Select options={gradeOptions} loading={grades.isLoading} aria-label={t('finishing.grade')} />
                          </Form.Item>
                        </Cell>
                        <Cell label={t('finishing.shade')}>
                          <Form.Item name={[field.name, 'shade']} rules={shortRules} normalize={tidy}>
                            <Input maxLength={10} aria-label={t('finishing.shade')} />
                          </Form.Item>
                        </Cell>
                        <Cell label={t('finishing.caliber')}>
                          <Form.Item name={[field.name, 'caliber']} rules={shortRules} normalize={tidy}>
                            <Input maxLength={10} aria-label={t('finishing.caliber')} />
                          </Form.Item>
                        </Cell>
                      </>
                    )
                    const pillarCell = (
                      <Cell label={t('fg.pillar')}>
                        <Form.Item name={[field.name, 'pillarId']} rules={[required]}>
                          <Select options={pillarOptions} loading={pillars.isLoading} showSearch={{ optionFilterProp: 'label' }}
                            aria-label={t('fg.pillar')} />
                        </Form.Item>
                      </Cell>
                    )
                    return (
                      <div key={field.key} className="doc-line">
                        <span className="line-no">{index + 1}</span>
                        {type === 'OPENING' && (
                          <>
                            {pillarCell}
                            <Cell label={t('fg.design')} className="span-2">
                              <Form.Item name={[field.name, 'designId']} rules={[required]}>
                                <Select options={designOptions} loading={designs.isLoading} showSearch={{ optionFilterProp: 'label' }}
                                  popupMatchSelectWidth={false} aria-label={t('fg.design')} />
                              </Form.Item>
                            </Cell>
                            <Cell label={t('fg.batch')}>
                              <Form.Item name={[field.name, 'batchCode']} rules={[required, { pattern: BATCH, message: t('finishing.codeInvalid') }]} normalize={tidy}>
                                <Input maxLength={40} aria-label={t('fg.batch')} />
                              </Form.Item>
                            </Cell>
                            {target}
                            {boxesCell}
                          </>
                        )}
                        {type === 'BREAKAGE' && (
                          <>
                            {fromCell}
                            {boxesCell}
                            <Cell label={t('fg.doc.reason')}>
                              <Form.Item name={[field.name, 'reason']} rules={[required]}>
                                <Select options={BREAKAGE_REASONS.map((r) => ({ value: r, label: t(`fg.doc.reasons.${r}`) }))} aria-label={t('fg.doc.reason')} />
                              </Form.Item>
                            </Cell>
                            <Cell label={t('store.remarks')}>
                              <Form.Item name={[field.name, 'remarks']}>
                                <Input maxLength={255} aria-label={t('store.remarks')} />
                              </Form.Item>
                            </Cell>
                          </>
                        )}
                        {type === 'REGRADE' && (
                          <>
                            {fromCell}
                            {boxesCell}
                            <span className="line-arrow" aria-hidden><ArrowRightOutlined /></span>
                            {target}
                            {pillarCell}
                          </>
                        )}
                        <div className="line-remove">
                          {editable && fields.length > 1 && (
                            <Button type="text" danger icon={<MinusCircleOutlined />} aria-label={t('common.delete')} onClick={() => removeRow(field.name)} />
                          )}
                        </div>
                      </div>
                    )
                  })}
                </div>
                <Form.ErrorList errors={errors} />
                {editable && (
                  <Button type="dashed" block icon={<PlusOutlined />}
                    onClick={() => {
                      // opening stock is entered pillar by pillar: a new row starts at the same pillar and design
                      const last = rows?.[rows.length - 1]
                      add(type === 'OPENING' ? { pillarId: last?.pillarId, designId: last?.designId } : {})
                    }}>
                    {t('finishing.addRow')}
                  </Button>
                )}
              </>
            )}
          </Form.List>
          <div className="doc-total">
            <span className="stat-label">{t('finishing.boxes')}</span>
            <span className="doc-total-value">{totalBoxes.toLocaleString('en-IN')}</span>
            {doc && doc.lines.some((l) => l.sqm != null) && (
              <span className="cell-sub">{qty(doc.lines.reduce((s, l) => s + (l.sqm ?? 0), 0), 0)} m²</span>
            )}
          </div>
        </FadeIn>
      </Form>

      <Modal open={confirmPost} title={t('fg.doc.postTitle')} okText={t('store.post')} cancelText={t('common.cancel')}
        confirmLoading={save.isPending || post.isPending} onOk={onPostConfirmed} onCancel={() => setConfirmPost(false)}>
        {t(`fg.doc.postHint.${type}`)}
      </Modal>
      <ReasonModal
        open={cancelling}
        title={t('store.cancelTitle', { no: doc?.docNo })}
        hint={t('fg.doc.cancelHint')}
        okText={t('fg.doc.cancel')}
        loading={cancel.isPending}
        onOk={onCancel}
        onClose={() => setCancelling(false)}
      />
    </>
  )
}
