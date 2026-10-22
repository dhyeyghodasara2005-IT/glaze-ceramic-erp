import { useEffect } from 'react'
import { Alert, App, Col, DatePicker, Form, Input, Modal, Row, Select } from 'antd'
import { useTranslation } from 'react-i18next'
import dayjs, { type Dayjs } from 'dayjs'
import { errorMessage } from '../../api/errors'
import type { LotOpenRequest, LotSummary } from '../../api/types'
import { useCurrentShift, useProdMachines, useProdShifts } from './api'
import { useDesignOptions, useOpenLot } from './pressApi'

type Values = Omit<LotOpenRequest, 'plantId' | 'openedDate' | 'closeCurrent'> & { openedDate: Dayjs }

/**
 * "Start a design" on a press line. If the press is running another lot, it says so and closes it
 * (closeCurrent = true) - changing the design is exactly that.
 */
export default function OpenLotModal({ open, plantId, pressId, running, onClose, onOpened }: {
  open: boolean
  plantId: number
  /** press chosen on the card it was opened from */
  pressId?: number
  /** the lots running now, by press id (to warn which one closes) */
  running: Map<number, LotSummary>
  onClose: () => void
  onOpened?: (lotId: number) => void
}) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const [form] = Form.useForm<Values>()
  const openLot = useOpenLot()
  const designs = useDesignOptions()
  const machines = useProdMachines(plantId)
  const shifts = useProdShifts()
  const current = useCurrentShift()
  const chosenPress = Form.useWatch('pressId', form) as number | undefined
  const willClose = chosenPress ? running.get(chosenPress) : undefined
  const presses = (machines.data ?? []).filter((m) => m.type === 'PRESS')

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue({
      pressId,
      openedDate: current.data ? dayjs(current.data.prodDate) : dayjs(),
      shiftId: current.data?.shiftId,
    })
  }, [open, pressId, current.data, form])

  const onFinish = async (v: Values) => {
    try {
      const lot = await openLot.mutateAsync({
        ...v,
        plantId,
        openedDate: v.openedDate.format('YYYY-MM-DD'),
        closeCurrent: true,   // the warning below told the supervisor which lot closes
      })
      message.success(t('press.startedOk', { no: lot.lotNo }))
      onClose()
      onOpened?.(lot.id)
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }

  return (
    <Modal
      open={open}
      title={t('press.startDesign')}
      okText={t('press.startDesign')}
      cancelText={t('common.cancel')}
      confirmLoading={openLot.isPending}
      onOk={() => form.submit()}
      onCancel={onClose}
      destroyOnHidden
    >
      <p className="panel-hint" style={{ margin: '0 0 14px' }}>{t('press.openHint')}</p>
      <Form form={form} layout="vertical" onFinish={onFinish}>
        <Form.Item name="designId" label={t('press.design')} rules={[required]}>
          <Select showSearch={{ optionFilterProp: 'label' }} loading={designs.isLoading}
            options={(designs.data ?? []).map((d) => ({ value: d.id, label: `${d.code} · ${d.name} · ${d.sizeCode} ${d.finishName}` }))} />
        </Form.Item>
        <Form.Item name="pressId" label={t('press.press')} rules={[required]}>
          <Select loading={machines.isLoading}
            options={presses.map((p) => ({ value: p.id, label: `${p.code} · ${p.name}` }))} />
        </Form.Item>
        {willClose && (
          <Alert type="warning" showIcon style={{ marginBottom: 16 }}
            title={t('press.willClose', { press: willClose.pressCode, lot: willClose.lotNo, design: willClose.designName })} />
        )}
        <Row gutter={12}>
          <Col xs={12}>
            <Form.Item name="openedDate" label={t('prod.prodDate')} rules={[required]}>
              <DatePicker format="DD-MM-YYYY" style={{ width: '100%' }} disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
            </Form.Item>
          </Col>
          <Col xs={12}>
            <Form.Item name="shiftId" label={t('prod.shift')} rules={[required]}>
              <Select options={(shifts.data ?? []).map((s) => ({ value: s.id, label: `${s.code} · ${s.name}` }))} />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="remarks" label={t('store.remarks')}>
          <Input maxLength={500} />
        </Form.Item>
      </Form>
    </Modal>
  )
}
