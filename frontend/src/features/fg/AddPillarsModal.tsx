import { useEffect } from 'react'
import { App, Col, Form, Input, InputNumber, Modal, Row } from 'antd'
import { useTranslation } from 'react-i18next'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import { useAddPillars } from './fgApi'

interface Values {
  prefix: string
  from: number
  to: number
  capacityPallets?: number
}

/** "Add pillars A1 to A20" in one go. Pillars that already exist are skipped. */
export default function AddPillarsModal({ open, godownId, onClose }: { open: boolean; godownId: number; onClose: () => void }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const [form] = Form.useForm<Values>()
  const add = useAddPillars()
  const prefix = Form.useWatch('prefix', form) as string | undefined
  const from = Form.useWatch('from', form) as number | undefined
  const to = Form.useWatch('to', form) as number | undefined

  useEffect(() => {
    if (open) {
      form.resetFields()
      form.setFieldsValue({ prefix: 'A', from: 1, to: 10 })
    }
  }, [open, form])

  const onFinish = async (v: Values) => {
    try {
      await add.mutateAsync({ godownId, body: { ...v, prefix: v.prefix.trim().toUpperCase() } })
      message.success(t('common.saved'))
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }
  const p = (prefix ?? '').toUpperCase()
  return (
    <Modal open={open} title={t('fg.addPillars')} okText={t('fg.addPillars')} cancelText={t('common.cancel')}
      confirmLoading={add.isPending} onOk={() => form.submit()} onCancel={onClose} destroyOnHidden>
      <p className="panel-hint" style={{ margin: '0 0 12px' }}>{t('fg.addPillarsHint')}</p>
      <Form<Values> form={form} layout="vertical" onFinish={onFinish}>
        <Row gutter={12}>
          <Col xs={8}>
            <Form.Item name="prefix" label={t('fg.rowLetter')} rules={[required, { pattern: /^[A-Za-z]{1,3}$/, message: t('fg.letterRule') }]}>
              <Input maxLength={3} />
            </Form.Item>
          </Col>
          <Col xs={8}>
            <Form.Item name="from" label={t('fg.fromNo')} rules={[required]}>
              <InputNumber<number> min={1} max={999} precision={0} style={{ width: '100%' }} inputMode="numeric" />
            </Form.Item>
          </Col>
          <Col xs={8}>
            <Form.Item name="to" label={t('fg.toNo')} rules={[required]}>
              <InputNumber<number> min={1} max={999} precision={0} style={{ width: '100%' }} inputMode="numeric" />
            </Form.Item>
          </Col>
          <Col xs={24}>
            <Form.Item name="capacityPallets" label={t('fg.capacity')} tooltip={t('fg.capacityHint')}>
              <InputNumber<number> min={1} max={9999} precision={0} suffix={t('fg.palletsShort')} style={{ width: '100%' }} inputMode="numeric" />
            </Form.Item>
          </Col>
        </Row>
      </Form>
      {p && from && to && to >= from && (
        <div className="cell-sub">{t('fg.willAdd', { first: `${p}${from}`, last: `${p}${to}`, n: to - from + 1 })}</div>
      )}
    </Modal>
  )
}
