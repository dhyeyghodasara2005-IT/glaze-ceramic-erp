import { useEffect, useMemo, useState } from 'react'
import { Alert, App, Button, Col, DatePicker, Form, Input, InputNumber, Modal, Popconfirm, Row, Select, Space, Spin } from 'antd'
import { ArrowLeftOutlined, BuildOutlined, CheckCircleOutlined, DeleteOutlined, SaveOutlined, StopOutlined } from '@ant-design/icons'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import dayjs, { type Dayjs } from 'dayjs'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import type { PressEntry, PressEntryRequest } from '../../api/types'
import NoPlant from '../store/NoPlant'
import DocStatusTag from '../store/DocStatusTag'
import { dateTime, qty, shortDate } from '../store/format'
import { useCurrentShift, useProdMachines, useProdShifts } from './api'
import { useCancelEntry, useDeleteEntry, usePostEntry, usePressEntry, useSaveEntry, useSelectableLots, useSilos } from './pressApi'
import ReasonModal from './ReasonModal'

const BASE = '/production/press-entries'

type FormValues = Omit<PressEntryRequest, 'plantId' | 'prodDate'> & { prodDate: Dayjs }

/** tiles x kg -> tonnes, rounded like the server (3 decimals). */
const autoPowder = (pressed?: number, weight?: number) =>
  pressed && weight ? Math.round(pressed * weight) / 1000 : undefined

const toForm = (e: PressEntry): FormValues => ({
  ...e,
  prodDate: dayjs(e.prodDate),
  // Only show the powder as "typed" when it differs from tiles x weight - otherwise leave it to the server.
  powderTon: autoPowder(e.pressedCount, e.greenWeightKg) === e.powderTon ? undefined : e.powderTon,
})

export default function PressEntryPage() {
  const { id } = useParams()
  return <EntryEditor key={id} entryId={id && id !== 'new' ? Number(id) : undefined} />
}

