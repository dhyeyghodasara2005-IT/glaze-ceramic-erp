import { useEffect } from 'react'
import { App, Button, DatePicker, Drawer, Form, Input, InputNumber, Segmented, Select, Space, Switch, Tag } from 'antd'
import { BulbOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import dayjs, { type Dayjs } from 'dayjs'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import { PM_KINDS, type PmKind, type PmTask } from '../../api/types'
import { useDrawerSize } from '../../components/useDrawerSize'
import { useCreatePmTasks, useMaintenanceMachines, useUpdatePmTask } from './maintenanceApi'
import { PM_SUGGESTIONS, type PmSuggestion } from './pmSuggestions'

interface Values {
  machineIds: number[]
  title: string
  kind?: PmKind
  everyDays?: number
  nextDue: Dayjs
  needsStop: boolean
  instructions?: string
  active: boolean
}

/** Quick picks for "every … days". */
const EVERY = [1, 7, 15, 30, 90, 182, 365]

/** Set up a preventive job (for one or more machines at once), or change one. */
export default function PmTaskFormDrawer({ open, plantId, task, onClose }: {
  open: boolean
  plantId: number
  task?: PmTask
  onClose: () => void
}) {
  const { t, i18n } = useTranslation()
  const { message } = App.useApp()
  const size = useDrawerSize(600)
  const [form] = Form.useForm<Values>()
  const machines = useMaintenanceMachines(open ? plantId : undefined)
  const create = useCreatePmTasks()
  const update = useUpdatePmTask()
  const chosen = Form.useWatch('machineIds', form) as number[] | undefined
  const every = Form.useWatch('everyDays', form) as number | undefined

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(task
      ? {
          machineIds: [task.machine.id], title: task.title, kind: task.kind, everyDays: task.everyDays, nextDue: dayjs(task.nextDue),
          needsStop: task.needsStop, instructions: task.instructions, active: task.active,
        }
      : { machineIds: [], nextDue: dayjs(), needsStop: false, active: true })
  }, [open, task, form])

  // grouped by kind of machine, in process order
  const groups = new Map<string, { value: number; label: string }[]>()
  for (const m of machines.data ?? []) {
    const label = t(`machineType.${m.type}`)
    groups.set(label, [...(groups.get(label) ?? []), { value: m.id, label: `${m.name} · ${m.code}` }])
  }
  const options = [...groups.entries()].map(([label, items]) => ({ label, options: items }))
  if (task && !(machines.data ?? []).some((m) => m.id === task.machine.id)) {
    options.push({ label: t(`machineType.${task.machine.type}`), options: [{ value: task.machine.id, label: task.machine.name }] })
  }
  const firstType = (machines.data ?? []).find((m) => m.id === chosen?.[0])?.type ?? task?.machine.type
  const suggestions = firstType ? PM_SUGGESTIONS[firstType] ?? [] : []
  const words = (s: PmSuggestion) => (i18n.language === 'gu' ? s.gu : s.en)

  const use = (s: PmSuggestion) =>
    form.setFieldsValue({ title: words(s), kind: s.kind, everyDays: s.everyDays, needsStop: s.needsStop })

  const onSave = async () => {
    let v: Values
    try {
      v = await form.validateFields()
    } catch {
      return
    }
    const body = {
      plantId,
      machineIds: v.machineIds,
      title: v.title.trim(),
      kind: v.kind!,
      everyDays: v.everyDays!,
      instructions: v.instructions?.trim() || undefined,
      needsStop: v.needsStop,
      nextDue: v.nextDue.format('YYYY-MM-DD'),
      active: v.active,
    }
    try {
      if (task) {
        await update.mutateAsync({ id: task.id, body })
        message.success(t('common.saved'))
      } else {
        const made = await create.mutateAsync(body)
        message.success(t('pm.createdN', { count: made.length }))
      }
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }
  return (
    <Drawer open={open} onClose={onClose} size={size} destroyOnHidden title={task ? t('pm.editTitle') : t('pm.new')}
      extra={<Button type="primary" loading={create.isPending || update.isPending} onClick={onSave}>{t('common.save')}</Button>}>
      <Form<Values> form={form} layout="vertical">
        <Form.Item name="machineIds" label={task ? t('maint.machine') : t('pm.machines')} rules={[required]}
          extra={!task && t('pm.machinesHint')}>
          <Select mode="multiple" maxCount={task ? 1 : undefined} options={options} loading={machines.isLoading}
            showSearch={{ optionFilterProp: 'label' }} placeholder={task ? t('maint.machine') : t('pm.chooseMachines')} />
        </Form.Item>

        {suggestions.length > 0 && !task && (
          <div className="pm-suggest">
            <div className="pm-suggest-head"><BulbOutlined /> {t('pm.suggestions', { type: t(`machineType.${firstType}`) })}</div>
            <div className="pm-suggest-chips">
              {suggestions.map((s) => (
                <Tag key={s.en} className="pm-chip" onClick={() => use(s)}>
                  {words(s)} <span>· {t('pm.everyShort', { count: s.everyDays })}</span>
                </Tag>
              ))}
            </div>
          </div>
        )}

        <Form.Item name="title" label={t('pm.title')} rules={[{ ...required, whitespace: true }]}>
          <Input maxLength={120} placeholder={t('pm.titlePlaceholder')} />
        </Form.Item>
        <div className="bd-close-grid">
          <Form.Item name="kind" label={t('pm.kind')} rules={[required]}>
            <Select options={PM_KINDS.map((k) => ({ value: k, label: t(`pm.kinds.${k}`) }))} />
          </Form.Item>
          <Form.Item name="nextDue" label={task ? t('pm.nextDue') : t('pm.firstDue')} rules={[required]}>
            <DatePicker format="DD-MM-YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </div>
        <Form.Item label={t('pm.every')} required>
          <Space wrap>
            <Form.Item name="everyDays" noStyle rules={[required]}>
              <InputNumber<number> min={1} max={3650} precision={0} suffix={t('pm.days')} style={{ width: 130 }} />
            </Form.Item>
            {/* -1 matches no preset, so none looks picked until the days match one */}
            <Segmented<number> size="small" value={EVERY.includes(every ?? -1) ? every : -1}
              onChange={(d) => form.setFieldValue('everyDays', d)}
              options={EVERY.map((d) => ({ value: d, label: t(`pm.preset.${d}`) }))} />
          </Space>
        </Form.Item>
        <Form.Item name="needsStop" label={t('pm.needsStop')} valuePropName="checked" extra={t('pm.needsStopHint')}>
          <Switch />
        </Form.Item>
        <Form.Item name="instructions" label={t('pm.instructions')}>
          <Input.TextArea maxLength={1000} autoSize={{ minRows: 3, maxRows: 8 }} placeholder={t('pm.instructionsPlaceholder')} />
        </Form.Item>
        {task && (
          <Form.Item name="active" label={t('pm.active')} valuePropName="checked" extra={t('pm.activeHint')}>
            <Switch />
          </Form.Item>
        )}
      </Form>
    </Drawer>
  )
}
