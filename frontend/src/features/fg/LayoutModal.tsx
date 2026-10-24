import { useEffect } from 'react'
import { App, Col, Form, Input, InputNumber, Modal, Row, Segmented, Select } from 'antd'
import { useTranslation } from 'react-i18next'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import { PILLAR_KINDS, PILLAR_ZONES, type PillarKind, type PillarZone } from '../../api/types'
import { useSetLayout } from './fgApi'

interface Values {
  prefix: string
  from: number
  to: number
  zone?: PillarZone
  kind?: PillarKind
  firstWalkOrder?: number
  step: 1 | -1
}

/**
 * Lay out a run of pillars at once. With the loading point in the middle, the left wing counts DOWN towards it
 * (A1 = 8 … A8 = 1) and the right wing counts UP away from it (A9 = 1 … A16 = 8).
 */
export default function LayoutModal({ open, godownId, onClose }: { open: boolean; godownId: number; onClose: () => void }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const [form] = Form.useForm<Values>()
  const setLayout = useSetLayout()
  const v = Form.useWatch([], form) as Partial<Values> | undefined

  useEffect(() => {
    if (open) {
      form.resetFields()
      form.setFieldsValue({ prefix: 'A', from: 1, to: 8, step: -1 })
    }
  }, [open, form])

  const onFinish = async (values: Values) => {
    try {
      await setLayout.mutateAsync({ godownId, body: { ...values, prefix: values.prefix.trim().toUpperCase() } })
      message.success(t('common.saved'))
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  // "A1 = 8 … A8 = 1": what the walk order will be, before saving
  const p = (v?.prefix ?? '').toUpperCase()
  const preview = p && v?.from && v?.to && v.to >= v.from && v.firstWalkOrder
    ? t('fg.layoutPreview', { first: `${p}${v.from}`, a: v.firstWalkOrder, last: `${p}${v.to}`, b: v.firstWalkOrder + (v.to - v.from) * (v.step ?? 1) })
    : undefined
  const required = { required: true, message: t('common.required') }

  return (
    <Modal open={open} title={t('fg.setLayout')} okText={t('common.save')} cancelText={t('common.cancel')}
      confirmLoading={setLayout.isPending} onOk={() => form.submit()} onCancel={onClose} destroyOnHidden>
      <p className="panel-hint" style={{ margin: '0 0 12px' }}>{t('fg.layoutHint')}</p>
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
          <Col xs={12}>
            <Form.Item name="zone" label={t('fg.zoneLabel')}>
              <Select allowClear placeholder={t('fg.unchanged')} options={PILLAR_ZONES.map((z) => ({ value: z, label: t(`fg.zone.${z}`) }))} />
            </Form.Item>
          </Col>
          <Col xs={12}>
            <Form.Item name="kind" label={t('fg.kindLabel')}>
              <Select allowClear placeholder={t('fg.unchanged')} options={PILLAR_KINDS.map((k) => ({ value: k, label: t(`fg.kind.${k}`) }))} />
            </Form.Item>
          </Col>
          <Col xs={12}>
            <Form.Item name="firstWalkOrder" label={t('fg.firstWalk')} tooltip={t('fg.walkOrderHint')}>
              <InputNumber<number> min={1} max={9999} precision={0} placeholder={t('fg.unchanged')} style={{ width: '100%' }} inputMode="numeric" />
            </Form.Item>
          </Col>
          <Col xs={12}>
            <Form.Item name="step" label={t('fg.direction')}>
              <Segmented<1 | -1> block options={[{ value: -1, label: t('fg.countsDown') }, { value: 1, label: t('fg.countsUp') }]} />
            </Form.Item>
          </Col>
        </Row>
      </Form>
      {preview && <div className="cell-sub">{preview}</div>}
    </Modal>
  )
}
