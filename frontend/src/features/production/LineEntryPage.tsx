import { useEffect, useMemo, useState } from 'react'
import { Alert, App, Button, Col, DatePicker, Form, Input, InputNumber, Modal, Popconfirm, Row, Select, Space, Spin } from 'antd'
import { ArrowLeftOutlined, CheckCircleOutlined, DeleteOutlined, FireOutlined, FormatPainterOutlined, SaveOutlined, StopOutlined } from '@ant-design/icons'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import dayjs, { type Dayjs } from 'dayjs'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import type { LineEntry, LineEntryRequest, LineStage } from '../../api/types'
import NoPlant from '../store/NoPlant'
import DocStatusTag from '../store/DocStatusTag'
import { dateTime, qty, shortDate } from '../store/format'
import { useCurrentShift, useProdMachines, useProdShifts } from './api'
import { LINE_PATHS, useCancelLineEntry, useDeleteLineEntry, useLineEntry, useLineOverview, usePostLineEntry, useSaveLineEntry } from './lineApi'
import { useSelectableLots } from './pressApi'
import ReasonModal from './ReasonModal'

type FormValues = Omit<LineEntryRequest, 'plantId' | 'stage' | 'prodDate'> & { prodDate: Dayjs }

/** /production/glaze-entries/new, /production/kiln-entries/12 ... - one editor, the stage decides the fields. */
export default function LineEntryPage({ stage }: { stage: LineStage }) {
  const { id } = useParams()
  return <EntryEditor key={id} stage={stage} entryId={id && id !== 'new' ? Number(id) : undefined} />
}

