import { useState } from 'react'
import { App, Button, Card, Popconfirm, Space, Table, Tag, Tooltip, type TableColumnsType } from 'antd'
import { DeleteOutlined, EditOutlined, EyeOutlined, PlusOutlined, SafetyCertificateOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../components/PageHeader'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import { errorMessage } from '../../api/errors'
import type { Role } from '../../api/types'
import { useDeleteRole, useRoles } from './api'
import RoleFormDrawer from './RoleFormDrawer'

export default function RolesPage() {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const { can } = useAuth()
  const canManage = can('ROLE_MANAGE')
  const { data, isLoading } = useRoles()
  const deleteRole = useDeleteRole()
  const [editing, setEditing] = useState<{ role?: Role } | null>(null)

  const onDelete = async (role: Role) => {
    try {
      await deleteRole.mutateAsync(role.id)
      message.success(t('role.deleted'))
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const columns: TableColumnsType<Role> = [
    {
      title: t('role.name'),
      dataIndex: 'name',
      render: (name: string, role) => (
        <Space>
          <span className="cell-title">{name}</span>
          {role.systemRole && <Tag color="gold">{t('role.system')}</Tag>}
        </Space>
      ),
    },
    { title: t('role.description'), dataIndex: 'description', responsive: ['md'] },
    {
      title: t('role.permissions'),
      dataIndex: 'permissions',
      width: 140,
      responsive: ['sm'],
      render: (p: string[]) => t('role.permissionCount', { count: p.length }),
    },
    { title: t('role.users'), dataIndex: 'userCount', width: 90, align: 'right' },
    {
      key: 'actions',
      width: 100,
      render: (_, role) => {
        const editable = canManage && !role.systemRole
        return (
          <Space size={0}>
            <Button
              type="text"
              icon={editable ? <EditOutlined /> : <EyeOutlined />}
              aria-label={editable ? t('common.edit') : t('common.view')}
              onClick={() => setEditing({ role })}
            />
            {editable && (
              <Tooltip title={role.userCount > 0 ? t('role.inUse') : undefined}>
                <Popconfirm
                  title={t('role.deleteConfirm')}
                  okText={t('common.delete')}
                  cancelText={t('common.cancel')}
                  okButtonProps={{ danger: true }}
                  onConfirm={() => onDelete(role)}
                  disabled={role.userCount > 0}
                >
                  <Button type="text" danger icon={<DeleteOutlined />} disabled={role.userCount > 0} aria-label={t('common.delete')} />
                </Popconfirm>
              </Tooltip>
            )}
          </Space>
        )
      },
    },
  ]

  return (
    <>
      <PageHeader
        title={t('role.title')}
        subtitle={t('role.subtitle')}
        icon={<SafetyCertificateOutlined />}
        extra={
          canManage && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({})}>
              {t('role.new')}
            </Button>
          )
        }
      />
      <FadeIn delay={0.08}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<Role> rowKey="id" columns={columns} dataSource={data} loading={isLoading} pagination={false} />
        </Card>
      </FadeIn>
      <RoleFormDrawer open={editing !== null} role={editing?.role} onClose={() => setEditing(null)} />
    </>
  )
}
