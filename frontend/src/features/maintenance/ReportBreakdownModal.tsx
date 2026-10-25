import { useEffect } from 'react'
import { App, DatePicker, Form, Input, Modal, Select } from 'antd'
import { useTranslation } from 'react-i18next'
import dayjs, { type Dayjs } from 'dayjs'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import type { Breakdown } from '../../api/types'
import { DATE_TIME, toLocalDateTime } from '../production/time'
import { useMaintenanceMachines, useReportBreakdown } from './maintenanceApi'

interface Values {
  machineId?: number
  stoppedAt: Dayjs
  symptom: string
}

/**
 * "This machine has stopped" - a supervisor's whole report: the machine, when, and what they saw. Opened with a
 * breakdown, it corrects an open report instead.
 */
export default function ReportBreakdownModal({ open, plantId, breakdown, machineId, onClose, onSaved }: {
  open: boolean
  plantId: number
  breakdown?: Breakdown
  /** machine chosen on the card it was opened from */
  machineId?: number
  onClose: () => void
  onSaved?: (b: Breakdown) => void
}) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const [form] = Form.useForm<Values>()
  const machines = useMaintenanceMachines(open ? plantId : undefined)
  const save = useReportBreakdown()

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(breakdown
      ? { machineId: breakdown.machine.id, stoppedAt: dayjs(breakdown.stoppedAt), symptom: breakdown.symptom }
      : { machineId, stoppedAt: dayjs() })
  }, [open, breakdown, machineId, form])

  // machines grouped by kind, in process order (the list comes sorted that way)
  const groups = new Map<string, { value: number; label: string }[]>()
  for (const m of machines.data ?? []) {
    const label = t(`machineType.${m.type}`)
    groups.set(label, [...(groups.get(label) ?? []), { value: m.id, label: `${m.name} · ${m.code}` }])
  }
  const options = [...groups.entries()].map(([label, items]) => ({ label, options: items }))
  if (breakdown && !(machines.data ?? []).some((m) => m.id === breakdown.machine.id)) {
    options.push({ label: t(`machineType.${breakdown.machine.type}`), options: [{ value: breakdown.machine.id, label: breakdown.machine.name }] })
  }

  const onOk = async () => {
    let v: Values
    try {
      v = await form.validateFields()
    } catch {
      return
    }
    try {
      const saved = await save.mutateAsync({
        id: breakdown?.id,
        body: { plantId, machineId: v.machineId!, stoppedAt: toLocalDateTime(v.stoppedAt), symptom: v.symptom.trim() },
      })
      message.success(breakdown ? t('common.saved') : t('maint.reportedOk', { no: saved.breakdownNo }))
      onSaved?.(saved)
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }
  return (
    <Modal open={open} title={breakdown ? t('maint.editTitle', { no: breakdown.breakdownNo }) : t('maint.reportTitle')}
      okText={breakdown ? t('common.save') : t('maint.report')} okButtonProps={{ danger: !breakdown }} cancelText={t('common.cancel')}
      confirmLoading={save.isPending} onOk={onOk} onCancel={onClose} destroyOnHidden>
      {!breakdown && <p className="panel-hint">{t('maint.reportHint')}</p>}
      <Form<Values> form={form} layout="vertical">
        <Form.Item name="machineId" label={t('maint.machine')} rules={[required]}>
          <Select options={options} loading={machines.isLoading} showSearch={{ optionFilterProp: 'label' }} size="large"
            placeholder={t('maint.chooseMachine')} />
        </Form.Item>
        <Form.Item name="stoppedAt" label={t('maint.stoppedAt')} rules={[required]}>
          <DatePicker showTime={{ format: 'HH:mm' }} format={DATE_TIME} style={{ width: '100%' }} needConfirm={false}
            disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
        </Form.Item>
        <Form.Item name="symptom" label={t('maint.symptom')} rules={[{ ...required, whitespace: true }]}>
          <Input.TextArea maxLength={255} autoSize={{ minRows: 2, maxRows: 4 }} placeholder={t('maint.symptomPlaceholder')} />
        </Form.Item>
      </Form>
    </Modal>
  )
}
