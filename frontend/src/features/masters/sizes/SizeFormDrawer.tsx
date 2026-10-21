import { useEffect } from 'react'
import { App, Button, Col, Drawer, Form, Input, InputNumber, Row, Space, Switch } from 'antd'
import { AnimatePresence, motion } from 'motion/react'
import { useTranslation } from 'react-i18next'
import { applyFieldErrors, errorMessage } from '../../../api/errors'
import type { TileSize, TileSizeRequest } from '../../../api/types'
import { blankToUndefined, patterns, toUpper } from '../../../api/validation'
import { useDrawerSize } from '../../../components/useDrawerSize'
import { useSaveSize } from './api'

interface Props {
  open: boolean
  size?: TileSize
  onClose: () => void
}

/** Same formula as TileSizeService.sqmPerBox - only a preview, the server's value is what gets saved. */
function previewSqm(l?: number, w?: number, pcs?: number): string | null {
  if (!l || !w || !pcs) return null
  return ((l * w * pcs) / 1_000_000).toFixed(4)
}

export default function SizeFormDrawer({ open, size, onClose }: Props) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const drawerSize = useDrawerSize()
  const [form] = Form.useForm<TileSizeRequest>()
  const save = useSaveSize()
  const isNew = !size

  const lengthMm = Form.useWatch('lengthMm', form)
  const widthMm = Form.useWatch('widthMm', form)
  const pcsPerBox = Form.useWatch('pcsPerBox', form)
  const sqm = previewSqm(lengthMm, widthMm, pcsPerBox)

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(size ?? { active: true, thicknessMm: 9 })
  }, [open, size, form])

  // New size: fill code "600X1200" and name "600 x 1200 mm" from the dimensions,
  // unless the user already typed their own.
  const onValuesChange = (changed: Partial<TileSizeRequest>, all: TileSizeRequest) => {
    if (!isNew || !('lengthMm' in changed || 'widthMm' in changed)) return
    if (!all.lengthMm || !all.widthMm) return
    if (!form.isFieldTouched('code')) form.setFieldValue('code', `${all.lengthMm}X${all.widthMm}`)
    if (!form.isFieldTouched('name')) form.setFieldValue('name', `${all.lengthMm} x ${all.widthMm} mm`)
  }

  const onFinish = async (values: TileSizeRequest) => {
    try {
      await save.mutateAsync({ id: size?.id, body: blankToUndefined(values) })
      message.success(t('common.saved'))
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }

  return (
    <Drawer
      title={isNew ? t('size.new') : t('size.edit')}
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
      <Form form={form} layout="vertical" onFinish={onFinish} onValuesChange={onValuesChange}>
        <Row gutter={16}>
          <Col xs={12}>
            <Form.Item name="lengthMm" label={t('size.length')} rules={[required]}>
              <InputNumber min={1} max={5000} precision={0} inputMode="numeric" style={{ width: '100%' }} suffix="mm" />
            </Form.Item>
          </Col>
          <Col xs={12}>
            <Form.Item name="widthMm" label={t('size.width')} rules={[required]}>
              <InputNumber min={1} max={5000} precision={0} inputMode="numeric" style={{ width: '100%' }} suffix="mm" />
            </Form.Item>
          </Col>
          <Col xs={12}>
            <Form.Item name="thicknessMm" label={t('size.thickness')} rules={[required]}>
              <InputNumber min={0.1} max={99.9} step={0.5} precision={1} inputMode="decimal" style={{ width: '100%' }} suffix="mm" />
            </Form.Item>
          </Col>
          <Col xs={12}>
            <Form.Item name="pcsPerBox" label={t('size.pcsPerBox')} rules={[required]}>
              <InputNumber min={1} max={100} precision={0} inputMode="numeric" style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>

        {/* Live result: re-animates every time the number changes. */}
        <div className="sqm-preview" aria-live="polite">
          <AnimatePresence mode="wait" initial={false}>
            <motion.div
              key={sqm ?? 'none'}
              initial={{ opacity: 0, y: 6 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -6 }}
              transition={{ duration: 0.2 }}
              className="sqm-preview-value"
            >
              {sqm ? t('size.sqmPreview', { sqm }) : '—'}
            </motion.div>
          </AnimatePresence>
          <div className="sqm-preview-hint">{t('size.sqmHint')}</div>
        </div>

        <Row gutter={16}>
          <Col xs={24} sm={10}>
            <Form.Item
              name="code"
              label={t('size.code')}
              tooltip={t('size.codeHint')}
              normalize={toUpper}
              rules={[required, { pattern: patterns.code, message: t('company.invalidCode') }]}
            >
              <Input maxLength={20} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={14}>
            <Form.Item name="name" label={t('size.name')} rules={[required]}>
              <Input maxLength={60} />
            </Form.Item>
          </Col>
          <Col xs={12}>
            <Form.Item name="boxWeightKg" label={t('size.boxWeight')}>
              <InputNumber min={0} max={9999.99} precision={2} inputMode="decimal" style={{ width: '100%' }} suffix="kg" />
            </Form.Item>
          </Col>
          <Col xs={12}>
            <Form.Item name="boxesPerPallet" label={t('size.boxesPerPallet')}>
              <InputNumber min={1} max={1000} precision={0} inputMode="numeric" style={{ width: '100%' }} />
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
