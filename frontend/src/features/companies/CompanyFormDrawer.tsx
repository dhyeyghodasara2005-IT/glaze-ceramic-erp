import { useEffect } from 'react'
import { App, Button, Col, Drawer, Form, Input, Row, Space, Switch } from 'antd'
import { useTranslation } from 'react-i18next'
import { useDrawerSize } from '../../components/useDrawerSize'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import type { Company, CompanyRequest } from '../../api/types'
import { blankToUndefined, patterns, toUpper } from '../../api/validation'
import { useSaveCompany } from './api'

interface Props {
  open: boolean
  /** undefined = creating a new company */
  company?: Company
  onClose: () => void
}

export default function CompanyFormDrawer({ open, company, onClose }: Props) {
  const { t } = useTranslation()
  const drawerSize = useDrawerSize()
  const { message } = App.useApp()
  const [form] = Form.useForm<CompanyRequest>()
  const save = useSaveCompany()

  // Fill the form each time the drawer opens.
  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(company ?? { active: true })
  }, [open, company, form])

  const onFinish = async (values: CompanyRequest) => {
    try {
      await save.mutateAsync({ id: company?.id, body: blankToUndefined(values) })
      message.success(t('common.saved'))
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }

  return (
    <Drawer
      title={company ? t('company.edit') : t('company.new')}
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
          <Col xs={24} sm={8}>
            <Form.Item
              name="code"
              label={t('company.code')}
              tooltip={t('company.codeHint')}
              normalize={toUpper}
              rules={[required, { pattern: patterns.code, message: t('company.invalidCode') }]}
            >
              <Input maxLength={20} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={16}>
            <Form.Item name="name" label={t('company.name')} rules={[required, { max: 150 }]}>
              <Input maxLength={150} />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="legalName" label={t('company.legalName')}>
          <Input maxLength={200} />
        </Form.Item>
        <Row gutter={16}>
          <Col xs={24} sm={12}>
            <Form.Item
              name="gstin"
              label={t('company.gstin')}
              normalize={toUpper}
              rules={[{ pattern: patterns.gstin, message: t('company.invalidGstin') }]}
            >
              <Input maxLength={15} placeholder="24ABCDE1234F1Z5" />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item
              name="pan"
              label={t('company.pan')}
              normalize={toUpper}
              rules={[{ pattern: patterns.pan, message: t('company.invalidPan') }]}
            >
              <Input maxLength={10} placeholder="ABCDE1234F" />
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
              <Input maxLength={6} inputMode="numeric" placeholder="363641" />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={16}>
          <Col xs={24} sm={12}>
            <Form.Item
              name="phone"
              label={t('company.phone')}
              rules={[{ pattern: patterns.phone, message: t('company.invalidPhone') }]}
            >
              <Input inputMode="tel" maxLength={20} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="email" label={t('company.email')} rules={[{ type: 'email', message: t('company.invalidEmail') }]}>
              <Input inputMode="email" maxLength={150} />
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
