import { useEffect, useMemo } from 'react'
import { Alert, App, Col, DatePicker, Form, Input, InputNumber, Modal, Radio, Row, Select } from 'antd'
import { useTranslation } from 'react-i18next'
import dayjs, { type Dayjs } from 'dayjs'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import { CHECK_VERDICTS, type LotCheck, type LotCheckRequest } from '../../api/types'
import { useCurrentShift, useProdShifts } from './api'
import { useCreateLotCheck, useFinishingOverview } from './finishApi'
import { useSelectableLots } from './pressApi'

type Values = Omit<LotCheckRequest, 'plantId' | 'checkDate'> & { checkDate: Dayjs }

/**
 * "New quality check" for a lot in the cooling area. There is no draft: Save gives the number,
 * and a wrong check is cancelled and entered again.
 */
export default function QualityCheckModal({ open, plantId, lotId, onClose, onSaved }: {
  open: boolean
  plantId: number
  /** lot chosen on the card it was opened from */
  lotId?: number
  onClose: () => void
  onSaved?: (check: LotCheck) => void
}) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const [form] = Form.useForm<Values>()
  const create = useCreateLotCheck()
  const shifts = useProdShifts()
  const current = useCurrentShift()
  const finishing = useFinishingOverview(open ? plantId : undefined)
  const running = useSelectableLots(open ? plantId : undefined)
  const top = finishing.data?.topGradeName ?? ''

  // Lots in the cooling area first, then the ones running at the press (their first tiles may be cooling already).
  const lotOptions = useMemo(() => {
    const list = [...(finishing.data?.lots.map((l) => l.lot) ?? []), ...(running.data ?? [])]
    return list.filter((l, i) => list.findIndex((o) => o.id === l.id) === i)
      .map((l) => ({ value: l.id, label: `${l.lotNo} · ${l.designName} · ${l.pressCode}` }))
  }, [finishing.data, running.data])

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue({ lotId, samples: 5 })
  }, [open, lotId, form])

  useEffect(() => {
    if (!open || !current.data) return
    if (!form.isFieldTouched('checkDate')) form.setFieldValue('checkDate', dayjs(current.data.prodDate))
    if (!form.isFieldTouched('shiftId') && current.data.shiftId) form.setFieldValue('shiftId', current.data.shiftId)
  }, [open, current.data, form])

  const onFinish = async (v: Values) => {
    try {
      const saved = await create.mutateAsync({
        ...v,
        plantId,
        checkDate: v.checkDate.format('YYYY-MM-DD'),
        remarks: v.remarks?.trim() || undefined,
      })
      message.success(t('finishing.checkSaved', { no: saved.checkNo }))
      onSaved?.(saved)
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }
  const mm = (name: keyof Values, label: string, opts: { min?: number; max?: number } = {}) => (
    <Form.Item name={name} label={label}>
      <InputNumber<number> min={opts.min ?? -99} max={opts.max ?? 99} step={0.1} precision={2} suffix="mm"
        style={{ width: '100%' }} inputMode="decimal" />
    </Form.Item>
  )

  return (
    <Modal
      open={open}
      title={t('finishing.newCheck')}
      okText={t('common.save')}
      cancelText={t('common.cancel')}
      confirmLoading={create.isPending}
      onOk={() => form.submit()}
      onCancel={onClose}
      width={640}
      destroyOnHidden
    >
      <Alert type="info" showIcon className="doc-alert" title={t('finishing.checkFinalHint')} />
      <Form<Values> form={form} layout="vertical" onFinish={onFinish}>
        <Row gutter={12}>
          <Col xs={24}>
            <Form.Item name="lotId" label={t('press.lot')} rules={[required]}>
              <Select options={lotOptions} loading={finishing.isLoading} showSearch={{ optionFilterProp: 'label' }} />
            </Form.Item>
          </Col>
          <Col xs={12} sm={8}>
            <Form.Item name="checkDate" label={t('prod.prodDate')} rules={[required]}>
              <DatePicker format="DD-MM-YYYY" style={{ width: '100%' }} disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
            </Form.Item>
          </Col>
          <Col xs={12} sm={8}>
            <Form.Item name="shiftId" label={t('prod.shift')} rules={[required]}>
              <Select options={(shifts.data ?? []).map((s) => ({ value: s.id, label: `${s.code} · ${s.name}` }))} />
            </Form.Item>
          </Col>
          <Col xs={12} sm={8}>
            <Form.Item name="samples" label={t('finishing.samples')} rules={[required]}>
              <InputNumber<number> min={1} max={10000} precision={0} style={{ width: '100%' }} inputMode="numeric" />
            </Form.Item>
          </Col>
        </Row>

        <div className="form-section">{t('finishing.bend')} <span className="form-section-hint">· {t('finishing.bendHint')}</span></div>
        <Row gutter={12}>
          <Col xs={12} sm={8}>{mm('centreBendMm', t('finishing.centreBend'))}</Col>
          <Col xs={12} sm={8}>{mm('edgeBendMm', t('finishing.edgeBend'))}</Col>
          <Col xs={12} sm={8}>{mm('warpageMm', t('finishing.warpage'))}</Col>
        </Row>

        <div className="form-section">{t('finishing.size')}</div>
        <Row gutter={12}>
          <Col xs={12} sm={8}>{mm('lengthMm', t('finishing.length'), { min: 0, max: 99999 })}</Col>
          <Col xs={12} sm={8}>{mm('widthMm', t('finishing.width'), { min: 0, max: 99999 })}</Col>
          <Col xs={12} sm={8}>{mm('thicknessMm', t('finishing.thickness'), { min: 0, max: 999 })}</Col>
        </Row>

        <Row gutter={12}>
          <Col xs={24} sm={14}>
            <Form.Item name="verdict" label={t('finishing.verdictLabel')} rules={[required]}>
              <Radio.Group optionType="button" buttonStyle="solid" className="verdict-picker"
                options={CHECK_VERDICTS.map((v) => ({ value: v, label: t(`finishing.verdict.${v}`), title: t(`finishing.verdictHint.${v}`) }))} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={10}>
            <Form.Item name="expectedTopPct" label={t('finishing.expectedTop', { grade: top })}
              tooltip={t('finishing.expectedTopHint', { grade: top })}>
              <InputNumber<number> min={0} max={100} precision={1} suffix="%" style={{ width: '100%' }} inputMode="decimal" />
            </Form.Item>
          </Col>
          <Col xs={24}>
            <Form.Item name="remarks" label={t('store.remarks')}>
              <Input.TextArea maxLength={500} autoSize={{ minRows: 1, maxRows: 4 }} />
            </Form.Item>
          </Col>
        </Row>
      </Form>
    </Modal>
  )
}
