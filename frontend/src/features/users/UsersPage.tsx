import { useCallback, useState } from 'react'
import { useOpenFromUrl } from '../../components/useOpenFromUrl'
import { api } from '../../api/client'
import { App, Button, Card, Dropdown, Flex, Input, Segmented, Space, Table, Tag, Typography, type MenuProps, type TableColumnsType } from 'antd'
import { EditOutlined, KeyOutlined, MoreOutlined, PlusOutlined, StopOutlined, CheckCircleOutlined, TeamOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import dayjs from 'dayjs'
import PageHeader from '../../components/PageHeader'
import UserAvatar from '../../components/UserAvatar'
import { FadeIn } from '../../components/motion'
import StatusTag from '../../components/StatusTag'
import { useAuth } from '../../auth/AuthContext'
import { errorMessage } from '../../api/errors'
import type { User } from '../../api/types'
import { useSetUserActive, useUsers, type UserSearch } from './api'
import UserFormDrawer from './UserFormDrawer'
import ResetPasswordModal from './ResetPasswordModal'

type ActiveFilter = 'all' | 'active' | 'inactive'

/** Loads one record for ?open=<id> links (the list is paged, so it may not be on screen). */
const fetchOne = async (id: number) => (await api.get<User>(`/users/${id}`)).data

export default function UsersPage() {
  const { t } = useTranslation()
  const { message, modal } = App.useApp()
  const { can, user: me } = useAuth()
  const canManage = can('USER_MANAGE')

  const [search, setSearch] = useState<string>()
  const [activeFilter, setActiveFilter] = useState<ActiveFilter>('active')
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const params: UserSearch = {
    search,
    active: activeFilter === 'all' ? undefined : activeFilter === 'active',
    page,
    size,
  }
  const { data, isFetching } = useUsers(params)
  const setActive = useSetUserActive()

  const [editing, setEditing] = useState<{ user?: User } | null>(null)
  // Search palette / links: ?open=<id> edits a record, ?new=1 opens the empty form.
  const openFromUrl = useCallback((item?: User) => setEditing(item ? { user: item } : {}), [])
  useOpenFromUrl({ fetchOne, onOpen: openFromUrl, enabled: canManage })
  const [resetting, setResetting] = useState<User>()

  const toggleActive = (user: User) => {
    const run = async () => {
      try {
        await setActive.mutateAsync({ id: user.id, active: !user.active })
        message.success(t('common.saved'))
      } catch (err) {
        message.error(errorMessage(err, t('common.somethingWrong')))
      }
    }
    if (user.active) {
      modal.confirm({
        title: t('user.deactivateConfirm'),
        okText: t('user.deactivate'),
        okButtonProps: { danger: true },
        cancelText: t('common.cancel'),
        onOk: run,
      })
    } else {
      run()
    }
  }

  const rowActions = (user: User): MenuProps['items'] => [
    { key: 'edit', icon: <EditOutlined />, label: t('common.edit'), onClick: () => setEditing({ user }) },
    { key: 'reset', icon: <KeyOutlined />, label: t('user.resetPassword'), onClick: () => setResetting(user) },
    user.active
      ? {
          key: 'deactivate',
          icon: <StopOutlined />,
          label: t('user.deactivate'),
          danger: true,
          disabled: user.id === me?.id,
          onClick: () => toggleActive(user),
        }
      : { key: 'activate', icon: <CheckCircleOutlined />, label: t('user.activate'), onClick: () => toggleActive(user) },
  ]

  const columns: TableColumnsType<User> = [
    {
      title: t('user.fullName'),
      dataIndex: 'fullName',
      render: (name: string, u) => (
        <Flex align="center" gap={12}>
          <UserAvatar name={name} size={36} />
          <div style={{ minWidth: 0 }}>
            <div className="cell-title">{name}</div>
            <div className="cell-sub">@{u.username}</div>
          </div>
        </Flex>
      ),
    },
    { title: t('user.phone'), dataIndex: 'phone', responsive: ['md'] },
    {
      title: t('user.roles'),
      dataIndex: 'roles',
      responsive: ['sm'],
      render: (roles: User['roles']) => (
        <Flex gap={4} wrap>
          {roles.map((r) => (
            <Tag key={r.id}>{r.name}</Tag>
          ))}
        </Flex>
      ),
    },
    {
      title: t('user.plants'),
      key: 'plants',
      responsive: ['lg'],
      render: (_, u) =>
        u.allPlantsAccess ? (
          <Tag color="blue">{t('common.allPlants')}</Tag>
        ) : (
          u.plants.map((p) => p.code).join(', ') || '-'
        ),
    },
    {
      title: t('user.lastLogin'),
      dataIndex: 'lastLoginAt',
      responsive: ['xl'],
      render: (v?: string) => (v ? dayjs(v).format('DD MMM YYYY, HH:mm') : t('user.never')),
    },
    {
      title: t('common.status'),
      key: 'status',
      width: 110,
      render: (_, u) => (
        <Space orientation="vertical" size={2}>
          <StatusTag active={u.active} />
          {u.active && u.mustChangePassword && (
            <Typography.Text type="warning" style={{ fontSize: 12 }}>
              {t('user.mustChange')}
            </Typography.Text>
          )}
        </Space>
      ),
    },
  ]
  if (canManage) {
    columns.push({
      key: 'actions',
      width: 56,
      render: (_, u) => (
        <Dropdown menu={{ items: rowActions(u) }} trigger={['click']}>
          <Button type="text" icon={<MoreOutlined />} aria-label={t('common.actions')} />
        </Dropdown>
      ),
    })
  }

  return (
    <>
      <PageHeader
        title={t('user.title')}
        subtitle={t('user.subtitle')}
        icon={<TeamOutlined />}
        extra={
          canManage && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({})}>
              {t('user.new')}
            </Button>
          )
        }
      />
      <FadeIn delay={0.05} className="toolbar">
        <Input.Search
          allowClear
          placeholder={t('user.searchPlaceholder')}
          onSearch={(value) => {
            setSearch(value.trim() || undefined)
            setPage(0)
          }}
          style={{ maxWidth: 320 }}
        />
        <Segmented<ActiveFilter>
          value={activeFilter}
          onChange={(v) => {
            setActiveFilter(v)
            setPage(0)
          }}
          options={[
            { value: 'active', label: t('common.active') },
            { value: 'inactive', label: t('common.inactive') },
            { value: 'all', label: t('common.all') },
          ]}
        />
      </FadeIn>
      <FadeIn delay={0.1}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<User>
            rowKey="id"
            columns={columns}
            dataSource={data?.content}
            loading={isFetching}
            pagination={{
              current: page + 1, // antd counts pages from 1, Spring from 0
              pageSize: size,
              total: data?.totalElements,
              showSizeChanger: true,
              onChange: (p, s) => {
                setPage(s !== size ? 0 : p - 1)
                setSize(s)
              },
            }}
          />
        </Card>
      </FadeIn>
      <UserFormDrawer open={editing !== null} user={editing?.user} onClose={() => setEditing(null)} />
      <ResetPasswordModal user={resetting} onClose={() => setResetting(undefined)} />
    </>
  )
}
