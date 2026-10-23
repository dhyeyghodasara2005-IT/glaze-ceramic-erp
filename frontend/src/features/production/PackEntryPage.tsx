import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Alert, App, AutoComplete, Button, Col, DatePicker, Form, Input, InputNumber, Modal, Popconfirm, Row, Select, Space, Spin } from 'antd'
import { ArrowLeftOutlined, CheckCircleOutlined, DeleteOutlined, GoldOutlined, MinusCircleOutlined, PlusOutlined, SaveOutlined, StopOutlined } from '@ant-design/icons'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import dayjs, { type Dayjs } from 'dayjs'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import type { PackEntry, PackEntryRequest } from '../../api/types'
import NoPlant from '../store/NoPlant'
import DocStatusTag from '../store/DocStatusTag'
import { dateTime, qty, shortDate } from '../store/format'
import { useCurrentShift, useProdMachines, useProdShifts } from './api'
import {
  PACK_PATH, useCancelPackEntry, useDeletePackEntry, useFinishingOverview, useGradeOptions, usePackCodes, usePackEntry,
  usePostPackEntry, useSavePackEntry,
} from './finishApi'
import { useSelectableLots } from './pressApi'
import ReasonModal from './ReasonModal'

interface RowValues {
  gradeId?: number
  shade?: string
  caliber?: string
  boxes?: number
}
type FormValues = Omit<PackEntryRequest, 'plantId' | 'prodDate' | 'lines'> & { prodDate: Dayjs; lines: RowValues[] }

/** Shade / caliber as printed on the stickers - the server checks the same rule. */
const CODE = /^[A-Z0-9][A-Z0-9./-]{0,9}$/
/** " a 3" -> "A3" while typing */
const tidy = (v?: string) => v?.toUpperCase().replace(/\s+/g, '')
const n = (v: number) => v.toLocaleString('en-IN')

/** On a narrow screen each row is a small card: the label shows above every field. */
function Cell({ label, className, children }: { label: string; className?: string; children: ReactNode }) {
  return (
    <div className={className}>
      <span className="line-label">{label}</span>
      {children}
    </div>
  )
}

/** /production/pack-entries/new and /production/pack-entries/12 - the packing supervisor's shift entry. */
export default function PackEntryPage() {
  const { id } = useParams()
  return <PackEditor key={id} entryId={id && id !== 'new' ? Number(id) : undefined} />
}

