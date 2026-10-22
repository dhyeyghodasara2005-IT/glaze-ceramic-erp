import { useEffect } from 'react'
import { App, Col, DatePicker, Form, InputNumber, Modal, Row, Select } from 'antd'
import { useTranslation } from 'react-i18next'
import dayjs, { type Dayjs } from 'dayjs'
import { errorMessage } from '../../api/errors'
import type { BodyBatchSummary, DischargeRequest } from '../../api/types'
import { useDischargeBatch, useProdMachines } from './api'
import { DATE_TIME, duration, toLocalDateTime } from './time'

type Values = Omit<DischargeRequest, 'millingEnd'> & { millingEnd: Dayjs }

/**
 * Milling finished: when, which tank, and the slip test. Opened from the body preparation home
 * and from the batch page. Sending it again later corrects the readings.
 */
export default function DischargeModal({ batch, plantId, onClose }: {
  batch: BodyBatchSummary | null
  plantId: number
  onClose: () => void
}) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const [form] = Form.useForm<Values>()
  const discharge = useDischargeBatch()
  const machines = useProdMachines(plantId)
  const tanks = (machines.data ?? []).filter((m) => m.type === 'SLIP_TANK')
  const end = Form.useWatch('millingEnd', form)

  useEffect(() => {
    if (!batch) return
    form.resetFields()
    form.setFieldsValue({
      millingEnd: batch.millingEnd ? dayjs(batch.millingEnd) : dayjs(),
      slipTankId: tanks.find((tk) => tk.code === batch.slipTankCode)?.id,
      slipDensity: batch.slipDensity,
      slipViscosity: batch.slipViscosity,
      slipResidue: batch.slipResidue,
    })
    // tanks are only used to pre-select the tank already chosen; no need to reset when they load
  }, [batch, form])

  const onFinish = async (v: Values) => {
    try {
      const saved = await discharge.mutateAsync({ id: batch!.id, body: { ...v, millingEnd: toLocalDateTime(v.millingEnd) } })
      message.success(t('prod.dischargedOk', { no: saved.batchNo }))
      onClose()
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }

  return (
    <Modal
      open={batch !== null}
      title={batch ? t('prod.dischargeTitle', { no: batch.batchNo }) : ''}
      okText={batch?.status === 'DISCHARGED' ? t('common.save') : t('prod.discharge')}
      cancelText={t('common.cancel')}
      confirmLoading={discharge.isPending}
      onOk={() => form.submit()}
      onCancel={onClose}
      destroyOnHidden
    >
      {batch && (
        <p className="panel-hint" style={{ margin: '0 0 14px' }}>
          {batch.ballMillCode} · {t('prod.milled', { time: duration(batch.millingStart, end ? toLocalDateTime(end) : undefined) })}
        </p>
      )}
      <Form form={form} layout="vertical" onFinish={onFinish}>
        <Row gutter={12}>
          <Col xs={24} sm={12}>
            <Form.Item name="millingEnd" label={t('prod.millingEnd')} rules={[required]}>
              <DatePicker showTime={{ format: 'HH:mm' }} format={DATE_TIME} style={{ width: '100%' }} needConfirm={false}
                disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="slipTankId" label={t('prod.slipTank')} rules={[required]}>
              <Select options={tanks.map((tk) => ({ value: tk.id, label: `${tk.code} · ${tk.name}` }))} loading={machines.isLoading} />
            </Form.Item>
          </Col>
          <Col xs={8}>
            <Form.Item name="slipDensity" label={t('prod.density')} rules={[required]}>
              <InputNumber<number> min={1000} max={2500} step={1} suffix="g/L" style={{ width: '100%' }} inputMode="decimal" />
            </Form.Item>
          </Col>
          <Col xs={8}>
            <Form.Item name="slipViscosity" label={t('prod.viscosity')}>
              <InputNumber<number> min={0} max={999} suffix="s" style={{ width: '100%' }} inputMode="decimal" />
            </Form.Item>
          </Col>
          <Col xs={8}>
            <Form.Item name="slipResidue" label={t('prod.residue')} rules={[required]}>
              <InputNumber<number> min={0} max={100} step={0.1} suffix="%" style={{ width: '100%' }} inputMode="decimal" />
            </Form.Item>
          </Col>
        </Row>
      </Form>
    </Modal>
  )
}
