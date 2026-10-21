import { useEffect } from 'react'
import { App, Button, Col, Drawer, Form, Input, InputNumber, Row, Select, Space, Switch } from 'antd'
import { useTranslation } from 'react-i18next'
import { useDrawerSize } from '../../components/useDrawerSize'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import type { Plant, PlantRequest } from '../../api/types'
import { blankToUndefined, patterns, toUpper } from '../../api/validation'
import { useAuth } from '../../auth/AuthContext'
import { useCompanies } from '../companies/api'
import { useSavePlant } from './api'

interface Props {
  open: boolean
  plant?: Plant
  onClose: () => void
}

export default function PlantFormDrawer({ open, plant, onClose }: Props) {
  const { t } = useTranslation()
  const drawerSize = useDrawerSize()
  const { message } = App.useApp()
  const { refresh } = useAuth()
  const [form] = Form.useForm<PlantRequest>()
  const save = useSavePlant()
  const companies = useCompanies(open)

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(plant ?? { active: true })
  }, [open, plant, form])

  const onFinish = async (values: PlantRequest) => {
    try {
      await save.mutateAsync({ id: plant?.id, body: blankToUndefined(values) })
      // The plant switcher in the header comes from /auth/me - reload it to show new plants.
      await refresh()
      message.success(t('common.saved'))
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }

  return (
    <Drawer
      title={plant ? t('plant.edit') : t('plant.new')}
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
        <Form.Item name="companyId" label={t('plant.company')} rules={[required]}>
          <Select
            loading={companies.isLoading}
            showSearch={{ optionFilterProp: 'label' }}
            options={companies.data?.map((c) => ({ value: c.id, label: `${c.code} - ${c.name}` }))}
          />
        </Form.Item>
        <Row gutter={16}>
          <Col xs={24} sm={8}>
            <Form.Item
              name="code"
              label={t('plant.code')}
              tooltip={t('plant.codeHint')}
              normalize={toUpper}
              rules={[required, { pattern: patterns.code, message: t('company.invalidCode') }]}
            >
              <Input maxLength={20} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={16}>
            <Form.Item name="name" label={t('plant.name')} rules={[required]}>
              <Input maxLength={150} />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={16}>
          <Col xs={24} sm={12}>
            <Form.Item
              name="gstin"
              label={t('plant.gstin')}
              tooltip={t('plant.gstinHint')}
              normalize={toUpper}
              rules={[{ pattern: patterns.gstin, message: t('company.invalidGstin') }]}
            >
              <Input maxLength={15} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="capacitySqmPerDay" label={t('plant.capacity')}>
              <InputNumber min={0} max={9_999_999_999} precision={2} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="addressLine" label={t('company.address')}>
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
        <Row gutter={16}>
          <Col xs={24} sm={8}>
            <Form.Item name="city" label={t('company.city')}>
              <Input maxLength={100} placeholder="Morbi" />
            </Form.Item>
          </Col>
          <Col xs={24} sm={8}>
            <Form.Item name="state" label={t('company.state')}>
              <Input maxLength={100} placeholder="Gujarat" />
            </Form.Item>
          </Col>
          <Col xs={24} sm={8}>
            <Form.Item
              name="pincode"
              label={t('company.pincode')}
              rules={[{ pattern: patterns.pincode, message: t('company.invalidPincode') }]}
            >
              <Input maxLength={6} inputMode="numeric" />
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
