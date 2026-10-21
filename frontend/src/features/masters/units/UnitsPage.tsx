import { useCallback, useEffect, useState } from 'react'
import { App, Button, Card, Col, Drawer, Form, Input, InputNumber, Row, Space, Switch, Table, type TableColumnsType } from 'antd'
import { EditOutlined, NumberOutlined, PlusOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../../components/PageHeader'
import StatusTag from '../../../components/StatusTag'
import EmptyState from '../../../components/EmptyState'
import { FadeIn } from '../../../components/motion'
import { useDrawerSize } from '../../../components/useDrawerSize'
import { useOpenFromUrl } from '../../../components/useOpenFromUrl'
import { useAuth } from '../../../auth/AuthContext'
import { applyFieldErrors, errorMessage } from '../../../api/errors'
import { blankToUndefined, toUpper } from '../../../api/validation'
import type { UnitOfMeasure, UnitOfMeasureRequest } from '../../../api/types'
import { useMasterList, useSaveMaster } from '../masterApi'

const UNIT_CODE = /^[A-Za-z0-9_-]{1,10}$/

function UnitFormDrawer({ open, unit, onClose }: { open: boolean; unit?: UnitOfMeasure; onClose: () => void }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const drawerSize = useDrawerSize(440)
  const [form] = Form.useForm<UnitOfMeasureRequest>()
  const save = useSaveMaster<UnitOfMeasure, UnitOfMeasureRequest>('units')

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(unit ?? { active: true, decimals: 2 })
  }, [open, unit, form])

  const onFinish = async (values: UnitOfMeasureRequest) => {
    try {
      await save.mutateAsync({ id: unit?.id, body: blankToUndefined(values) })
      message.success(t('common.saved'))
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }

  return (
    <Drawer
      title={unit ? t('unit.edit') : t('unit.new')}
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
          <Col xs={10}>
            <Form.Item name="code" label={t('common.code')} normalize={toUpper}
              rules={[required, { pattern: UNIT_CODE, message: t('unit.invalidCode') }]}>
              <Input maxLength={10} placeholder="KG" />
            </Form.Item>
          </Col>
          <Col xs={14}>
            <Form.Item name="name" label={t('common.name')} rules={[required]}>
              <Input maxLength={40} placeholder="Kilogram" />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="decimals" label={t('unit.decimals')} extra={t('unit.decimalsHint')} rules={[required]}>
          <InputNumber min={0} max={4} precision={0} style={{ width: 120 }} />
        </Form.Item>
        <Form.Item name="active" label={t('common.status')} valuePropName="checked">
          <Switch checkedChildren={t('common.active')} unCheckedChildren={t('common.inactive')} />
        </Form.Item>
      </Form>
    </Drawer>
  )
}

export default function UnitsPage() {
  const { t } = useTranslation()
  const { can } = useAuth()
  const canManage = can('MASTER_MANAGE')
  const { data, isLoading } = useMasterList<UnitOfMeasure>('units')
  const [editing, setEditing] = useState<{ unit?: UnitOfMeasure } | null>(null)
  const openFromUrl = useCallback((item?: UnitOfMeasure) => setEditing(item ? { unit: item } : {}), [])
  useOpenFromUrl({ items: data, onOpen: openFromUrl, enabled: canManage })

  const newButton = canManage && (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({})}>
      {t('unit.new')}
    </Button>
  )

  const columns: TableColumnsType<UnitOfMeasure> = [
    { title: t('common.code'), dataIndex: 'code', width: 130, render: (code: string) => <span className="code-badge">{code}</span> },
    { title: t('common.name'), dataIndex: 'name', render: (name: string) => <span className="cell-title">{name}</span> },
    {
      title: t('unit.decimals'),
      dataIndex: 'decimals',
      width: 140,
      align: 'right',
      render: (d: number) => <span className="cell-sub">{d === 0 ? '1' : (1).toFixed(d)}</span>,
    },
    { title: t('common.status'), dataIndex: 'active', width: 120, responsive: ['sm'], render: (a: boolean) => <StatusTag active={a} /> },
  ]
  if (canManage) {
    columns.push({
      key: 'actions',
      width: 56,
      render: (_, unit) => <Button type="text" icon={<EditOutlined />} aria-label={t('common.edit')} onClick={() => setEditing({ unit })} />,
    })
  }

  return (
    <>
      <PageHeader title={t('unit.title')} subtitle={t('unit.subtitle')} icon={<NumberOutlined />} extra={newButton} />
      <FadeIn delay={0.08}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<UnitOfMeasure>
            rowKey="id"
            columns={columns}
            dataSource={data}
            loading={isLoading}
            pagination={false}
            locale={{ emptyText: isLoading ? ' ' : <EmptyState title={t('unit.empty')} action={newButton} /> }}
            onRow={(unit) => (canManage ? { onDoubleClick: () => setEditing({ unit }) } : {})}
          />
        </Card>
      </FadeIn>
      <UnitFormDrawer open={editing !== null} unit={editing?.unit} onClose={() => setEditing(null)} />
    </>
  )
}