function EntryEditor({ stage, entryId }: { stage: LineStage; entryId?: number }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const { can, currentPlant } = useAuth()
  const base = LINE_PATHS[stage]
  const isNew = entryId === undefined
  const isGlaze = stage === 'GLAZE'
  const goBack = () => (location.key !== 'default' ? navigate(-1) : navigate(base))

  const entryQuery = useLineEntry(entryId)
  const entry = entryQuery.data
  const plantId = entry?.plantId ?? currentPlant?.id
  const canEntry = can(isGlaze ? 'GLAZE_ENTRY' : 'KILN_ENTRY')
  const editable = canEntry && (isNew || entry?.status === 'DRAFT')

  const current = useCurrentShift()
  const shifts = useProdShifts()
  const machines = useProdMachines(plantId)
  const lots = useSelectableLots(editable ? plantId : undefined)
  const overview = useLineOverview(editable ? plantId : undefined)

  const save = useSaveLineEntry()
  const post = usePostLineEntry()
  const cancel = useCancelLineEntry()
  const remove = useDeleteLineEntry()
  const [confirmPost, setConfirmPost] = useState(false)
  const [cancelling, setCancelling] = useState(false)

  const [form] = Form.useForm<FormValues>()
  const lotId = Form.useWatch('lotId', form) as number | undefined
  const tilesIn = Form.useWatch('tilesIn', form) as number | undefined
  const rejects = Form.useWatch('rejects', form) as number | undefined

  // useMemo: the same arrays between renders, so the effect below doesn't re-run on every keystroke.
  const stageMachines = useMemo(() => (machines.data ?? []).filter((m) => m.type === (isGlaze ? 'GLAZE_LINE' : 'KILN')), [machines.data, isGlaze])
  const printers = useMemo(() => (machines.data ?? []).filter((m) => m.type === 'DIGITAL_PRINTER'), [machines.data])

  // Tiles waiting for this stage in the chosen lot (good from the stage before, not yet entered here).
  const flow = overview.data?.lots.find((l) => l.lot.id === lotId)?.flow
  const waiting = flow ? (isGlaze ? flow.waitingGlaze : flow.waitingKiln) : undefined

  useEffect(() => {
    if (entry) form.setFieldsValue({ ...entry, prodDate: dayjs(entry.prodDate) })
  }, [entry, form])

  // New entry: the lot from ?lot=, today's shift, and the only machine of its kind if there is just one.
  useEffect(() => {
    if (!isNew) return
    const fromUrl = Number(params.get('lot'))
    if (fromUrl && !form.getFieldValue('lotId')) form.setFieldValue('lotId', fromUrl)
    if (current.data) {
      if (!form.isFieldTouched('prodDate')) form.setFieldValue('prodDate', dayjs(current.data.prodDate))
      if (!form.isFieldTouched('shiftId') && current.data.shiftId) form.setFieldValue('shiftId', current.data.shiftId)
    }
    if (stageMachines.length === 1 && !form.getFieldValue('machineId')) form.setFieldValue('machineId', stageMachines[0].id)
    if (isGlaze && printers.length === 1 && !form.getFieldValue('printerId')) form.setFieldValue('printerId', printers[0].id)
  }, [isNew, isGlaze, params, current.data, stageMachines, printers, form])

  // ...and "tiles in" starts at what is waiting, until the supervisor types their own count.
  useEffect(() => {
    if (isNew && waiting !== undefined && waiting > 0 && !form.isFieldTouched('tilesIn')) form.setFieldValue('tilesIn', waiting)
  }, [isNew, waiting, form])

  const lotOptions = useMemo(() => {
    const opts = (lots.data ?? []).map((l) => ({
      value: l.id,
      label: `${l.lotNo} · ${l.designName} · ${l.pressCode}${l.status === 'CLOSED' ? ` (${t('press.lotStatus.CLOSED')})` : ''}`,
    }))
    if (entry && !opts.some((o) => o.value === entry.lotId)) opts.push({ value: entry.lotId, label: `${entry.lotNo} · ${entry.designName} · ${entry.pressCode}` })
    return opts
  }, [lots.data, entry, t])
  const machineOptions = stageMachines.map((m) => ({ value: m.id, label: `${m.code} · ${m.name}` }))
  if (entry && !machineOptions.some((o) => o.value === entry.machineId)) machineOptions.push({ value: entry.machineId, label: `${entry.machineCode} · ${entry.machineName}` })
  const printerOptions = printers.map((m) => ({ value: m.id, label: `${m.code} · ${m.name}` }))
  if (entry?.printerId && !printerOptions.some((o) => o.value === entry.printerId)) printerOptions.push({ value: entry.printerId, label: entry.printerCode ?? '' })

  const sqmPerTile = lots.data?.find((l) => l.id === lotId)?.sqmPerTile ?? entry?.sqmPerTile
  const out = tilesIn ? tilesIn - (rejects ?? 0) : undefined
  const loss = tilesIn ? ((rejects ?? 0) * 100) / tilesIn : undefined

  async function saveDraft(): Promise<LineEntry | undefined> {
    let v: FormValues
    try {
      v = await form.validateFields()
    } catch {
      return undefined
    }
    try {
      return await save.mutateAsync({
        id: entryId,
        body: { ...v, plantId: plantId!, stage, prodDate: v.prodDate.format('YYYY-MM-DD'), remarks: v.remarks?.trim() || undefined },
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
    if (!saved) return
    try {
      const done = await post.mutateAsync(saved.id)
      message.success(t('store.postedOk', { no: done.entryNo }))
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
    setConfirmPost(false)
    if (isNew) navigate(`${base}/${saved.id}`, { replace: true })
  }

  const onDelete = async () => {
    try {
      await remove.mutateAsync(entryId!)
      message.success(t('store.deleted'))
      navigate(base)
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
  if (!isNew && !entry) return <div className="panel"><EmptyState title={t('store.notFound')} action={<Link to={base}>{t('common.back')}</Link>} /></div>

  const required = { required: true, message: t('common.required') }
  const noMachine = editable && machines.data !== undefined && stageMachines.length === 0

  return (
    <>
      <PageHeader
        title={isNew ? t(`line.newEntry.${stage}`) : (entry!.entryNo ?? t(`line.draftEntry.${stage}`))}
        subtitle={entry && (
          <Space size={10} wrap>
            <DocStatusTag status={entry.status} />
            <span><b>{entry.designName}</b> · {entry.lotNo}</span>
            <span>{shortDate(entry.prodDate)} · {t('prod.shift')} {entry.shiftCode}</span>
          </Space>
        )}
        icon={isGlaze ? <FormatPainterOutlined /> : <FireOutlined />}
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
              <Button danger icon={<StopOutlined />} onClick={() => setCancelling(true)}>{t('line.cancelEntry')}</Button>
            )}
          </Space>
        )}
      />

      <FadeIn delay={0.04}>
        {noMachine && <Alert className="doc-alert" type="warning" showIcon title={t('prod.noMachines', { type: t(`line.machine.${stage}`) })} />}
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
              <Form.Item name="lotId" label={t('press.lot')} rules={[required]}>
                <Select options={lotOptions} loading={lots.isLoading} showSearch={{ optionFilterProp: 'label' }} />
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
              <Form.Item name="machineId" label={t(`line.machine.${stage}`)} rules={[required]}>
                <Select options={machineOptions} loading={machines.isLoading} />
              </Form.Item>
            </Col>
            {isGlaze && (
              <Col xs={12} md={6}>
                <Form.Item name="printerId" label={t('line.printer')}>
                  <Select allowClear options={printerOptions} loading={machines.isLoading} />
                </Form.Item>
              </Col>
            )}
          </Row>
        </FadeIn>

        <FadeIn delay={0.1} className="panel doc-panel">
          <div className="form-section">{t('press.tiles')}</div>
          <Row gutter={16}>
            <Col xs={12} md={6}>
              <Form.Item name="tilesIn" label={t('line.tilesIn')} rules={[required]}
                extra={editable && waiting !== undefined ? t('line.waitingHint', { n: waiting.toLocaleString('en-IN') }) : undefined}>
                <InputNumber<number> min={1} precision={0} style={{ width: '100%' }} inputMode="numeric" />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item name="rejects" label={t('line.rejects')}
                rules={[{
                  validator: async (_, v?: number | null) => {
                    if (tilesIn && (v ?? 0) > tilesIn) throw new Error(t('press.rejectsTooMany'))
                  },
                }]}>
                <InputNumber<number> min={0} precision={0} style={{ width: '100%' }} inputMode="numeric" />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <div className="entry-result">
                <div><span className="stat-label">{t('line.tilesOut')}</span><b>{out !== undefined ? out.toLocaleString('en-IN') : '–'}</b></div>
                <div><span className="stat-label">{t('line.sqmOut')}</span><b>{out !== undefined && sqmPerTile ? `${qty(out * sqmPerTile, 0)} m²` : '–'}</b></div>
                <div><span className="stat-label">{t('press.loss')}</span><b>{loss !== undefined ? `${loss.toFixed(1)} %` : '–'}</b></div>
                {entry?.gasPerSqm != null && (
                  <div><span className="stat-label">{t('line.gasPerSqm')}</span><b>{qty(entry.gasPerSqm, 2)}</b></div>
                )}
              </div>
            </Col>
          </Row>
        </FadeIn>

        <FadeIn delay={0.12} className="panel doc-panel">
          <div className="form-section">{t('press.readings')}</div>
          <Row gutter={16}>
            {isGlaze ? (
              <>
                <Col xs={12} md={4}>
                  <Form.Item name="engobeGpt" label={t('line.engobe')}>
                    <InputNumber<number> min={0} suffix="g/tile" style={{ width: '100%' }} inputMode="decimal" />
                  </Form.Item>
                </Col>
                <Col xs={12} md={4}>
                  <Form.Item name="glazeGpt" label={t('line.glaze')}>
                    <InputNumber<number> min={0} suffix="g/tile" style={{ width: '100%' }} inputMode="decimal" />
                  </Form.Item>
                </Col>
                <Col xs={12} md={4}>
                  <Form.Item name="glazeDensity" label={t('line.glazeDensity')}>
                    <InputNumber<number> min={0} max={3000} suffix="g/L" style={{ width: '100%' }} inputMode="decimal" />
                  </Form.Item>
                </Col>
                <Col xs={12} md={4}>
                  <Form.Item name="inkLitres" label={t('line.ink')}>
                    <InputNumber<number> min={0} suffix="L" style={{ width: '100%' }} inputMode="decimal" />
                  </Form.Item>
                </Col>
              </>
            ) : (
              <>
                <Col xs={12} md={4}>
                  <Form.Item name="maxTemp" label={t('line.maxTemp')}>
                    <InputNumber<number> min={0} max={1500} suffix="°C" style={{ width: '100%' }} inputMode="decimal" />
                  </Form.Item>
                </Col>
                <Col xs={12} md={4}>
                  <Form.Item name="cycleMin" label={t('line.cycle')}>
                    <InputNumber<number> min={0} max={999} suffix="min" style={{ width: '100%' }} inputMode="decimal" />
                  </Form.Item>
                </Col>
                <Col xs={12} md={4}>
                  <Form.Item name="gasScm" label={t('line.gas')}>
                    <InputNumber<number> min={0} suffix="SCM" style={{ width: '100%' }} inputMode="decimal" />
                  </Form.Item>
                </Col>
              </>
            )}
            <Col xs={24} md={isGlaze ? 8 : 12}>
              <Form.Item name="remarks" label={t('store.remarks')}>
                <Input.TextArea maxLength={500} autoSize={{ minRows: 1, maxRows: 4 }} />
              </Form.Item>
            </Col>
          </Row>
        </FadeIn>
      </Form>

      <Modal open={confirmPost} title={t('line.postTitle')} okText={t('store.post')} cancelText={t('common.cancel')}
        confirmLoading={save.isPending || post.isPending} onOk={onPostConfirmed} onCancel={() => setConfirmPost(false)}>
        {t('line.postHint')}
      </Modal>
      <ReasonModal
        open={cancelling}
        title={t('store.cancelTitle', { no: entry?.entryNo })}
        hint={t('line.cancelHint')}
        okText={t('line.cancelEntry')}
        loading={cancel.isPending}
        onOk={onCancel}
        onClose={() => setCancelling(false)}
      />
    </>
  )
}
