import { useEffect, useMemo, useState } from 'react'
import { Alert, App, Button, Col, DatePicker, Form, Input, InputNumber, Modal, Popconfirm, Row, Select, Space, Spin } from 'antd'
import { ArrowLeftOutlined, CheckCircleOutlined, CloudOutlined, DeleteOutlined, SaveOutlined, StopOutlined } from '@ant-design/icons'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import dayjs, { type Dayjs } from 'dayjs'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import type { SprayRun, SprayRunRequest } from '../../api/types'
import NoPlant from '../store/NoPlant'
import DocStatusTag from '../store/DocStatusTag'
import { dateTime, qty, shortDate } from '../store/format'
import {
  useAvailableBatches,
  useCancelRun,
  useCurrentShift,
  useDeleteRun,
  usePostRun,
  useProdMachines,
  useProdShifts,
  useSaveRun,
  useSprayRun,
} from './api'
import BatchPicker, { type BatchUse, type PickableBatch } from './BatchPicker'
import ReasonModal from './ReasonModal'
import { DATE_TIME, toLocalDateTime } from './time'

const BASE = '/production/spray-runs'

type FormValues = Omit<SprayRunRequest, 'plantId' | 'prodDate' | 'runStart' | 'runEnd'> & {
  prodDate: Dayjs
  runStart: Dayjs
  runEnd?: Dayjs
}

const toForm = (r: SprayRun): FormValues => ({
  ...r,
  prodDate: dayjs(r.prodDate),
  runStart: dayjs(r.runStart),
  runEnd: r.runEnd ? dayjs(r.runEnd) : undefined,
  remarks: r.remarks,
  batches: r.batches.map((b) => ({ bodyBatchId: b.bodyBatchId, finished: b.finished })),
})

const toRequest = (v: FormValues, plantId: number): SprayRunRequest => ({
  ...v,
  plantId,
  prodDate: v.prodDate.format('YYYY-MM-DD'),
  runStart: toLocalDateTime(v.runStart),
  runEnd: v.runEnd ? toLocalDateTime(v.runEnd) : undefined,
  remarks: v.remarks?.trim() || undefined,
})

export default function SprayRunPage() {
  const { id } = useParams()
  return <RunEditor key={id} runId={id && id !== 'new' ? Number(id) : undefined} />
}