function PackEditor({ entryId }: { entryId?: number }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const { can, currentPlant } = useAuth()
  const isNew = entryId === undefined
  const goBack = () => (location.key !== 'default' ? navigate(-1) : navigate(PACK_PATH))

  const entryQuery = usePackEntry(entryId)
  const entry = entryQuery.data
  const plantId = entry?.plantId ?? currentPlant?.id
  const editable = can('PACKING_ENTRY') && (isNew || entry?.status === 'DRAFT')

  const current = useCurrentShift()
  const shifts = useProdShifts()
  const machines = useProdMachines(plantId)
  const grades = useGradeOptions()
  const selectable = useSelectableLots(editable ? plantId : undefined)
  const finishing = useFinishingOverview(editable ? plantId : undefined)

  const save = useSavePackEntry()
  const post = usePostPackEntry()
  const cancel = useCancelPackEntry()
  const remove = useDeletePackEntry()
  const [confirmPost, setConfirmPost] = useState(false)
  const [cancelling, setCancelling] = useState(false)

  const [form] = Form.useForm<FormValues>()
  const lotId = Form.useWatch('lotId', form) as number | undefined
  const rows = Form.useWatch('lines', form) as RowValues[] | undefined
  const broken = Form.useWatch('broken', form) as number | undefined
  const codes = usePackCodes(editable ? lotId : undefined)

  const sorters = useMemo(() => (machines.data ?? []).filter((m) => m.type === 'SORTING_LINE'), [machines.data])
  // Lots with polished tiles waiting first, then the ones running at the press.
  const allLots = useMemo(() => {
    const list = [...(finishing.data?.lots.map((l) => l.lot) ?? []), ...(selectable.data ?? [])]
    return list.filter((l, i) => list.findIndex((o) => o.id === l.id) === i)
  }, [finishing.data, selectable.data])
  const lot = allLots.find((l) => l.id === lotId)
  const waiting = finishing.data?.lots.find((l) => l.lot.id === lotId)?.flow.waitingSort
  const topGrade = grades.data?.[0]

  useEffect(() => {
    if (entry) {
      form.setFieldsValue({
        ...entry,
        prodDate: dayjs(entry.prodDate),
        lines: entry.lines.map((l) => ({ gradeId: l.gradeId, shade: l.shade, caliber: l.caliber, boxes: l.boxes })),
      })
    }
  }, [entry, form])

  // New entry: the lot from ?lot=, today's shift, the only sorting line, and a first row in the best grade.
  useEffect(() => {
    if (!isNew) return
    const fromUrl = Number(params.get('lot'))
    if (fromUrl && !form.getFieldValue('lotId')) form.setFieldValue('lotId', fromUrl)
    if (current.data) {
      if (!form.isFieldTouched('prodDate')) form.setFieldValue('prodDate', dayjs(current.data.prodDate))
      if (!form.isFieldTouched('shiftId') && current.data.shiftId) form.setFieldValue('shiftId', current.data.shiftId)
    }
    if (sorters.length === 1 && !form.getFieldValue('machineId')) form.setFieldValue('machineId', sorters[0].id)
    if (topGrade && !form.getFieldValue('lines')?.length) form.setFieldValue('lines', [{ gradeId: topGrade.id }])
  }, [isNew, params, current.data, sorters, topGrade, form])

  const lotOptions = useMemo(() => {
    const opts = allLots.map((l) => ({
      value: l.id,
      label: `${l.lotNo} · ${l.designName} · ${l.pressCode}${l.status === 'CLOSED' ? ` (${t('press.lotStatus.CLOSED')})` : ''}`,
    }))
    if (entry && !opts.some((o) => o.value === entry.lotId)) opts.push({ value: entry.lotId, label: `${entry.lotNo} · ${entry.designName} · ${entry.pressCode}` })
    return opts
  }, [allLots, entry, t])
  const machineOptions = sorters.map((m) => ({ value: m.id, label: `${m.code} · ${m.name}` }))
  if (entry && !machineOptions.some((o) => o.value === entry.machineId)) machineOptions.push({ value: entry.machineId, label: `${entry.machineCode} · ${entry.machineName}` })
  const gradeOptions = (grades.data ?? []).map((g) => ({ value: g.id, label: g.name }))
  for (const l of entry?.lines ?? []) {
    // a grade made inactive since this draft was saved still shows by name
    if (!gradeOptions.some((o) => o.value === l.gradeId)) gradeOptions.push({ value: l.gradeId, label: l.gradeName })
  }

  // Live figures: the server works out the same, this is only so the supervisor sees them while typing.
  const perBox = lot?.pcsPerBox ?? entry?.pcsPerBox
  const sqmPerTile = lot?.sqmPerTile ?? entry?.sqmPerTile
  const piecesOf = (r?: RowValues) => (r?.boxes && perBox ? r.boxes * perBox : 0)
  const packed = (rows ?? []).reduce((sum, r) => sum + piecesOf(r), 0)
  const boxes = (rows ?? []).reduce((sum, r) => sum + (r?.boxes ?? 0), 0)
  const sorted = packed + (broken ?? 0)
  const topPieces = (rows ?? []).filter((r) => r?.gradeId === topGrade?.id).reduce((sum, r) => sum + piecesOf(r), 0)
  const area = (pieces: number) => (sqmPerTile && pieces ? `${qty(pieces * sqmPerTile, 0)} m²` : '–')
  const pct = (part: number) => (sorted > 0 ? `${((part * 100) / sorted).toFixed(1)} %` : '–')

  const codeOptions = (list?: string[]) => (list ?? []).map((c) => ({ value: c }))

  async function saveDraft(): Promise<PackEntry | undefined> {
    let v: FormValues
    try {
      v = await form.validateFields()
    } catch {
      return undefined
    }
    // the same grade + shade + caliber twice: show it on the second row, like the server would refuse it
    const seen = new Set<string>()
    for (const [i, r] of v.lines.entries()) {
      const key = `${r.gradeId}|${r.shade}|${r.caliber}`
      if (seen.has(key)) {
        form.setFields([{ name: ['lines', i, 'caliber'], errors: [t('finishing.duplicateRow')] }])
        return undefined
      }
      seen.add(key)
    }
    try {
      return await save.mutateAsync({
        id: entryId,
        body: {
          plantId: plantId!,
          lotId: v.lotId,
          prodDate: v.prodDate.format('YYYY-MM-DD'),
          shiftId: v.shiftId,
          machineId: v.machineId,
          broken: v.broken ?? 0,
          lines: v.lines.map((r) => ({ gradeId: r.gradeId!, shade: r.shade!, caliber: r.caliber!, boxes: r.boxes! })),
          remarks: v.remarks?.trim() || undefined,
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
    if (isNew) navigate(`${PACK_PATH}/${saved.id}`, { replace: true })
  }

  const onPostConfirmed = async () => {
    const saved = await saveDraft()
    if (!saved) {
      setConfirmPost(false)
      return
    }
    try {
      const done = await post.mutateAsync(saved.id)
      message.success(t('store.postedOk', { no: done.entryNo }))
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
    setConfirmPost(false)
    if (isNew) navigate(`${PACK_PATH}/${saved.id}`, { replace: true })
  }

  const onDelete = async () => {
    try {
      await remove.mutateAsync(entryId!)
      message.success(t('store.deleted'))
      navigate(PACK_PATH)
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const onCancel = async (reason: string) => {
    try {
      await cancel.mutateAsync({ id: entryId!, reason })
      message.success(t('store.cancelOk', { no: entry?.entryNo }))
      setCancelling(false)
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  if (isNew && !currentPlant) return <NoPlant />
  if (entryQuery.isLoading) return <div className="center-block"><Spin size="large" /></div>
  if (!isNew && !entry) return <div className="panel"><EmptyState title={t('store.notFound')} action={<Link to={PACK_PATH}>{t('common.back')}</Link>} /></div>

  const required = { required: true, message: t('common.required') }
  const codeRules = [required, { pattern: CODE, message: t('finishing.codeInvalid') }]
  const noMachine = editable && machines.data !== undefined && sorters.length === 0

  return (
    <>
      <PageHeader
        title={isNew ? t('finishing.newPack') : (entry!.entryNo ?? t('finishing.draftPack'))}
        subtitle={entry && (
          <Space size={10} wrap>
            <DocStatusTag status={entry.status} />
            <span><Link to={`/production/lots/${entry.lotId}`} className="cell-title">{entry.designName}</Link> · {entry.lotNo}</span>
            <span>{shortDate(entry.prodDate)} · {t('prod.shift')} {entry.shiftCode}</span>
          </Space>
        )}
        icon={<GoldOutlined />}
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
            {entry?.status === 'POSTED' && can('PRODUCTION_CANCEL') && (
              <Button danger icon={<StopOutlined />} onClick={() => setCancelling(true)}>{t('finishing.cancelEntry')}</Button>
            )}
          </Space>
        )}
      />

      <FadeIn delay={0.04}>
        {noMachine && <Alert className="doc-alert" type="warning" showIcon title={t('prod.noMachines', { type: t('finishing.sortingLine') })} />}
        {entry?.status === 'POSTED' && (
          <Alert className="doc-alert" type="success" showIcon title={t('store.postedInfo', { by: entry.postedBy, at: dateTime(entry.postedAt) })} />
        )}
        {entry?.status === 'CANCELLED' && (
          <Alert className="doc-alert" type="error" showIcon
            title={t('store.cancelledInfo', { by: entry.cancelledBy, at: dateTime(entry.cancelledAt), reason: entry.cancelReason })} />
        )}
      </FadeIn>

      <Form<FormValues> form={form} layout="vertical" disabled={!editable} className={editable ? undefined : 'doc-readonly'}>
        <FadeIn delay={0.08} className="panel doc-panel">
          <div className="form-section">{t('store.details')}</div>
          <Row gutter={16}>
            <Col xs={24} md={12}>
              <Form.Item name="lotId" label={t('press.lot')} rules={[required]}
                extra={editable && waiting !== undefined ? t('finishing.waitingHint', { n: n(waiting) }) : undefined}>
                <Select options={lotOptions} loading={finishing.isLoading} showSearch={{ optionFilterProp: 'label' }} />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item name="prodDate" label={t('prod.prodDate')} rules={[required]}>
                <DatePicker format="DD-MM-YYYY" style={{ width: '100%' }} disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item name="shiftId" label={t('prod.shift')} rules={[required]}>
                <Select options={(shifts.data ?? []).map((s) => ({ value: s.id, label: `${s.code} · ${s.name}` }))} />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item name="machineId" label={t('finishing.sortingLine')} rules={[required]}>
                <Select options={machineOptions} loading={machines.isLoading} />
              </Form.Item>
            </Col>
          </Row>
        </FadeIn>

        <FadeIn delay={0.1} className="panel doc-panel">
          <div className="form-section">
            {t('finishing.boxesSection')}
            {perBox && <span className="cell-sub"> · {t('finishing.pcsPerBox', { n: perBox })}</span>}
          </div>
          <Form.List
            name="lines"
            rules={[{ validator: async (_, value?: RowValues[]) => {
              if (!value?.length) throw new Error(t('finishing.noRows'))
            } }]}
          >
            {(fields, { add, remove: removeRow }, { errors }) => (
              <>
                <div className="doc-lines cols-pack">
                  <div className="doc-lines-head" aria-hidden>
                    <span>#</span>
                    <span>{t('finishing.grade')}</span>
                    <span>{t('finishing.shade')}</span>
                    <span>{t('finishing.caliber')}</span>
                    <span>{t('finishing.boxes')}</span>
                    <span className="num">{t('finishing.pieces')}</span>
                    <span className="num">{t('finishing.area')}</span>
                    <span />
                  </div>
                  {fields.map((field, index) => {
                    const pieces = piecesOf(rows?.[index])
                    return (
                      <div key={field.key} className="doc-line">
                        <span className="line-no">{index + 1}</span>
                        <Cell label={t('finishing.grade')} className="span-2">
                          <Form.Item name={[field.name, 'gradeId']} rules={[required]}>
                            <Select options={gradeOptions} loading={grades.isLoading} aria-label={t('finishing.grade')} />
                          </Form.Item>
                        </Cell>
                        <Cell label={t('finishing.shade')}>
                          <Form.Item name={[field.name, 'shade']} rules={codeRules} normalize={tidy}>
                            <AutoComplete options={codeOptions(codes.data?.shades)} aria-label={t('finishing.shade')}
                              filterOption={(input, o) => String(o?.value ?? '').startsWith(input.toUpperCase())}>
                              <Input maxLength={10} />
                            </AutoComplete>
                          </Form.Item>
                        </Cell>
                        <Cell label={t('finishing.caliber')}>
                          <Form.Item name={[field.name, 'caliber']} rules={codeRules} normalize={tidy}>
                            <AutoComplete options={codeOptions(codes.data?.calibers)} aria-label={t('finishing.caliber')}
                              filterOption={(input, o) => String(o?.value ?? '').startsWith(input.toUpperCase())}>
                              <Input maxLength={10} />
                            </AutoComplete>
                          </Form.Item>
                        </Cell>
                        <Cell label={t('finishing.boxes')}>
                          <Form.Item name={[field.name, 'boxes']} rules={[required]}>
                            <InputNumber<number> min={1} max={100000} precision={0} style={{ width: '100%' }} inputMode="numeric"
                              aria-label={t('finishing.boxes')} />
                          </Form.Item>
                        </Cell>
                        <Cell label={t('finishing.pieces')}>
                          <div className="line-static">{pieces ? n(pieces) : '–'}</div>
                        </Cell>
                        <Cell label={t('finishing.area')}>
                          <div className="line-static">{area(pieces)}</div>
                        </Cell>
                        <div className="line-remove">
                          {editable && fields.length > 1 && (
                            <Button type="text" danger icon={<MinusCircleOutlined />} aria-label={t('common.delete')}
                              onClick={() => removeRow(field.name)} />
                          )}
                        </div>
                      </div>
                    )
                  })}
                </div>
                <Form.ErrorList errors={errors} />
                {editable && (
                  <Button type="dashed" block icon={<PlusOutlined />}
                    // a new row starts with the last row's shade and caliber: boxes of one shade come in several grades
                    onClick={() => add({ shade: rows?.[rows.length - 1]?.shade, caliber: rows?.[rows.length - 1]?.caliber })}>
                    {t('finishing.addRow')}
                  </Button>
                )}
              </>
            )}
          </Form.List>
        </FadeIn>

        <FadeIn delay={0.12} className="panel doc-panel">
          <div className="form-section">{t('finishing.sorted')}</div>
          <Row gutter={16}>
            <Col xs={12} md={6}>
              <Form.Item name="broken" label={t('finishing.broken')} tooltip={t('finishing.brokenHint')}>
                <InputNumber<number> min={0} precision={0} style={{ width: '100%' }} inputMode="numeric" />
              </Form.Item>
            </Col>
            <Col xs={24} md={18}>
              <div className="entry-result">
                <div><span className="stat-label">{t('finishing.sorted')}</span><b>{sorted ? n(sorted) : '–'}</b></div>
                <div><span className="stat-label">{t('finishing.boxes')}</span><b>{boxes ? n(boxes) : '–'}</b></div>
                <div><span className="stat-label">{t('finishing.packed')}</span><b>{area(packed)}</b></div>
                {topGrade && <div><span className="stat-label">{t('finishing.topShare', { grade: topGrade.name })}</span><b>{pct(topPieces)}</b></div>}
                <div><span className="stat-label">{t('finishing.broken')}</span><b>{pct(broken ?? 0)}</b></div>
              </div>
            </Col>
            <Col xs={24}>
              <Form.Item name="remarks" label={t('store.remarks')}>
                <Input.TextArea maxLength={500} autoSize={{ minRows: 1, maxRows: 4 }} />
              </Form.Item>
            </Col>
          </Row>
        </FadeIn>
      </Form>

      <Modal open={confirmPost} title={t('finishing.postTitle')} okText={t('store.post')} cancelText={t('common.cancel')}
        confirmLoading={save.isPending || post.isPending} onOk={onPostConfirmed} onCancel={() => setConfirmPost(false)}>
        {t('finishing.postHint')}
      </Modal>
      <ReasonModal
        open={cancelling}
        title={t('store.cancelTitle', { no: entry?.entryNo })}
        hint={t('finishing.cancelHint')}
        okText={t('finishing.cancelEntry')}
        loading={cancel.isPending}
        onOk={onCancel}
        onClose={() => setCancelling(false)}
      />
    </>
  )
}
