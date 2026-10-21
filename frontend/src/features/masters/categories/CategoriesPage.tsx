import { useEffect, useState } from 'react'
import { App, Button, Card, Col, Drawer, Form, Input, InputNumber, Row, Space, Switch, Table, type TableColumnsType } from 'antd'
import { EditOutlined, PlusOutlined, TagsOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../../components/PageHeader'
import StatusTag from '../../../components/StatusTag'
import EmptyState from '../../../components/EmptyState'
import { FadeIn } from '../../../components/motion'
import { useDrawerSize } from '../../../components/useDrawerSize'
import { useAuth } from '../../../auth/AuthContext'
import { applyFieldErrors, errorMessage } from '../../../api/errors'
import { blankToUndefined, patterns, toUpper } from '../../../api/validation'
import type { ProductCategory, ProductCategoryRequest } from '../../../api/types'
import { useMasterList, useSaveMaster } from '../masterApi'

const HSN_PATTERN = /^[0-9]{4,8}$/

function CategoryFormDrawer({ open, category, onClose }: { open: boolean; category?: ProductCategory; onClose: () => void }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const drawerSize = useDrawerSize(480)
  const [form] = Form.useForm<ProductCategoryRequest>()
  const save = useSaveMaster<ProductCategory, ProductCategoryRequest>('categories')

  useEffect(() => {
    if (!open) return
    form.resetFields()
    // New categories start with the usual tile values: HSN 6907, GST 18%.
    form.setFieldsValue(category ?? { active: true, hsnCode: '6907', gstRate: 18 })
  }, [open, category, form])

  const onFinish = async (values: ProductCategoryRequest) => {
    try {
      await save.mutateAsync({ id: category?.id, body: blankToUndefined(values) })
      message.success(t('common.saved'))
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }

  return (
    <Drawer
      title={category ? t('category.edit') : t('category.new')}
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
        <Form.Item
          name="code"
          label={t('common.code')}
          normalize={toUpper}
          rules={[required, { pattern: patterns.code, message: t('company.invalidCode') }]}
        >
          <Input maxLength={20} placeholder="GVT" />
        </Form.Item>
        <Form.Item name="name" label={t('common.name')} rules={[required]}>
          <Input maxLength={80} placeholder="Glazed Vitrified Tiles" />
        </Form.Item>
        <Row gutter={16}>
          <Col xs={12}>
            <Form.Item name="hsnCode" label={t('category.hsn')} rules={[{ pattern: HSN_PATTERN, message: t('category.invalidHsn') }]}>
              <Input maxLength={8} inputMode="numeric" />
            </Form.Item>
          </Col>
          <Col xs={12}>
            <Form.Item name="gstRate" label={t('category.gst')} rules={[required]}>
              <InputNumber min={0} max={100} precision={2} suffix="%" inputMode="decimal" style={{ width: '100%' }} />
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

export default function CategoriesPage() {
  const { t } = useTranslation()
  const { can } = useAuth()
  const canManage = can('MASTER_MANAGE')
  const { data, isLoading } = useMasterList<ProductCategory>('categories')
  const [editing, setEditing] = useState<{ category?: ProductCategory } | null>(null)

  const newButton = canManage && (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({})}>
      {t('category.new')}
    </Button>
  )

  const columns: TableColumnsType<ProductCategory> = [
    { title: t('common.code'), dataIndex: 'code', width: 140, render: (code: string) => <span className="code-badge">{code}</span> },
    { title: t('common.name'), dataIndex: 'name', render: (name: string) => <span className="cell-title">{name}</span> },
    { title: t('category.hsn'), dataIndex: 'hsnCode', width: 120, responsive: ['sm'], render: (v?: string) => v ?? '-' },
    { title: t('category.gst'), dataIndex: 'gstRate', width: 100, align: 'right', render: (v: number) => `${Number(v)}%` },
    { title: t('common.status'), dataIndex: 'active', width: 120, responsive: ['md'], render: (a: boolean) => <StatusTag active={a} /> },
  ]
  if (canManage) {
    columns.push({
      key: 'actions',
      width: 56,
      render: (_, category) => (
        <Button type="text" icon={<EditOutlined />} aria-label={t('common.edit')} onClick={() => setEditing({ category })} />
      ),
    })
  }

  return (
    <>
      <PageHeader title={t('category.title')} subtitle={t('category.subtitle')} icon={<TagsOutlined />} extra={newButton} />
      <FadeIn delay={0.08}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<ProductCategory>
            rowKey="id"
            columns={columns}
            dataSource={data}
            loading={isLoading}
            pagination={false}
            locale={{ emptyText: isLoading ? ' ' : <EmptyState title={t('category.empty')} action={newButton} /> }}
            onRow={(category) => (canManage ? { onDoubleClick: () => setEditing({ category }) } : {})}
          />
        </Card>
      </FadeIn>
      <CategoryFormDrawer open={editing !== null} category={editing?.category} onClose={() => setEditing(null)} />
    </>
  )
}
