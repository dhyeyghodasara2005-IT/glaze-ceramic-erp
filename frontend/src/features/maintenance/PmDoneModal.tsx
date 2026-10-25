import { useEffect } from 'react'
import { App, DatePicker, Form, Input, InputNumber, Modal } from 'antd'
import { useTranslation } from 'react-i18next'
import dayjs, { type Dayjs } from 'dayjs'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import type { PmTaskSummary } from '../../api/types'
import { usePmDone } from './maintenanceApi'
import SparesField, { type SpareRow } from './SparesField'

interface Values {
  doneOn: Dayjs
  doneBy?: string
  minutes?: number
  stopMinutes?: number
  findings?: string
  spares: SpareRow[]
}

/** "Done": when, by whom, how long, what was found, and the spares (they leave the store). */
export default function PmDoneModal({ task, plantId, onClose }: { task?: PmTaskSummary; plantId: number; onClose: () => void }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const [form] = Form.useForm<Values>()
  const done = usePmDone()

  useEffect(() => {
    if (task) {
      form.resetFields()
      form.setFieldsValue({ doneOn: dayjs(), spares: [] })
    }
  }, [task, form])

  const onOk = async () => {
    let v: Values
    try {
      v = await form.validateFields()
    } catch {
      return
    }
    try {
      const saved = await done.mutateAsync({
        id: task!.id,
        body: {
          doneOn: v.doneOn.format('YYYY-MM-DD'),
          doneBy: v.doneBy?.trim() || undefined,
          minutes: v.minutes ?? undefined,
          stopMinutes: v.stopMinutes ?? undefined,
          findings: v.findings?.trim() || undefined,
          spares: (v.spares ?? []).map((s) => ({ materialId: s.materialId!, locationId: s.locationId!, quantity: s.quantity! })),
        },
      })
      message.success(t('pm.doneOk', { date: dayjs(saved.nextDue).format('D MMM') }))
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  return (
    <Modal open={task !== undefined} width={640} title={task && `${task.machine.name}: ${task.title}`} okText={t('pm.markDone')}
      cancelText={t('common.cancel')} confirmLoading={done.isPending} onOk={onOk} onCancel={onClose} destroyOnHidden>
      {task && (
        <Form<Values> form={form} layout="vertical">
          <div className="bd-close-grid">
            <Form.Item name="doneOn" label={t('pm.doneOn')} rules={[{ required: true, message: t('common.required') }]}>
              <DatePicker format="DD-MM-YYYY" style={{ width: '100%' }} disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
            </Form.Item>
            <Form.Item name="doneBy" label={t('maint.fixedBy')}>
              <Input maxLength={150} placeholder={t('maint.fixedByPlaceholder')} />
            </Form.Item>
            <Form.Item name="minutes" label={t('pm.minutes')}>
              <InputNumber<number> min={0} precision={0} suffix={t('maint.minutes')} style={{ width: '100%' }} inputMode="numeric" />
            </Form.Item>
            {task.needsStop && (
              <Form.Item name="stopMinutes" label={t('pm.stopMinutes')}>
                <InputNumber<number> min={0} precision={0} suffix={t('maint.minutes')} style={{ width: '100%' }} inputMode="numeric" />
              </Form.Item>
            )}
          </div>
          <Form.Item name="findings" label={t('pm.findings')}>
            <Input.TextArea maxLength={500} autoSize={{ minRows: 2, maxRows: 4 }} placeholder={t('pm.findingsPlaceholder')} />
          </Form.Item>
          <SparesField form={form} plantId={plantId} />
        </Form>
      )}
    </Modal>
  )
}
