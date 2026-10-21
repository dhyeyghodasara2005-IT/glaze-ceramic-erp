import { useEffect } from 'react'
import { App, AutoComplete, Button, Col, DatePicker, Drawer, Form, Input, InputNumber, Row, Select, Space, Switch } from 'antd'
import { useTranslation } from 'react-i18next'
import dayjs, { type Dayjs } from 'dayjs'
import { applyFieldErrors, errorMessage } from '../../../api/errors'
import { MACHINE_TYPES, type Machine, type MachineRequest, type MachineType } from '../../../api/types'
import { patterns, toUpper } from '../../../api/validation'
import { useDrawerSize } from '../../../components/useDrawerSize'
import { useAuth } from '../../../auth/AuthContext'
import { useSaveMaster } from '../masterApi'

/** Common capacity units in a tile plant - suggestions only, any text is allowed. */
const CAPACITY_UNITS = ['sq m/day', 'boxes/day', 'tons/batch', 'tons/hour', 'strokes/min', 'kg/hour', 'litres']

interface FormValues {
  plantId: number
  code: string
  name: string
  type: MachineType
  capacity?: number
  capacityUnit?: string
  make?: string
  model?: string
  installedOn?: Dayjs
  active: boolean
}

export default function MachineFormDrawer({ open, machine, onClose }: { open: boolean; machine?: Machine; onClose: () => void }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const { user, currentPlant } = useAuth()
  const drawerSize = useDrawerSize(600)
  const [form] = Form.useForm<FormValues>()
  const save = useSaveMaster<Machine, MachineRequest>('machines')

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(
      machine
        ? { ...machine, installedOn: machine.installedOn ? dayjs(machine.installedOn) : undefined }
        : { active: true, plantId: currentPlant?.id }, // new machine: start in the plant chosen in the header
    )
  }, [open, machine, currentPlant, form])

  const onFinish = async (v: FormValues) => {
    try {
      await save.mutateAsync({
        id: machine?.id,
        body: {
          plantId: v.plantId,
          code: v.code,
          name: v.name.trim(),
          type: v.type,
          capacity: v.capacity ?? undefined,
          capacityUnit: v.capacityUnit?.trim() || undefined,
          make: v.make?.trim() || undefined,
          model: v.model?.trim() || undefined,
          installedOn: v.installedOn ? v.installedOn.format('YYYY-MM-DD') : undefined,
          active: v.active,
        },
      })
      message.success(t('common.saved'))
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }

  return (
    <Drawer
      title={machine ? t('machine.edit') : t('machine.new')}
      open={open}
      onClose={onClose}
      size={drawerSize}
      destroyOnHidden
      extra={
        <Space>
          <Button onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="primary" loading={save.isPending} onClick={() => form.submit()}>
            {t('common.save')}
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical" onFinish={onFinish}>
        <Row gutter={16}>
          <Col xs={24} sm={12}>
            <Form.Item name="plantId" label={t('common.plant')} rules={[required]}>
              <Select options={(user?.plants ?? []).map((p) => ({ value: p.id, label: `${p.code} · ${p.name}` }))} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="type" label={t('machine.type')} rules={[required]}>
              <Select
                showSearch={{ optionFilterProp: 'label' }}
                options={MACHINE_TYPES.map((type) => ({ value: type, label: t(`machineType.${type}`) }))}
              />
            </Form.Item>
          </Col>
          <Col xs={24} sm={10}>
            <Form.Item name="code" label={t('common.code')} normalize={toUpper}
              rules={[required, { pattern: patterns.code, message: t('company.invalidCode') }]}>
              <Input maxLength={20} placeholder="KILN-1" />
            </Form.Item>
          </Col>
          <Col xs={24} sm={14}>
            <Form.Item name="name" label={t('common.name')} rules={[required]}>
              <Input maxLength={120} placeholder="Roller kiln 1" />
            </Form.Item>
          </Col>
          <Col xs={12}>
            <Form.Item name="capacity" label={t('machine.capacity')}>
              <InputNumber min={0} precision={2} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={12}>
            <Form.Item name="capacityUnit" label={t('machine.capacityUnit')}>
              <AutoComplete options={CAPACITY_UNITS.map((u) => ({ value: u }))} maxLength={30} placeholder="sq m/day" />
            </Form.Item>
          </Col>
          <Col xs={12}>
            <Form.Item name="make" label={t('machine.make')}>
              <Input maxLength={80} placeholder="Sacmi" />
            </Form.Item>
          </Col>
          <Col xs={12}>
            <Form.Item name="model" label={t('machine.model')}>
              <Input maxLength={80} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="installedOn" label={t('machine.installedOn')}>
              <DatePicker format="DD MMM YYYY" style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="active" label={t('common.status')} valuePropName="checked">
          <Switch checkedChildren={t('common.active')} unCheckedChildren={t('common.inactive')} />
        </Form.Item>
      </Form>
    </Drawer>
  )
}
