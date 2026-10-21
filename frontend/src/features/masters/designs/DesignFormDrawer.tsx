import { useEffect } from 'react'
import { App, Button, Col, DatePicker, Drawer, Form, Input, InputNumber, Row, Select, Space, Switch } from 'antd'
import { useTranslation } from 'react-i18next'
import dayjs, { type Dayjs } from 'dayjs'
import { applyFieldErrors, errorMessage } from '../../../api/errors'
import type { Design, Finish, ProductCategory, TileSize } from '../../../api/types'
import { toUpper } from '../../../api/validation'
import { useDrawerSize } from '../../../components/useDrawerSize'
import { useMasterList } from '../masterApi'
import { useSaveDesign } from './api'

const DESIGN_CODE = /^[A-Za-z0-9_-]{2,30}$/

/** The form holds a Dayjs date object; the API wants "2026-10-22". */
interface FormValues {
  code: string
  name: string
  categoryId: number
  sizeId: number
  finishId: number
  faces: number
  launchDate?: Dayjs
  description?: string
  active: boolean
}

interface Props {
  open: boolean
  design?: Design
  onClose: () => void
}

export default function DesignFormDrawer({ open, design, onClose }: Props) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const drawerSize = useDrawerSize(600)
  const [form] = Form.useForm<FormValues>()
  const save = useSaveDesign()
  const categories = useMasterList<ProductCategory>('categories', open)
  const sizes = useMasterList<TileSize>('sizes', open)
  const finishes = useMasterList<Finish>('finishes', open)

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(
      design
        ? { ...design, launchDate: design.launchDate ? dayjs(design.launchDate) : undefined }
        : { active: true, faces: 1 },
    )
  }, [open, design, form])

  /**
   * Dropdown options: only active items, plus the one this design already uses
   * (so an old design still shows its retired size). Mirrors the rule in DesignService.
   */
  function options<T extends { id: number; code: string; name: string; active: boolean }>(
    list: T[] | undefined,
    currentId: number | undefined,
    label: (item: T) => string,
  ) {
    return (list ?? [])
      .filter((i) => i.active || i.id === currentId)
      .map((i) => ({ value: i.id, label: i.active ? label(i) : `${label(i)} ${t('design.inactiveOption')}` }))
  }

  const onFinish = async (v: FormValues) => {
    try {
      await save.mutateAsync({
        id: design?.id,
        body: {
          code: v.code,
          name: v.name.trim(),
          categoryId: v.categoryId,
          sizeId: v.sizeId,
          finishId: v.finishId,
          faces: v.faces,
          launchDate: v.launchDate ? v.launchDate.format('YYYY-MM-DD') : undefined,
          description: v.description?.trim() || undefined,
          active: v.active,
        },
      })
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
      title={design ? t('design.edit') : t('design.new')}
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
            <Form.Item
              name="code"
              label={t('design.code')}
              normalize={toUpper}
              rules={[required, { pattern: DESIGN_CODE, message: t('design.invalidCode') }]}
            >
              <Input maxLength={30} placeholder="MARBLE-01" />
            </Form.Item>
          </Col>
          <Col xs={24} sm={14}>
            <Form.Item name="name" label={t('design.name')} rules={[required]}>
              <Input maxLength={120} placeholder="Statuario Marble" />
            </Form.Item>
          </Col>
        </Row>

        <Form.Item name="categoryId" label={t('design.category')} rules={[required]}>
          <Select
            showSearch={searchable}
            loading={categories.isLoading}
            options={options(categories.data, design?.categoryId, (c) => `${c.code} · ${c.name}`)}
          />
        </Form.Item>
        <Row gutter={16}>
          <Col xs={24} sm={12}>
            <Form.Item name="sizeId" label={t('design.size')} rules={[required]}>
              <Select
                showSearch={searchable}
                loading={sizes.isLoading}
                options={options(sizes.data, design?.sizeId, (s) => `${s.code} · ${Number(s.sqmPerBox).toFixed(2)} sq m`)}
              />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="finishId" label={t('design.finish')} rules={[required]}>
              <Select
                showSearch={searchable}
                loading={finishes.isLoading}
                options={options(finishes.data, design?.finishId, (f) => f.name)}
              />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col xs={12}>
            <Form.Item name="faces" label={t('design.faces')} tooltip={t('design.facesHint')} rules={[required]}>
              <InputNumber min={1} max={99} precision={0} inputMode="numeric" style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={12}>
            <Form.Item name="launchDate" label={t('design.launchDate')}>
              <DatePicker format="DD MMM YYYY" style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>

        <Form.Item name="description" label={t('design.description')}>
          <Input.TextArea rows={3} maxLength={255} showCount />
        </Form.Item>
        <Form.Item name="active" label={t('common.status')} valuePropName="checked">
          <Switch checkedChildren={t('common.active')} unCheckedChildren={t('common.inactive')} />
        </Form.Item>
      </Form>
    </Drawer>
  )
}
