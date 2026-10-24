import { useEffect } from 'react'
import { App, Button, Col, Drawer, Form, Input, InputNumber, Row, Select, Space, Switch } from 'antd'
import { useTranslation } from 'react-i18next'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import { STATE_OPTIONS } from '../../api/indianStates'
import { GST_REGISTRATIONS, isCustomer, type Party, type PartyRequest, type PartyType } from '../../api/types'
import { blankToUndefined, patterns, toUpper } from '../../api/validation'
import { useDrawerSize } from '../../components/useDrawerSize'
import { useSaveParty } from './api'

const IFSC = /^[A-Za-z]{4}0[A-Za-z0-9]{6}$/

interface Props {
  open: boolean
  type: PartyType
  party?: Party
  onClose: () => void
}

/** Small heading between groups of fields. */
const Section = ({ children }: { children: string }) => <div className="form-section">{children}</div>

export default function PartyFormDrawer({ open, type, party, onClose }: Props) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const drawerSize = useDrawerSize(680)
  const [form] = Form.useForm<PartyRequest>()
  const save = useSaveParty()
  const registration = Form.useWatch('gstRegistration', form)
  const overseas = registration === 'OVERSEAS'
  const billed = isCustomer(type)     // dealers and jobwork principals: credit limit, no bank details
  const isTransporter = type === 'TRANSPORTER'

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(party ?? { partyType: type, gstRegistration: 'REGULAR', active: true })
  }, [open, party, type, form])

  // Typing a full GSTIN fills the state and PAN (same rule the server enforces).
  const onValuesChange = (changed: Partial<PartyRequest>) => {
    const gstin = changed.gstin
    if (gstin && patterns.gstin.test(gstin)) {
      form.setFieldsValue({ stateCode: gstin.slice(0, 2), pan: gstin.slice(2, 12).toUpperCase() })
    }
  }

  const onFinish = async (values: PartyRequest) => {
    try {
      const body = blankToUndefined({ ...values, partyType: type })
      await save.mutateAsync({ id: party?.id, body })
      message.success(t('common.saved'))
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }

  return (
    <Drawer
      title={party ? `${t(`party.edit.${type}`)} · ${party.code}` : t(`party.new.${type}`)}
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
        <Form.Item name="partyType" hidden>
          <Input />
        </Form.Item>

        <Section>{t('party.sectionBasic')}</Section>
        <Row gutter={16}>
          <Col xs={24} sm={16}>
            <Form.Item name="name" label={t('party.name')} rules={[required]}>
              <Input maxLength={150} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={8}>
            <Form.Item name="code" label={t('common.code')} tooltip={t('party.codeHint')} normalize={toUpper}
              rules={[{ pattern: patterns.code, message: t('company.invalidCode') }]}>
              <Input maxLength={20} placeholder={t('party.autoCode')} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="contactPerson" label={t('party.contactPerson')}>
              <Input maxLength={100} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="region" label={t('party.region')} tooltip={t('party.regionHint')}>
              <Input maxLength={60} />
            </Form.Item>
          </Col>
        </Row>

        <Section>{t('party.sectionGst')}</Section>
        <Row gutter={16}>
          <Col xs={24} sm={12}>
            <Form.Item name="gstRegistration" label={t('party.gstRegistration')} rules={[required]}>
              <Select options={GST_REGISTRATIONS.map((r) => ({ value: r, label: t(`gstReg.${r}`) }))} />
            </Form.Item>
          </Col>
          {!overseas && (
            <Col xs={24} sm={12}>
              <Form.Item
                name="gstin"
                label={isTransporter ? t('party.transporterId') : t('company.gstin')}
                normalize={toUpper}
                rules={[
                  { pattern: patterns.gstin, message: t('company.invalidGstin') },
                  { required: registration === 'REGULAR' || registration === 'COMPOSITION', message: t('common.required') },
                ]}
              >
                <Input maxLength={15} placeholder="24ABCDE1234F1Z5" />
              </Form.Item>
            </Col>
          )}
          {!overseas && (
            <Col xs={24} sm={12}>
              <Form.Item name="pan" label={t('company.pan')} normalize={toUpper}
                rules={[{ pattern: patterns.pan, message: t('company.invalidPan') }]}>
                <Input maxLength={10} />
              </Form.Item>
            </Col>
          )}
        </Row>

        <Section>{t('party.sectionAddress')}</Section>
        <Form.Item name="addressLine" label={t('company.address')}>
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
        <Row gutter={16}>
          <Col xs={24} sm={8}>
            <Form.Item name="city" label={t('company.city')}>
              <Input maxLength={100} />
            </Form.Item>
          </Col>
          {overseas ? (
            <Col xs={24} sm={16}>
              <Form.Item name="country" label={t('party.country')} rules={[required]}>
                <Input maxLength={60} placeholder="United Arab Emirates" />
              </Form.Item>
            </Col>
          ) : (
            <Col xs={24} sm={10}>
              <Form.Item name="stateCode" label={t('company.state')} tooltip={t('party.stateHint')}>
                <Select allowClear showSearch={{ optionFilterProp: 'label' }} options={STATE_OPTIONS} />
              </Form.Item>
            </Col>
          )}
          <Col xs={24} sm={overseas ? 8 : 6}>
            <Form.Item name="pincode" label={overseas ? t('party.postalCode') : t('company.pincode')}
              rules={overseas ? [] : [{ pattern: patterns.pincode, message: t('company.invalidPincode') }]}>
              <Input maxLength={10} inputMode={overseas ? 'text' : 'numeric'} />
            </Form.Item>
          </Col>
        </Row>

        <Section>{t('party.sectionContact')}</Section>
        <Row gutter={16}>
          <Col xs={24} sm={8}>
            <Form.Item name="mobile" label={t('party.mobile')} rules={[{ pattern: patterns.phone, message: t('company.invalidPhone') }]}>
              <Input inputMode="tel" maxLength={20} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={8}>
            <Form.Item name="phone" label={t('company.phone')} rules={[{ pattern: patterns.phone, message: t('company.invalidPhone') }]}>
              <Input inputMode="tel" maxLength={20} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={8}>
            <Form.Item name="email" label={t('company.email')} rules={[{ type: 'email', message: t('company.invalidEmail') }]}>
              <Input inputMode="email" maxLength={150} />
            </Form.Item>
          </Col>
        </Row>

        <Section>{t('party.sectionCommercial')}</Section>
        <Row gutter={16}>
          {billed && (
            <Col xs={24} sm={12}>
              <Form.Item name="creditLimit" label={t('party.creditLimit')} tooltip={t('party.creditLimitHint')}>
                {/* Shows 5,00,000 (Indian grouping) while typing; the form value stays a plain number. */}
                <InputNumber<number>
                  min={0}
                  precision={0}
                  prefix="₹"
                  style={{ width: '100%' }}
                  formatter={(v) => (v == null || `${v}` === '' ? '' : Number(v).toLocaleString('en-IN'))}
                  parser={(v) => {
                    const digits = (v ?? '').replace(/[^\d]/g, '')
                    // empty box = no limit (undefined), not 0
                    return (digits === '' ? undefined : Number(digits)) as number
                  }}
                />
              </Form.Item>
            </Col>
          )}
          <Col xs={24} sm={12}>
            <Form.Item name="creditDays" label={billed ? t('party.creditDays') : t('party.paymentDays')}>
              <InputNumber min={0} max={365} precision={0} suffix={t('party.days')} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>

        {!billed && (
          <>
            <Section>{t('party.sectionBank')}</Section>
            <Row gutter={16}>
              <Col xs={24} sm={8}>
                <Form.Item name="bankName" label={t('party.bankName')}>
                  <Input maxLength={100} />
                </Form.Item>
              </Col>
              <Col xs={24} sm={9}>
                <Form.Item name="bankAccountNo" label={t('party.bankAccountNo')}
                  rules={[{ pattern: /^[0-9A-Za-z]{6,30}$/, message: t('party.invalidAccount') }]}>
                  <Input maxLength={30} />
                </Form.Item>
              </Col>
              <Col xs={24} sm={7}>
                <Form.Item name="bankIfsc" label="IFSC" normalize={toUpper} rules={[{ pattern: IFSC, message: t('party.invalidIfsc') }]}>
                  <Input maxLength={11} placeholder="SBIN0001234" />
                </Form.Item>
              </Col>
            </Row>
          </>
        )}

        <Form.Item name="notes" label={t('party.notes')}>
          <Input.TextArea rows={2} maxLength={500} showCount />
        </Form.Item>
        <Form.Item name="active" label={t('common.status')} valuePropName="checked">
          <Switch checkedChildren={t('common.active')} unCheckedChildren={t('common.inactive')} />
        </Form.Item>
      </Form>
    </Drawer>
  )
}
