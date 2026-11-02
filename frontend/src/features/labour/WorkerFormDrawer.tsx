import { useEffect } from 'react'
import { App, AutoComplete, Button, Col, DatePicker, Drawer, Form, Input, InputNumber, Row, Segmented, Select, Space, Switch } from 'antd'
import { useTranslation } from 'react-i18next'
import dayjs, { type Dayjs } from 'dayjs'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import { DEPARTMENTS, type Department, type PunchMethod, type Worker } from '../../api/types'
import { patterns, toUpper } from '../../api/validation'
import { useDrawerSize } from '../../components/useDrawerSize'
import { useContractors, useSaveWorker } from './labourApi'

interface Values {
  code?: string
  name: string
  contractorId?: number
  department: Department
  job?: string
  punch: PunchMethod
  deviceId?: string
  dayRate?: number
  otRate?: number
  phone?: string
  joinedOn?: Dayjs
  active: boolean
}

/** The usual jobs on a tile plant's floor, offered as the user types (anything else can be typed too). */
const JOBS = ['operator', 'helper', 'loader', 'fitter', 'electrician', 'driver', 'sorter', 'packer', 'cleaner', 'watchman']

/** Add a worker, or change one: where they work, their contractor, how they punch, what a day costs. */
export default function WorkerFormDrawer({ open, plantId, worker, onClose }: {
  open: boolean
  plantId: number
  worker?: Worker
  onClose: () => void
}) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const size = useDrawerSize(560)
  const [form] = Form.useForm<Values>()
  const save = useSaveWorker()
  const contractors = useContractors(open)
  const punch = Form.useWatch('punch', form) as PunchMethod | undefined

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(worker
      ? { ...worker, contractorId: worker.contractor?.id, joinedOn: worker.joinedOn ? dayjs(worker.joinedOn) : undefined }
      : { punch: 'CARD', active: true })
  }, [open, worker, form])

  const onFinish = async (v: Values) => {
    try {
      await save.mutateAsync({
        id: worker?.id,
        body: {
          plantId, code: v.code?.trim() || undefined, name: v.name.trim(), contractorId: v.contractorId,
          department: v.department, job: v.job?.trim() || undefined, punch: v.punch,
          deviceId: v.punch === 'FINGERPRINT' ? v.deviceId?.trim() || undefined : undefined,
          dayRate: v.dayRate, otRate: v.otRate, phone: v.phone?.trim() || undefined,
          joinedOn: v.joinedOn ? v.joinedOn.format('YYYY-MM-DD') : undefined, active: v.active,
        },
      })
      message.success(t('common.saved'))
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }
  // a contractor who was closed stays in the list for the workers who already have them
  const options = (contractors.data ?? []).map((c) => ({ value: c.id, label: `${c.name} · ${c.code}` }))
  if (worker?.contractor && !options.some((o) => o.value === worker.contractor!.id)) {
    options.push({ value: worker.contractor.id, label: `${worker.contractor.name} · ${worker.contractor.code}` })
  }
  return (
    <Drawer open={open} onClose={onClose} size={size} destroyOnHidden title={worker ? t('labour.editWorker') : t('labour.newWorker')}
      extra={
        <Space>
          <Button onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="primary" loading={save.isPending} onClick={() => form.submit()}>{t('common.save')}</Button>
        </Space>
      }>
      <Form<Values> form={form} layout="vertical" onFinish={onFinish}>
        <Row gutter={16}>
          <Col xs={24} sm={14}>
            <Form.Item name="name" label={t('common.name')} rules={[required, { whitespace: true, message: t('common.required') }]}>
              <Input maxLength={100} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={10}>
            <Form.Item name="code" label={t('common.code')} normalize={toUpper} extra={!worker && t('labour.codeHint')}
              rules={[...(worker ? [required] : []), { pattern: /^[A-Za-z0-9_/-]{1,20}$/, message: t('company.invalidCode') }]}>
              <Input maxLength={20} placeholder={worker ? undefined : t('party.autoCode')} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="department" label={t('labour.department')} rules={[required]}>
              <Select options={DEPARTMENTS.map((d) => ({ value: d, label: t(`labour.dept.${d}`) }))} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="job" label={t('labour.job')}>
              <AutoComplete maxLength={60} options={JOBS.map((j) => ({ value: t(`labour.jobs.${j}`) }))}
                filterOption={(input, o) => String(o?.value ?? '').toLowerCase().includes(input.toLowerCase())} />
            </Form.Item>
          </Col>
          <Col xs={24}>
            <Form.Item name="contractorId" label={t('labour.contractor')} extra={t('labour.contractorHint')}>
              <Select allowClear showSearch={{ optionFilterProp: 'label' }} placeholder={t('labour.ownRolls')}
                loading={contractors.isLoading} options={options}
                notFoundContent={t('labour.noContractors')} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="punch" label={t('labour.punchLabel')} rules={[required]} extra={t(`labour.punchHint.${punch ?? 'CARD'}`)}>
              <Segmented<PunchMethod> block options={[
                { value: 'CARD', label: t('labour.punch.CARD') },
                { value: 'FINGERPRINT', label: t('labour.punch.FINGERPRINT') },
              ]} />
            </Form.Item>
          </Col>
          {punch === 'FINGERPRINT' && (
            <Col xs={24} sm={12}>
              <Form.Item name="deviceId" label={t('labour.deviceId')} extra={t('labour.deviceIdHint')}>
                <Input maxLength={20} inputMode="numeric" />
              </Form.Item>
            </Col>
          )}
          <Col xs={12}>
            <Form.Item name="dayRate" label={t('labour.dayRate')}>
              <InputNumber<number> min={0} precision={2} prefix="₹" style={{ width: '100%' }} inputMode="decimal" />
            </Form.Item>
          </Col>
          <Col xs={12}>
            <Form.Item name="otRate" label={t('labour.otRate')}>
              <InputNumber<number> min={0} precision={2} prefix="₹" style={{ width: '100%' }} inputMode="decimal" />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="phone" label={t('company.phone')} rules={[{ pattern: patterns.phone, message: t('company.invalidPhone') }]}>
              <Input maxLength={20} inputMode="tel" />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="joinedOn" label={t('labour.joinedOn')}>
              <DatePicker format="DD MMM YYYY" style={{ width: '100%' }} disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
            </Form.Item>
          </Col>
        </Row>
        {worker && (
          <Form.Item name="active" label={t('common.status')} valuePropName="checked" extra={t('labour.activeHint')}>
            <Switch checkedChildren={t('common.active')} unCheckedChildren={t('labour.left')} />
          </Form.Item>
        )}
      </Form>
    </Drawer>
  )
}
