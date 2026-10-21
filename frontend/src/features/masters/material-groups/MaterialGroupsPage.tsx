import { useCallback, useEffect, useState } from 'react'
import { App, Button, Card, Drawer, Form, Input, Space, Switch, Table, type TableColumnsType } from 'antd'
import { EditOutlined, FolderOutlined, PlusOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../../components/PageHeader'
import StatusTag from '../../../components/StatusTag'
import EmptyState from '../../../components/EmptyState'
import { FadeIn } from '../../../components/motion'
import { useDrawerSize } from '../../../components/useDrawerSize'
import { useOpenFromUrl } from '../../../components/useOpenFromUrl'
import { useAuth } from '../../../auth/AuthContext'
import { applyFieldErrors, errorMessage } from '../../../api/errors'
import { blankToUndefined, patterns, toUpper } from '../../../api/validation'
import type { MaterialGroup, MaterialGroupRequest } from '../../../api/types'
import { useMasterList, useSaveMaster } from '../masterApi'

function GroupFormDrawer({ open, group, onClose }: { open: boolean; group?: MaterialGroup; onClose: () => void }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const drawerSize = useDrawerSize(440)
  const [form] = Form.useForm<MaterialGroupRequest>()
  const save = useSaveMaster<MaterialGroup, MaterialGroupRequest>('material-groups')

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(group ?? { active: true })
  }, [open, group, form])

  const onFinish = async (values: MaterialGroupRequest) => {
    try {
      await save.mutateAsync({ id: group?.id, body: blankToUndefined(values) })
      message.success(t('common.saved'))
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }

  return (
    <Drawer
      title={group ? t('materialGroup.edit') : t('materialGroup.new')}
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
        <Form.Item name="code" label={t('common.code')} normalize={toUpper}
          rules={[required, { pattern: patterns.code, message: t('company.invalidCode') }]}>
          <Input maxLength={20} placeholder="GLAZE" />
        </Form.Item>
        <Form.Item name="name" label={t('common.name')} rules={[required]}>
          <Input maxLength={80} placeholder="Glaze, engobe & frit" />
        </Form.Item>
        <Form.Item name="active" label={t('common.status')} valuePropName="checked">
          <Switch checkedChildren={t('common.active')} unCheckedChildren={t('common.inactive')} />
        </Form.Item>
      </Form>
    </Drawer>
  )
}

export default function MaterialGroupsPage() {
  const { t } = useTranslation()
  const { can } = useAuth()
  const canManage = can('MASTER_MANAGE')
  const { data, isLoading } = useMasterList<MaterialGroup>('material-groups')
  const [editing, setEditing] = useState<{ group?: MaterialGroup } | null>(null)
  const openFromUrl = useCallback((item?: MaterialGroup) => setEditing(item ? { group: item } : {}), [])
  useOpenFromUrl({ items: data, onOpen: openFromUrl, enabled: canManage })

  const newButton = canManage && (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({})}>
      {t('materialGroup.new')}
    </Button>
  )

  const columns: TableColumnsType<MaterialGroup> = [
    { title: t('common.code'), dataIndex: 'code', width: 150, render: (code: string) => <span className="code-badge">{code}</span> },
    { title: t('common.name'), dataIndex: 'name', render: (name: string) => <span className="cell-title">{name}</span> },
    { title: t('common.status'), dataIndex: 'active', width: 120, responsive: ['sm'], render: (a: boolean) => <StatusTag active={a} /> },
  ]
  if (canManage) {
    columns.push({
      key: 'actions',
      width: 56,
      render: (_, group) => <Button type="text" icon={<EditOutlined />} aria-label={t('common.edit')} onClick={() => setEditing({ group })} />,
    })
  }

  return (
    <>
      <PageHeader title={t('materialGroup.title')} subtitle={t('materialGroup.subtitle')} icon={<FolderOutlined />} extra={newButton} />
      <FadeIn delay={0.08}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<MaterialGroup>
            rowKey="id"
            columns={columns}
            dataSource={data}
            loading={isLoading}
            pagination={false}
            locale={{ emptyText: isLoading ? ' ' : <EmptyState title={t('materialGroup.empty')} action={newButton} /> }}
            onRow={(group) => (canManage ? { onDoubleClick: () => setEditing({ group }) } : {})}
          />
        </Card>
      </FadeIn>
      <GroupFormDrawer open={editing !== null} group={editing?.group} onClose={() => setEditing(null)} />
    </>
  )
}
