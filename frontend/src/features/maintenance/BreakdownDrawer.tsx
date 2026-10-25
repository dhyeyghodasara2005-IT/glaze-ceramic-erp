import { useEffect, useMemo, useState } from 'react'
import { Alert, App, Button, DatePicker, Descriptions, Drawer, Form, Input, InputNumber, Select, Space, Spin } from 'antd'
import { CheckOutlined, EditOutlined, MinusCircleOutlined, PlusOutlined, StopOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import dayjs, { type Dayjs } from 'dayjs'
import { useAuth } from '../../auth/AuthContext'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import { BREAKDOWN_CATEGORIES, type BreakdownCategory } from '../../api/types'
import { useDrawerSize } from '../../components/useDrawerSize'
import { dateTime, money, qty } from '../store/format'
import ReasonModal from '../production/ReasonModal'
import { DATE_TIME, dayClock, duration, toLocalDateTime } from '../production/time'
import { useNow } from '../production/useNow'
import {
  useBreakdown, useCancelBreakdown, useCloseBreakdown, useSpareGodowns, useSpares, useSpareStock,
} from './maintenanceApi'
import { BreakdownStatusTag, CategoryTag } from './maintenanceParts'
import ReportBreakdownModal from './ReportBreakdownModal'

interface SpareRow {
  materialId?: number
  locationId?: number
  quantity?: number
}
interface CloseValues {
  restartedAt: Dayjs
  category?: BreakdownCategory
  failedPart?: string
  cause?: string
  actionTaken: string
  fixedBy?: string
  spares: SpareRow[]
}

/** One breakdown: what happened, and - while the machine is down - the maintenance team's "running again" form. */
export default function BreakdownDrawer({ id, onClose }: { id?: number; onClose: () => void }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const { can } = useAuth()
  const width = useDrawerSize(640)
  const query = useBreakdown(id)
  const b = query.data
  useNow(30_000)
  const close = useCloseBreakdown()
  const cancel = useCancelBreakdown()
  const [editing, setEditing] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  const [form] = Form.useForm<CloseValues>()

  const isOpen = b?.status === 'OPEN'
  const mayClose = isOpen && can('MAINTENANCE_ENTRY')
  const mayEdit = isOpen && (can('BREAKDOWN_REPORT') || can('MAINTENANCE_ENTRY'))
  const mayCancel = (isOpen && (can('BREAKDOWN_REPORT') || can('MAINTENANCE_ENTRY'))) || (b?.status === 'CLOSED' && can('MAINTENANCE_CANCEL'))

  const spares = useSpares(!!mayClose)
  const godowns = useSpareGodowns(mayClose ? b?.plantId : undefined)
  const stock = useSpareStock(mayClose ? b?.plantId : undefined)
  const rows = Form.useWatch('spares', form) as SpareRow[] | undefined

  useEffect(() => {
    if (mayClose) form.setFieldsValue({ restartedAt: dayjs(), spares: [] })
  }, [mayClose, id, form])

  const stockOf = useMemo(() => {
    const map = new Map<string, number>()
    for (const s of stock.data ?? []) map.set(`${s.materialId}:${s.locationId}`, s.quantity)
    return map
  }, [stock.data])
  const spareOptions = useMemo(() => {
    const list = spares.data ?? []
    const option = (s: (typeof list)[number]) => ({ value: s.id, label: `${s.name} · ${s.code}` })
    return [
      { label: t('maint.sparesGroup'), options: list.filter((s) => s.spare).map(option) },
      { label: t('maint.otherMaterials'), options: list.filter((s) => !s.spare).map(option) },
    ].filter((g) => g.options.length)
  }, [spares.data, t])
  const unitOf = (materialId?: number) => spares.data?.find((s) => s.id === materialId)

  const onRunning = async () => {
    let v: CloseValues
    try {
      v = await form.validateFields()
    } catch {
      return
    }
    try {
      const done = await close.mutateAsync({
        id: b!.id,
        body: {
          restartedAt: toLocalDateTime(v.restartedAt),
          category: v.category!,
          failedPart: v.failedPart?.trim() || undefined,
          cause: v.cause?.trim() || undefined,
          actionTaken: v.actionTaken.trim(),
          fixedBy: v.fixedBy?.trim() || undefined,
          spares: (v.spares ?? []).map((s) => ({ materialId: s.materialId!, locationId: s.locationId!, quantity: s.quantity! })),
        },
      })
      message.success(t('maint.closedOk', { machine: done.machine.name, time: duration(done.stoppedAt, done.restartedAt) }))
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const onCancel = async (reason: string) => {
    try {
      await cancel.mutateAsync({ id: b!.id, reason })
      message.success(t('maint.cancelledOk', { no: b!.breakdownNo }))
      setCancelling(false)
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }
  return (
    <Drawer open={id !== undefined} onClose={onClose} size={width} destroyOnHidden
      title={b ? <Space wrap>{b.breakdownNo}<BreakdownStatusTag status={b.status} /></Space> : t('maint.breakdown')}
      extra={b && (
        <Space>
          {mayEdit && <Button icon={<EditOutlined />} onClick={() => setEditing(true)}>{t('common.edit')}</Button>}
          {mayCancel && <Button danger icon={<StopOutlined />} onClick={() => setCancelling(true)}>{t('maint.cancel')}</Button>}
        </Space>
      )}>
      {query.isLoading && <Spin />}
      {b && (
        <>
          <div className={`bd-banner ${b.status.toLowerCase()}`}>
            <div className="bd-banner-machine">{b.machine.name} <span>{b.machine.code} · {t(`machineType.${b.machine.type}`)}</span></div>
            <div className="bd-banner-time">
              {isOpen ? t('maint.downFor', { time: duration(b.stoppedAt) }) : b.status === 'CLOSED'
                ? t('maint.wasDown', { time: duration(b.stoppedAt, b.restartedAt) }) : t('maint.cancelledShort')}
            </div>
          </div>

          <Descriptions column={1} size="small" className="bd-facts">
            <Descriptions.Item label={t('maint.symptom')}>{b.symptom}</Descriptions.Item>
            <Descriptions.Item label={t('maint.stoppedAt')}>{dayClock(b.stoppedAt)}</Descriptions.Item>
            <Descriptions.Item label={t('maint.reportedBy')}>{b.reportedBy} · {dateTime(b.reportedAt)}</Descriptions.Item>
            {b.restartedAt && <Descriptions.Item label={t('maint.restartedAt')}>{dayClock(b.restartedAt)}</Descriptions.Item>}
            {b.category && <Descriptions.Item label={t('maint.category')}><CategoryTag category={b.category} /></Descriptions.Item>}
            {b.failedPart && <Descriptions.Item label={t('maint.failedPart')}>{b.failedPart}</Descriptions.Item>}
            {b.cause && <Descriptions.Item label={t('maint.cause')}>{b.cause}</Descriptions.Item>}
            {b.actionTaken && <Descriptions.Item label={t('maint.action')}>{b.actionTaken}</Descriptions.Item>}
            {b.fixedBy && <Descriptions.Item label={t('maint.fixedBy')}>{b.fixedBy}</Descriptions.Item>}
            {b.closedBy && <Descriptions.Item label={t('maint.closedBy')}>{b.closedBy} · {dateTime(b.closedAt)}</Descriptions.Item>}
          </Descriptions>

          {b.spares.length > 0 && (
            <>
              <div className="form-section">{t('maint.sparesUsed')}</div>
              <ul className="bd-spares">
                {b.spares.map((s) => (
                  <li key={s.lineNo}>
                    <span>{s.materialName} <span className="cell-sub">{s.materialCode} · {s.locationCode}</span></span>
                    <b>{qty(s.quantity)} {s.unit}</b>
                    <span className="cell-sub">{money(s.value)}</span>
                  </li>
                ))}
              </ul>
              <div className="bd-cost">
                {t('maint.spareCost')}: <b>{money(b.spareCost)}</b>
                {b.issueDocumentId && can('STORE_VIEW') && (
                  <> · <Link to={`/store/issues/${b.issueDocumentId}`}>{b.issueDocumentNo}</Link></>
                )}
              </div>
            </>
          )}

          {b.status === 'CANCELLED' && (
            <Alert type="error" showIcon title={t('store.cancelledInfo', { by: b.cancelledBy, at: dateTime(b.cancelledAt), reason: b.cancelReason })} />
          )}

          {mayClose && (
            <Form<CloseValues> form={form} layout="vertical" className="bd-close">
              <div className="form-section">{t('maint.closeSection')}</div>
              <div className="bd-close-grid">
                <Form.Item name="restartedAt" label={t('maint.restartedAt')} rules={[required]}>
                  <DatePicker showTime={{ format: 'HH:mm' }} format={DATE_TIME} style={{ width: '100%' }} needConfirm={false}
                    disabledDate={(d) => d.isAfter(dayjs(), 'day') || d.isBefore(dayjs(b.stoppedAt), 'day')} />
                </Form.Item>
                <Form.Item name="category" label={t('maint.category')} rules={[required]}>
                  <Select options={BREAKDOWN_CATEGORIES.map((c) => ({ value: c, label: t(`maint.categories.${c}`) }))} />
                </Form.Item>
                <Form.Item name="failedPart" label={t('maint.failedPart')}>
                  <Input maxLength={100} placeholder={t('maint.failedPartPlaceholder')} />
                </Form.Item>
                <Form.Item name="fixedBy" label={t('maint.fixedBy')}>
                  <Input maxLength={150} placeholder={t('maint.fixedByPlaceholder')} />
                </Form.Item>
              </div>
              <Form.Item name="cause" label={t('maint.cause')}>
                <Input.TextArea maxLength={500} autoSize={{ minRows: 1, maxRows: 3 }} />
              </Form.Item>
              <Form.Item name="actionTaken" label={t('maint.action')} rules={[{ ...required, whitespace: true }]}>
                <Input.TextArea maxLength={500} autoSize={{ minRows: 2, maxRows: 4 }} />
              </Form.Item>

              <div className="form-section">{t('maint.sparesUsed')} <span className="form-section-hint">· {t('maint.sparesHint')}</span></div>
              <Form.List name="spares">
                {(fields, { add, remove }) => (
                  <>
                    {fields.map((field, index) => {
                      const row = rows?.[index]
                      const unit = unitOf(row?.materialId)
                      const there = row?.materialId && row?.locationId ? stockOf.get(`${row.materialId}:${row.locationId}`) ?? 0 : undefined
                      return (
                        <div key={field.key} className="bd-spare-row">
                          <Form.Item name={[field.name, 'materialId']} rules={[required]}>
                            <Select options={spareOptions} loading={spares.isLoading} showSearch={{ optionFilterProp: 'label' }}
                              placeholder={t('maint.chooseSpare')} popupMatchSelectWidth={false} />
                          </Form.Item>
                          <Form.Item name={[field.name, 'locationId']} rules={[required]}
                            extra={there !== undefined && <span className={there < (row?.quantity ?? 0) ? 'short' : undefined}>{t('maint.inStore', { n: qty(there), unit: unit?.unit ?? '' })}</span>}>
                            <Select options={(godowns.data ?? []).map((g) => ({ value: g.id, label: g.name }))} placeholder={t('maint.godown')} />
                          </Form.Item>
                          <Form.Item name={[field.name, 'quantity']} rules={[required]}>
                            <InputNumber<number> min={0} precision={unit?.decimals ?? 3} suffix={unit?.unit} style={{ width: '100%' }}
                              placeholder={t('maint.qty')} inputMode="decimal" />
                          </Form.Item>
                          <Button type="text" danger icon={<MinusCircleOutlined />} aria-label={t('common.delete')} onClick={() => remove(field.name)} />
                        </div>
                      )
                    })}
                    <Button type="dashed" block icon={<PlusOutlined />}
                      onClick={() => add({ locationId: godowns.data?.length === 1 ? godowns.data[0].id : undefined })}>
                      {t('maint.addSpare')}
                    </Button>
                  </>
                )}
              </Form.List>

              <Button type="primary" size="large" block icon={<CheckOutlined />} loading={close.isPending} onClick={onRunning}
                className="bd-running">
                {t('maint.running')}
              </Button>
            </Form>
          )}

          <ReportBreakdownModal open={editing} plantId={b.plantId} breakdown={b} onClose={() => setEditing(false)} />
          <ReasonModal open={cancelling} title={t('maint.cancelTitle', { no: b.breakdownNo })}
            hint={b.status === 'CLOSED' ? t('maint.cancelClosedHint') : t('maint.cancelOpenHint')}
            okText={t('maint.cancel')} loading={cancel.isPending} onOk={onCancel} onClose={() => setCancelling(false)} />
        </>
      )}
    </Drawer>
  )
}
