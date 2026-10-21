import { useCallback, useEffect, useState } from 'react'
import { useOpenFromUrl } from '../../../components/useOpenFromUrl'
import { App, Button, Card, Drawer, Form, Input, Space, Switch, Table, type TableColumnsType } from 'antd'
import { BgColorsOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../../components/PageHeader'
import StatusTag from '../../../components/StatusTag'
import EmptyState from '../../../components/EmptyState'
import { FadeIn } from '../../../components/motion'
import { useDrawerSize } from '../../../components/useDrawerSize'
import { useAuth } from '../../../auth/AuthContext'
import { applyFieldErrors, errorMessage } from '../../../api/errors'
import { blankToUndefined, patterns, toUpper } from '../../../api/validation'
import type { Finish, FinishRequest } from '../../../api/types'
import { useMasterList, useSaveMaster } from '../masterApi'

/** Create / edit one finish. Small enough to live in the same file as the page. */
function FinishFormDrawer({ open, finish, onClose }: { open: boolean; finish?: Finish; onClose: () => void }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const drawerSize = useDrawerSize(440)
  const [form] = Form.useForm<FinishRequest>()
  const save = useSaveMaster<Finish, FinishRequest>('finishes')

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(finish ?? { active: true })
  }, [open, finish, form])

  const onFinish = async (values: FinishRequest) => {
    try {
      await save.mutateAsync({ id: finish?.id, body: blankToUndefined(values) })
      message.success(t('common.saved'))
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }

  return (
    <Drawer
      title={finish ? t('finish.edit') : t('finish.new')}
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
          <Input maxLength={20} placeholder="GLOSSY" />
        </Form.Item>
        <Form.Item name="name" label={t('common.name')} rules={[required]}>
          <Input maxLength={60} placeholder="Glossy" />
        </Form.Item>
        <Form.Item name="active" label={t('common.status')} valuePropName="checked">
          <Switch checkedChildren={t('common.active')} unCheckedChildren={t('common.inactive')} />
        </Form.Item>
      </Form>
    </Drawer>
  )
}

export default function FinishesPage() {
  const { t } = useTranslation()
  const { can } = useAuth()
  const canManage = can('MASTER_MANAGE')
  const { data, isLoading } = useMasterList<Finish>('finishes')
  const [editing, setEditing] = useState<{ finish?: Finish } | null>(null)
  // Search palette / links: ?open=<id> edits a record, ?new=1 opens the empty form.
  const openFromUrl = useCallback((item?: Finish) => setEditing(item ? { finish: item } : {}), [])
  useOpenFromUrl({ items: data, onOpen: openFromUrl, enabled: canManage })

  const newButton = canManage && (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({})}>
      {t('finish.new')}
    </Button>
  )

  const columns: TableColumnsType<Finish> = [
    { title: t('common.code'), dataIndex: 'code', width: 160, render: (code: string) => <span className="code-badge">{code}</span> },
    { title: t('common.name'), dataIndex: 'name', render: (name: string) => <span className="cell-title">{name}</span> },
    { title: t('common.status'), dataIndex: 'active', width: 120, render: (a: boolean) => <StatusTag active={a} /> },
  ]
  if (canManage) {
    columns.push({
      key: 'actions',
      width: 56,
      render: (_, finish) => (
        <Button type="text" icon={<EditOutlined />} aria-label={t('common.edit')} onClick={() => setEditing({ finish })} />
      ),
    })
  }

  return (
    <>
      <PageHeader title={t('finish.title')} subtitle={t('finish.subtitle')} icon={<BgColorsOutlined />} extra={newButton} />
      <FadeIn delay={0.08}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<Finish>
            rowKey="id"
            columns={columns}
            dataSource={data}
            loading={isLoading}
            pagination={false}
            locale={{ emptyText: isLoading ? ' ' : <EmptyState title={t('finish.empty')} action={newButton} /> }}
            onRow={(finish) => (canManage ? { onDoubleClick: () => setEditing({ finish }) } : {})}
          />
        </Card>
      </FadeIn>
      <FinishFormDrawer open={editing !== null} finish={editing?.finish} onClose={() => setEditing(null)} />
    </>
  )
}
