import { useEffect } from 'react'
import { App, Button, Col, Drawer, Form, Input, InputNumber, Row, Select, Space, Switch } from 'antd'
import { useTranslation } from 'react-i18next'
import { applyFieldErrors, errorMessage } from '../../../api/errors'
import type { Material, MaterialGroup, MaterialRequest, UnitOfMeasure } from '../../../api/types'
import { blankToUndefined, toUpper } from '../../../api/validation'
import { useDrawerSize } from '../../../components/useDrawerSize'
import { useMasterList } from '../masterApi'
import { useSaveMaterial } from './api'

const MATERIAL_CODE = /^[A-Za-z0-9_-]{2,30}$/
const HSN = /^[0-9]{4,8}$/

export default function MaterialFormDrawer({ open, material, onClose }: { open: boolean; material?: Material; onClose: () => void }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const drawerSize = useDrawerSize(600)
  const [form] = Form.useForm<MaterialRequest>()
  const save = useSaveMaterial()
  const groups = useMasterList<MaterialGroup>('material-groups', open)
  const units = useMasterList<UnitOfMeasure>('units', open)

  // The reorder level input follows the chosen unit: "BOX" -> whole numbers, "KG" -> 3 decimals.
  const unitId = Form.useWatch('unitId', form)
  const unit = units.data?.find((u) => u.id === unitId)

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(material ?? { active: true })
  }, [open, material, form])

  /** Active items, plus the one already used (same rule as the server). */
  function options<T extends { id: number; code: string; name: string; active: boolean }>(list: T[] | undefined, currentId?: number) {
    return (list ?? [])
      .filter((i) => i.active || i.id === currentId)
      .map((i) => ({ value: i.id, label: `${i.code} · ${i.name}` }))
  }

  const onFinish = async (values: MaterialRequest) => {
    try {
      await save.mutateAsync({ id: material?.id, body: blankToUndefined(values) })
      message.success(t('common.saved'))
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }
  const searchable = { optionFilterProp: 'label' }

  return (
    <Drawer
      title={material ? t('material.edit') : t('material.new')}
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
          <Col xs={24} sm={10}>
            <Form.Item name="code" label={t('common.code')} normalize={toUpper}
              rules={[required, { pattern: MATERIAL_CODE, message: t('design.invalidCode') }]}>
              <Input maxLength={30} placeholder="FELDSPAR-NA" />
            </Form.Item>
          </Col>
          <Col xs={24} sm={14}>
            <Form.Item name="name" label={t('common.name')} rules={[required]}>
              <Input maxLength={120} placeholder="Sodium feldspar" />
            </Form.Item>
          </Col>
          <Col xs={24} sm={14}>
            <Form.Item name="groupId" label={t('material.group')} rules={[required]}>
              <Select showSearch={searchable} loading={groups.isLoading} options={options(groups.data, material?.groupId)} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={10}>
            <Form.Item name="unitId" label={t('material.unit')} rules={[required]}>
              <Select showSearch={searchable} loading={units.isLoading} options={options(units.data, material?.unitId)} />
            </Form.Item>
          </Col>
          <Col xs={12} sm={8}>
            <Form.Item name="hsnCode" label={t('category.hsn')} rules={[{ pattern: HSN, message: t('category.invalidHsn') }]}>
              <Input maxLength={8} inputMode="numeric" />
            </Form.Item>
          </Col>
          <Col xs={12} sm={6}>
            <Form.Item name="gstRate" label={t('category.gst')}>
              <InputNumber min={0} max={100} precision={2} suffix="%" style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={10}>
            <Form.Item name="reorderLevel" label={t('material.reorderLevel')} tooltip={t('material.reorderHint')}>
              <InputNumber min={0} precision={unit?.decimals ?? 3} suffix={unit?.code} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="description" label={t('design.description')}>
          <Input.TextArea rows={2} maxLength={255} showCount />
        </Form.Item>
        <Form.Item name="active" label={t('common.status')} valuePropName="checked">
          <Switch checkedChildren={t('common.active')} unCheckedChildren={t('common.inactive')} />
        </Form.Item>
      </Form>
    </Drawer>
  )
}
