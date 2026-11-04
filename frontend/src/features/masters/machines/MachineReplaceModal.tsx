import { useEffect } from 'react'
import { Alert, App, AutoComplete, Col, DatePicker, Form, Input, InputNumber, Modal, Row } from 'antd'
import { useTranslation } from 'react-i18next'
import dayjs, { type Dayjs } from 'dayjs'
import { applyFieldErrors, errorMessage } from '../../../api/errors'
import type { Machine } from '../../../api/types'
import { patterns, toUpper } from '../../../api/validation'
import { useReplaceMachine } from '../masterApi'

interface FormValues {
  code: string
  name: string
  capacity?: number
  capacityUnit?: string
  make?: string
  model?: string
  installedOn?: Dayjs
}

/**
 * Puts a new machine in an old one's place. The form starts from the old machine (same code, name and capacity),
 * so usually only the make, the model and the date need typing. The box says what moves and what stays.
 */
export default function MachineReplaceModal({ open, machine, onClose, onDone }: {
  open: boolean
  machine: Machine
  onClose: () => void
  /** Called after the replacement is saved (the drawer closes too: the old machine is now out of use). */
  onDone: () => void
}) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const [form] = Form.useForm<FormValues>()
  const replace = useReplaceMachine()
  const code = Form.useWatch('code', form) as string | undefined

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue({
      code: machine.code,
      name: machine.name,
      capacity: machine.capacity,
      capacityUnit: machine.capacityUnit,
      installedOn: dayjs(),
    })
  }, [open, machine, form])

  const onFinish = async (v: FormValues) => {
    try {
      const fresh = await replace.mutateAsync({
        id: machine.id,
        body: {
          code: v.code,
          name: v.name.trim(),
          capacity: v.capacity ?? undefined,
          capacityUnit: v.capacityUnit?.trim() || undefined,
          make: v.make?.trim() || undefined,
          model: v.model?.trim() || undefined,
          installedOn: v.installedOn ? v.installedOn.format('YYYY-MM-DD') : undefined,
        },
      })
      message.success(t('machine.replaceDone', { code: fresh.code }))
      onClose()
      onDone()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }
  const keepsCode = (code ?? '').trim().toUpperCase() === machine.code

  return (
    <Modal
      open={open}
      title={t('machine.replaceTitle', { code: machine.code })}
      okText={t('machine.replace')}
      cancelText={t('common.cancel')}
      onOk={() => form.submit()}
      onCancel={onClose}
      confirmLoading={replace.isPending}
      destroyOnHidden
    >
      <Alert
        className="doc-alert"
        type="info"
        showIcon
        title={t('machine.replaceMoves')}
        description={
          <>
            <div>{t('machine.replaceStays')}</div>
            {keepsCode && <div>{t('machine.replaceKeepsCode', { code: machine.code })}</div>}
          </>
        }
      />
      <Form form={form} layout="vertical" onFinish={onFinish}>
        <Row gutter={16}>
          <Col xs={24} sm={10}>
            <Form.Item name="code" label={t('common.code')} normalize={toUpper}
              rules={[required, { pattern: patterns.code, message: t('company.invalidCode') }]}>
              <Input maxLength={20} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={14}>
            <Form.Item name="name" label={t('common.name')} rules={[required]}>
              <Input maxLength={120} />
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
          <Col xs={12}>
            <Form.Item name="capacity" label={t('machine.capacity')}>
              <InputNumber min={0} precision={2} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={12}>
            <Form.Item name="capacityUnit" label={t('machine.capacityUnit')}>
              <AutoComplete maxLength={30} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="installedOn" label={t('machine.installedOn')}>
              <DatePicker format="DD MMM YYYY" style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>
      </Form>
    </Modal>
  )
}
