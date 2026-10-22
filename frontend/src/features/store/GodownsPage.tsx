import { useCallback, useEffect, useState } from 'react'
import { App, Button, Card, Col, Drawer, Form, Input, Row, Space, Switch, Table, type TableColumnsType } from 'antd'
import { EditOutlined, HomeOutlined, PlusOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../components/PageHeader'
import StatusTag from '../../components/StatusTag'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useDrawerSize } from '../../components/useDrawerSize'
import { useOpenFromUrl } from '../../components/useOpenFromUrl'
import { useAuth } from '../../auth/AuthContext'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import { patterns, toUpper } from '../../api/validation'
import type { StoreLocation, StoreLocationRequest } from '../../api/types'
import { useGodowns, useSaveGodown } from './api'
import NoPlant from './NoPlant'

function GodownFormDrawer({ open, plantId, godown, onClose }: {
  open: boolean
  plantId: number
  godown?: StoreLocation
  onClose: () => void
}) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const drawerSize = useDrawerSize(440)
  const [form] = Form.useForm<StoreLocationRequest>()
  const save = useSaveGodown()

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(godown ?? { active: true })
  }, [open, godown, form])

  const onFinish = async (values: StoreLocationRequest) => {
    try {
      // A godown always stays in its plant; a new one goes into the plant chosen in the header.
      await save.mutateAsync({ id: godown?.id, body: { ...values, plantId: godown?.plantId ?? plantId } })
      message.success(t('common.saved'))
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }

  return (
    <Drawer
      title={godown ? `${t('store.editGodown')} · ${godown.code}` : t('store.newGodown')}
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
              rules={[required, { pattern: patterns.code, message: t('company.invalidCode') }]}>
              <Input maxLength={20} placeholder="MAIN" />
            </Form.Item>
          </Col>
          <Col xs={14}>
            <Form.Item name="name" label={t('common.name')} rules={[required]}>
              <Input maxLength={80} placeholder="Main store" />
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

/** Godowns (store locations) of the plant chosen in the header. */
export default function GodownsPage() {
  const { t } = useTranslation()
  const { can, currentPlant } = useAuth()
  const canManage = can('STORE_ENTRY')
  const { data, isLoading } = useGodowns(currentPlant?.id)
  const [editing, setEditing] = useState<{ godown?: StoreLocation } | null>(null)
  const openFromUrl = useCallback((item?: StoreLocation) => setEditing(item ? { godown: item } : {}), [])
  useOpenFromUrl({ items: data, onOpen: openFromUrl, enabled: canManage && !!currentPlant })

  if (!currentPlant) return <NoPlant />

  const newButton = canManage && (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({})}>
      {t('store.newGodown')}
    </Button>
  )

  const columns: TableColumnsType<StoreLocation> = [
    { title: t('common.code'), dataIndex: 'code', width: 140, render: (code: string) => <span className="code-badge">{code}</span> },
    { title: t('common.name'), dataIndex: 'name', render: (name: string) => <span className="cell-title">{name}</span> },
    { title: t('common.status'), dataIndex: 'active', width: 120, render: (a: boolean) => <StatusTag active={a} /> },
  ]
  if (canManage) {
    columns.push({
      key: 'actions',
      width: 56,
      render: (_, godown) => <Button type="text" icon={<EditOutlined />} aria-label={t('common.edit')} onClick={() => setEditing({ godown })} />,
    })
  }

  return (
    <>
      <PageHeader
        title={t('store.godownsTitle')}
        subtitle={t('store.godownsSubtitle', { plant: currentPlant.name })}
        icon={<HomeOutlined />}
        extra={newButton}
      />
      <FadeIn delay={0.08}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<StoreLocation>
            rowKey="id"
            columns={columns}
            dataSource={data}
            loading={isLoading}
            pagination={false}
            locale={{
              emptyText: isLoading ? ' ' : (
                <EmptyState title={t('store.emptyGodowns')} hint={t('store.emptyGodownsHint')} action={newButton} />
              ),
            }}
            onRow={(godown) => (canManage ? { onDoubleClick: () => setEditing({ godown }) } : {})}
          />
        </Card>
      </FadeIn>
      <GodownFormDrawer
        open={editing !== null}
        plantId={currentPlant.id}
        godown={editing?.godown}
        onClose={() => setEditing(null)}
      />
    </>
  )
}