function RunEditor({ runId }: { runId?: number }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const navigate = useNavigate()
  const location = useLocation()
  const { can, currentPlant } = useAuth()
  const isNew = runId === undefined
  const goBack = () => (location.key !== 'default' ? navigate(-1) : navigate(BASE))

  const runQuery = useSprayRun(runId)
  const run = runQuery.data
  const plantId = run?.plantId ?? currentPlant?.id
  const canEntry = can('BODY_PREP_ENTRY')
  const editable = canEntry && (isNew || run?.status === 'DRAFT')

  const current = useCurrentShift()
  const shifts = useProdShifts()
  const machines = useProdMachines(plantId)
  const available = useAvailableBatches(editable ? plantId : undefined)

  const save = useSaveRun()
  const post = usePostRun()
  const cancel = useCancelRun()
  const remove = useDeleteRun()
  const [confirmPost, setConfirmPost] = useState(false)
  const [cancelling, setCancelling] = useState(false)

  const [form] = Form.useForm<FormValues>()

  useEffect(() => {
    if (run) form.setFieldsValue(toForm(run))
  }, [run, form])

  useEffect(() => {
    if (!isNew || !current.data) return
    if (!form.isFieldTouched('prodDate')) form.setFieldValue('prodDate', dayjs(current.data.prodDate))
    if (!form.isFieldTouched('shiftId') && current.data.shiftId) form.setFieldValue('shiftId', current.data.shiftId)
  }, [isNew, current.data, form])

  const ofType = (type: string, keep?: { id: number; code: string; name: string }) => {
    const opts = (machines.data ?? []).filter((m) => m.type === type).map((m) => ({ value: m.id, label: `${m.code} · ${m.name}` }))
    if (keep && !opts.some((o) => o.value === keep.id)) opts.push({ value: keep.id, label: `${keep.code} · ${keep.name}` })
    return opts
  }
  const dryerOptions = ofType('SPRAY_DRYER', run && { id: run.sprayDryerId, code: run.sprayDryerCode, name: run.sprayDryerName })
  const siloOptions = ofType('SILO', run && { id: run.siloId, code: run.siloCode, name: run.siloName })
  const shiftOptions = (shifts.data ?? []).map((s) => ({ value: s.id, label: `${s.code} · ${s.name}` }))
  if (run && !shiftOptions.some((o) => o.value === run.shiftId)) shiftOptions.push({ value: run.shiftId, label: `${run.shiftCode} · ${run.shiftName}` })

  // What can be picked: slip waiting in tanks, plus whatever this run already uses.
  const pickable = useMemo<PickableBatch[]>(() => {
    const map = new Map<number, PickableBatch>()
    for (const b of run?.batches ?? []) {
      map.set(b.bodyBatchId, { id: b.bodyBatchId, batchNo: b.batchNo, ballMillCode: b.ballMillCode, chargeTon: b.chargeTon,
        slipTankCode: b.slipTankCode, millingEnd: b.millingEnd, slipDensity: b.slipDensity, slipResidue: b.slipResidue })
    }
    for (const b of available.data ?? []) map.set(b.id, b)
    return [...map.values()]
  }, [run, available.data])

  async function saveDraft(): Promise<SprayRun | undefined> {
    let values: FormValues
    try {
      values = await form.validateFields()
    } catch {
      return undefined
    }
    try {
      return await save.mutateAsync({ id: runId, body: toRequest(values, plantId!) })
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
      message.success(t('store.postedOk', { no: done.runNo }))
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
    setConfirmPost(false)
    if (isNew) navigate(`${BASE}/${saved.id}`, { replace: true })
  }

  const onDelete = async () => {
    try {
      await remove.mutateAsync(runId!)
      message.success(t('store.deleted'))
      navigate(BASE)
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const onCancel = async (reason: string) => {
    try {
      await cancel.mutateAsync({ id: runId!, reason })
      message.success(t('store.cancelOk', { no: run?.runNo }))
      setCancelling(false)
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  if (isNew && !currentPlant) return <NoPlant />
  if (runQuery.isLoading) return <div className="center-block"><Spin size="large" /></div>
  if (!isNew && !run) {
    return <div className="panel"><EmptyState title={t('store.notFound')} action={<Link to={BASE}>{t('common.back')}</Link>} /></div>
  }

  const required = { required: true, message: t('common.required') }
  const missing = editable && machines.data !== undefined
    ? (['SPRAY_DRYER', 'SILO'] as const).filter((type) => !machines.data!.some((m) => m.type === type))
    : []

  const actions = (
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
      {run?.status === 'POSTED' && can('PRODUCTION_CANCEL') && (
        <Button danger icon={<StopOutlined />} onClick={() => setCancelling(true)}>{t('prod.cancelRun')}</Button>
      )}
    </Space>
  )

  return (
    <>
      <PageHeader
        title={isNew ? t('prod.newRunFull') : (run!.runNo ?? t('prod.draftRun'))}
        subtitle={run && (
          <Space size={10} wrap>
            <DocStatusTag status={run.status} />
            <span>{shortDate(run.prodDate)} · {t('prod.shift')} {run.shiftCode}</span>
            <span>{t('store.createdBy', { by: run.createdBy })}</span>
          </Space>
        )}
        icon={<CloudOutlined />}
        extra={actions}
      />

      <FadeIn delay={0.04}>
        {missing.map((type) => (
          <Alert key={type} className="doc-alert" type="warning" showIcon title={t('prod.noMachines', { type: t(`machineType.${type}`) })} />
        ))}
        {run?.status === 'POSTED' && (
          <Alert className="doc-alert" type="success" showIcon
            title={`${t('store.postedInfo', { by: run.postedBy, at: dateTime(run.postedAt) })} · ${run.siloCode}: ${t('press.siloHas', { t: qty(run.powderLeft, 1) })}`} />
        )}
        {run?.status === 'CANCELLED' && (
          <Alert className="doc-alert" type="error" showIcon
            title={t('store.cancelledInfo', { by: run.cancelledBy, at: dateTime(run.cancelledAt), reason: run.cancelReason })} />
        )}
      </FadeIn>

      <Form<FormValues> form={form} layout="vertical" disabled={!editable} className={editable ? undefined : 'doc-readonly'}
        initialValues={{ runStart: dayjs(), batches: [] }}>
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
            <Col xs={12} md={6}>
              <Form.Item name="sprayDryerId" label={t('prod.sprayDryer')} rules={[required]}>
                <Select options={dryerOptions} loading={machines.isLoading} />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item name="siloId" label={t('prod.silo')} rules={[required]}>
                <Select options={siloOptions} loading={machines.isLoading} />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item name="runStart" label={t('prod.runStart')} rules={[required]}>
                <DatePicker showTime={{ format: 'HH:mm' }} format={DATE_TIME} style={{ width: '100%' }} needConfirm={false}
                  disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item name="runEnd" label={t('prod.runEnd')}>
                <DatePicker showTime={{ format: 'HH:mm' }} format={DATE_TIME} style={{ width: '100%' }} needConfirm={false}
                  disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
              </Form.Item>
            </Col>
            <Col xs={12} md={4}>
              <Form.Item name="powderTon" label={t('prod.powder')} rules={[required]}>
                <InputNumber<number> min={0} suffix="T" style={{ width: '100%' }} inputMode="decimal" />
              </Form.Item>
            </Col>
            <Col xs={12} md={4}>
              <Form.Item name="powderMoisture" label={t('prod.moisture')}>
                <InputNumber<number> min={0} max={20} step={0.1} suffix="%" style={{ width: '100%' }} inputMode="decimal" />
              </Form.Item>
            </Col>
          </Row>
          <div className="form-section">{t('prod.readings')}</div>
          <Row gutter={16}>
            <Col xs={8} md={6}>
              <Form.Item name="gasScm" label={t('prod.gas')}>
                <InputNumber<number> min={0} suffix="SCM" style={{ width: '100%' }} inputMode="decimal" />
              </Form.Item>
            </Col>
            <Col xs={8} md={6}>
              <Form.Item name="inletTemp" label={t('prod.inlet')}>
                <InputNumber<number> min={0} max={1200} suffix="°C" style={{ width: '100%' }} inputMode="decimal" />
              </Form.Item>
            </Col>
            <Col xs={8} md={6}>
              <Form.Item name="outletTemp" label={t('prod.outlet')}>
                <InputNumber<number> min={0} max={1200} suffix="°C" style={{ width: '100%' }} inputMode="decimal" />
              </Form.Item>
            </Col>
            <Col xs={24} md={6}>
              <Form.Item name="remarks" label={t('store.remarks')}>
                <Input.TextArea maxLength={500} autoSize={{ minRows: 1, maxRows: 4 }} />
              </Form.Item>
            </Col>
          </Row>
        </FadeIn>

        <FadeIn delay={0.12} className="panel doc-panel">
          <div className="form-section">{t('prod.bodyBatches')}</div>
          {editable && <p className="panel-hint">{t('prod.pickBatches')}</p>}
          {editable && available.data?.length === 0 && pickable.length === 0 && (
            <Alert type="warning" showIcon title={t('prod.noWaiting')} style={{ marginBottom: 12 }} />
          )}
          <Form.Item name="batches" rules={[{ validator: async (_, v?: BatchUse[]) => {
            if (!v?.length) throw new Error(t('prod.pickOne'))
          } }]}>
            <BatchPicker batches={pickable} disabled={!editable} />
          </Form.Item>
        </FadeIn>
      </Form>

      <Modal
        open={confirmPost}
        title={t('prod.postTitle')}
        okText={t('store.post')}
        cancelText={t('common.cancel')}
        confirmLoading={save.isPending || post.isPending}
        onOk={onPostConfirmed}
        onCancel={() => setConfirmPost(false)}
      >
        {t('prod.postHint')}
      </Modal>
      <ReasonModal
        open={cancelling}
        title={t('store.cancelTitle', { no: run?.runNo })}
        hint={t('prod.cancelRunHint')}
        okText={t('prod.cancelRun')}
        loading={cancel.isPending}
        onOk={onCancel}
        onClose={() => setCancelling(false)}
      />
    </>
  )
}
