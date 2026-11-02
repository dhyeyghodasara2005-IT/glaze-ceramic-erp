import { useEffect } from 'react'
import { App, Button, Col, DatePicker, Drawer, Form, Input, InputNumber, Row, Segmented, Select, Space } from 'antd'
import { useTranslation } from 'react-i18next'
import dayjs, { type Dayjs } from 'dayjs'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import { KIND_UNITS, METER_KINDS, type CostRate, type MeterKind, type MeterUnit } from '../../api/types'
import { useDrawerSize } from '../../components/useDrawerSize'
import { useUnit } from '../energy/energyParts'
import { useSaveCostRate } from './costingApi'

interface Values {
  kind: MeterKind
  unit: MeterUnit
  validFrom: Dayjs
  rate: number
  note?: string
}

/** What can be priced: running hours are not, the DG set's cost is its diesel. */
export const PRICED_KINDS = METER_KINDS.filter((k) => k !== 'RUN_HOURS')

/** A rate from a date. A price change is a new rate from that date, so the months before keep their cost. */
export default function CostRateDrawer({ open, plantId, rate, onClose }: {
  open: boolean
  plantId: number
  rate?: CostRate
  onClose: () => void
}) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const size = useDrawerSize(480)
  const unitName = useUnit()
  const [form] = Form.useForm<Values>()
  const save = useSaveCostRate()
  const kind = Form.useWatch('kind', form) as MeterKind | undefined
  const unit = Form.useWatch('unit', form) as MeterUnit | undefined

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(rate
      ? { kind: rate.kind, unit: rate.unit, validFrom: dayjs(rate.validFrom), rate: rate.rate, note: rate.note }
      : { kind: 'GAS', unit: KIND_UNITS.GAS[0], validFrom: dayjs().startOf('month') })
  }, [open, rate, form])

  const onFinish = async (v: Values) => {
    try {
      await save.mutateAsync({
        id: rate?.id,
        body: { plantId, kind: v.kind, unit: v.unit, validFrom: v.validFrom.format('YYYY-MM-DD'), rate: v.rate, note: v.note?.trim() || undefined },
      })
      message.success(t('common.saved'))
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }
  return (
    <Drawer open={open} onClose={onClose} size={size} destroyOnHidden title={rate ? t('costing.editRate') : t('costing.newRate')}
      extra={
        <Space>
          <Button onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="primary" loading={save.isPending} onClick={() => form.submit()}>{t('common.save')}</Button>
        </Space>
      }>
      <Form<Values> form={form} layout="vertical" onFinish={onFinish}>
        <Row gutter={16}>
          <Col xs={24} sm={14}>
            <Form.Item name="kind" label={t('costing.fuel')} rules={[required]}>
              <Select options={PRICED_KINDS.map((k) => ({ value: k, label: t(`energy.kinds.${k}`) }))}
                onChange={(k: MeterKind) => form.setFieldsValue({ unit: KIND_UNITS[k][0] })} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={10}>
            <Form.Item name="unit" label={t('costing.perUnit')} rules={[required]}>
              <Segmented<MeterUnit> block options={(kind ? KIND_UNITS[kind] : []).map((u) => ({ value: u, label: unitName(u) }))} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="rate" label={t('costing.rate')} rules={[required]}
              extra={kind === 'COAL' ? t('costing.coalHint') : undefined}>
              <InputNumber<number> min={0} precision={4} prefix="₹" suffix={unit ? `/ ${unitName(unit)}` : undefined}
                inputMode="decimal" style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="validFrom" label={t('costing.validFrom')} rules={[required]} extra={t('costing.validFromHint')}>
              <DatePicker format="DD MMM YYYY" allowClear={false} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={24}>
            <Form.Item name="note" label={t('costing.note')}>
              <Input maxLength={255} placeholder={t('costing.ratePlaceholder')} />
            </Form.Item>
          </Col>
        </Row>
      </Form>
    </Drawer>
  )
}