function EntryEditor({ entryId }: { entryId?: number }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const { can, currentPlant } = useAuth()
  const isNew = entryId === undefined
  const goBack = () => (location.key !== 'default' ? navigate(-1) : navigate(BASE))

  const entryQuery = usePressEntry(entryId)
  const entry = entryQuery.data
  const plantId = entry?.plantId ?? currentPlant?.id
  const canEntry = can('PRESS_ENTRY')
  const editable = canEntry && (isNew || entry?.status === 'DRAFT')

  const current = useCurrentShift()
  const shifts = useProdShifts()
  const machines = useProdMachines(plantId)
  const lots = useSelectableLots(editable ? plantId : undefined)
  const silos = useSilos(plantId)

  const save = useSaveEntry()
  const post = usePostEntry()
  const cancel = useCancelEntry()
  const remove = useDeleteEntry()
  const [confirmPost, setConfirmPost] = useState(false)
  const [cancelling, setCancelling] = useState(false)

  const [form] = Form.useForm<FormValues>()
  const lotId = Form.useWatch('lotId', form) as number | undefined
  const pressed = Form.useWatch('pressedCount', form) as number | undefined
  const pressRejects = Form.useWatch('pressRejects', form) as number | undefined
  const dryerRejects = Form.useWatch('dryerRejects', form) as number | undefined
  const weight = Form.useWatch('greenWeightKg', form) as number | undefined

  useEffect(() => {
    if (entry) form.setFieldsValue(toForm(entry))
  }, [entry, form])

  // New entry: today's shift, and the lot it was opened for (?lot=12 from the press home or the lot page).
  useEffect(() => {
    if (!isNew) return
    const fromUrl = Number(params.get('lot'))
    if (fromUrl && !form.getFieldValue('lotId')) form.setFieldValue('lotId', fromUrl)
    if (current.data) {
      if (!form.isFieldTouched('prodDate')) form.setFieldValue('prodDate', dayjs(current.data.prodDate))
      if (!form.isFieldTouched('shiftId') && current.data.shiftId) form.setFieldValue('shiftId', current.data.shiftId)
    }
  }, [isNew, params, current.data, form])

  const lotOptions = useMemo(() => {
    const opts = (lots.data ?? []).map((l) => ({
      value: l.id,
      label: `${l.lotNo} · ${l.designName} · ${l.pressCode}${l.status === 'CLOSED' ? ` (${t('press.lotStatus.CLOSED')})` : ''}`,
    }))
    if (entry && !opts.some((o) => o.value === entry.lotId)) opts.push({ value: entry.lotId, label: `${entry.lotNo} · ${entry.designName} · ${entry.pressCode}` })
    return opts
  }, [lots.data, entry, t])
  const chosenLot = lots.data?.find((l) => l.id === lotId)
  const sqmPerTile = chosenLot?.sqmPerTile ?? entry?.sqmPerTile

  const siloTonnes = new Map((silos.data ?? []).map((s) => [s.siloId, s.tonnes]))
  const ofType = (type: string, keep?: { id?: number; code?: string }) => {
    const opts = (machines.data ?? []).filter((m) => m.type === type).map((m) => ({
      value: m.id,
      label: type === 'SILO' ? `${m.code} · ${t('press.siloHas', { t: qty(siloTonnes.get(m.id) ?? 0, 1) })}` : `${m.code} · ${m.name}`,
    }))
    if (keep?.id && !opts.some((o) => o.value === keep.id)) opts.push({ value: keep.id, label: keep.code ?? '' })
    return opts
  }

  // Live figures while typing (the server works them out again).
  const good = pressed ? pressed - (pressRejects ?? 0) - (dryerRejects ?? 0) : undefined
  const loss = pressed ? (((pressRejects ?? 0) + (dryerRejects ?? 0)) * 100) / pressed : undefined
  const auto = autoPowder(pressed, weight)

  async function saveDraft(): Promise<PressEntry | undefined> {
    let v: FormValues
    try {
      v = await form.validateFields()
    } catch {
      return undefined
    }
    try {
      return await save.mutateAsync({ id: entryId, body: { ...v, plantId: plantId!, prodDate: v.prodDate.format('YYYY-MM-DD'), remarks: v.remarks?.trim() || undefined } })
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
    if (isNew) navigate(`${BASE}/${saved.id}`, { replace: true })
  }

  const onDelete = async () => {
    try {
      await remove.mutateAsync(entryId!)
      message.success(t('store.deleted'))
      navigate(BASE)
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
  if (!isNew && !entry) return <div className="panel"><EmptyState title={t('store.notFound')} action={<Link to={BASE}>{t('common.back')}</Link>} /></div>

  const required = { required: true, message: t('common.required') }
  const noLots = editable && lots.data !== undefined && lots.data.length === 0

  return (
    <>
      <PageHeader
        title={isNew ? t('press.newEntry') : (entry!.entryNo ?? t('press.draftEntry'))}
        subtitle={entry && (
          <Space size={10} wrap>
            <DocStatusTag status={entry.status} />
            <span><b>{entry.designName}</b> · {entry.lotNo}</span>
            <span>{shortDate(entry.prodDate)} · {t('prod.shift')} {entry.shiftCode}</span>
          </Space>
        )}
        icon={<BuildOutlined />}
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
              <Button danger icon={<StopOutlined />} onClick={() => setCancelling(true)}>{t('press.cancelEntry')}</Button>
            )}
          </Space>
        )}
      />

      <FadeIn delay={0.04}>
        {noLots && <Alert className="doc-alert" type="warning" showIcon title={t('press.noLots')} action={<Link to="/production/press">{t('press.startDesign')}</Link>} />}
        {entry?.status === 'POSTED' && (
          <Alert className="doc-alert" type="success" showIcon title={t('store.postedInfo', { by: entry.postedBy, at: dateTime(entry.postedAt) })} />
        )}
        {entry?.status === 'CANCELLED' && (
          <Alert className="doc-alert" type="error" showIcon
            title={t('store.cancelledInfo', { by: entry.cancelledBy, at: dateTime(entry.cancelledAt), reason: entry.cancelReason })} />
        )}
        {entry && entry.status === 'POSTED' && entry.untracedTon > 0 && (
          <Alert className="doc-alert" type="warning" showIcon title={`${t('press.untraced')}: ${qty(entry.untracedTon, 3)} T. ${t('press.untracedHint')}`} />
        )}
      </FadeIn>

      {entry?.status === 'POSTED' && entry.powders.length > 0 && (
        <FadeIn delay={0.06} className="panel doc-panel">
          <div className="form-section">{t('press.powderFrom')}</div>
          <div className="trace-list">
            {entry.powders.map((p) => (
              <div key={p.runId} className="trace-row">
                <span className="trace-tonnes">{qty(p.tonnes, 1)} T</span>
                <Link to={`/production/spray-runs/${p.runId}`} className="cell-title">{p.runNo}</Link>
              </div>
            ))}
          </div>
        </FadeIn>
      )}

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
              <Form.Item name="siloId" label={t('press.silo')} rules={[required]} extra={editable ? t('press.powderHint') : undefined}>
                <Select options={ofType('SILO', entry && { id: entry.siloId, code: entry.siloCode })} loading={machines.isLoading} />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item name="dryerId" label={t('press.dryer')}>
                <Select allowClear options={ofType('DRYER', entry && { id: entry.dryerId, code: entry.dryerCode })} loading={machines.isLoading} />
              </Form.Item>
            </Col>
          </Row>
        </FadeIn>

        <FadeIn delay={0.1} className="panel doc-panel">
          <div className="form-section">{t('press.tiles')}</div>
          <Row gutter={16}>
            <Col xs={8} md={4}>
              <Form.Item name="pressedCount" label={t('press.pressed')} rules={[required]}>
                <InputNumber<number> min={1} precision={0} style={{ width: '100%' }} inputMode="numeric" />
              </Form.Item>
            </Col>
            <Col xs={8} md={4}>
              <Form.Item name="pressRejects" label={t('press.pressRejects')}>
                <InputNumber<number> min={0} precision={0} style={{ width: '100%' }} inputMode="numeric" />
              </Form.Item>
            </Col>
            <Col xs={8} md={4}>
              <Form.Item name="dryerRejects" label={t('press.dryerRejects')}
                rules={[{
                  validator: async (_, v?: number | null) => {
                    if (pressed && (v ?? 0) + (pressRejects ?? 0) > pressed) throw new Error(t('press.rejectsTooMany'))
                  },
                }]}>
                <InputNumber<number> min={0} precision={0} style={{ width: '100%' }} inputMode="numeric" />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <div className="entry-result">
                <div><span className="stat-label">{t('press.goodCount')}</span><b>{good !== undefined ? good.toLocaleString('en-IN') : '–'}</b></div>
                <div><span className="stat-label">{t('press.goodSqm')}</span><b>{good !== undefined && sqmPerTile ? `${qty(good * sqmPerTile, 0)} m²` : '–'}</b></div>
                <div><span className="stat-label">{t('press.loss')}</span><b>{loss !== undefined ? `${loss.toFixed(1)} %` : '–'}</b></div>
              </div>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item name="greenWeightKg" label={t('press.greenWeight')}>
                <InputNumber<number> min={0} max={200} step={0.1} suffix="kg" style={{ width: '100%' }} inputMode="decimal" />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item name="powderTon" label={t('press.powderTon')}
                extra={auto !== undefined ? t('press.powderAuto', { t: qty(auto, 3) }) : undefined}
                rules={[{
                  validator: async (_, v?: number | null) => {
                    if (v == null && !weight) throw new Error(t('common.required'))
                  },
                }]}>
                <InputNumber<number> min={0} suffix="T" style={{ width: '100%' }} inputMode="decimal"
                  placeholder={auto !== undefined ? qty(auto, 3) : undefined} />
              </Form.Item>
            </Col>
          </Row>
        </FadeIn>

        <FadeIn delay={0.12} className="panel doc-panel">
          <div className="form-section">{t('press.readings')}</div>
          <Row gutter={16}>
            <Col xs={12} md={4}>
              <Form.Item name="pressure" label={t('press.pressure')}>
                <InputNumber<number> min={0} suffix="kg/cm²" style={{ width: '100%' }} inputMode="decimal" />
              </Form.Item>
            </Col>
            <Col xs={12} md={4}>
              <Form.Item name="cyclesPerMin" label={t('press.cycles')}>
                <InputNumber<number> min={0} max={99} step={0.1} style={{ width: '100%' }} inputMode="decimal" />
              </Form.Item>
            </Col>
            <Col xs={12} md={4}>
              <Form.Item name="thicknessMm" label={t('press.thickness')}>
                <InputNumber<number> min={0} max={99} step={0.1} suffix="mm" style={{ width: '100%' }} inputMode="decimal" />
              </Form.Item>
            </Col>
            <Col xs={12} md={4}>
              <Form.Item name="dryerTemp" label={t('press.dryerTemp')}>
                <InputNumber<number> min={0} max={999} suffix="°C" style={{ width: '100%' }} inputMode="decimal" />
              </Form.Item>
            </Col>
            <Col xs={12} md={4}>
              <Form.Item name="dryerMoisture" label={t('press.dryerMoisture')}>
                <InputNumber<number> min={0} max={20} step={0.1} suffix="%" style={{ width: '100%' }} inputMode="decimal" />
              </Form.Item>
            </Col>
            <Col xs={24} md={4}>
              <Form.Item name="remarks" label={t('store.remarks')}>
                <Input.TextArea maxLength={500} autoSize={{ minRows: 1, maxRows: 4 }} />
              </Form.Item>
            </Col>
          </Row>
        </FadeIn>
      </Form>

      <Modal open={confirmPost} title={t('press.postTitle')} okText={t('store.post')} cancelText={t('common.cancel')}
        confirmLoading={save.isPending || post.isPending} onOk={onPostConfirmed} onCancel={() => setConfirmPost(false)}>
        {t('press.postHint')}
      </Modal>
      <ReasonModal
        open={cancelling}
        title={t('store.cancelTitle', { no: entry?.entryNo })}
        hint={t('press.cancelEntryHint')}
        okText={t('press.cancelEntry')}
        loading={cancel.isPending}
        onOk={onCancel}
        onClose={() => setCancelling(false)}
      />
    </>
  )
}
