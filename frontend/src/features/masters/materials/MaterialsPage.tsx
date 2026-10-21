import { useCallback, useState } from 'react'
import { Button, Card, Input, Segmented, Select, Table, Tag, type TableColumnsType } from 'antd'
import { EditOutlined, InboxOutlined, PlusOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../../components/PageHeader'
import StatusTag from '../../../components/StatusTag'
import EmptyState from '../../../components/EmptyState'
import { FadeIn } from '../../../components/motion'
import { useOpenFromUrl } from '../../../components/useOpenFromUrl'
import { useAuth } from '../../../auth/AuthContext'
import type { Material, MaterialGroup } from '../../../api/types'
import { useMasterList } from '../masterApi'
import { fetchMaterial, useMaterials, type MaterialSearch } from './api'
import MaterialFormDrawer from './MaterialFormDrawer'

type ActiveFilter = 'all' | 'active' | 'inactive'

export default function MaterialsPage() {
  const { t } = useTranslation()
  const { can } = useAuth()
  const canManage = can('MASTER_MANAGE')

  const [search, setSearch] = useState<string>()
  const [groupId, setGroupId] = useState<number>()
  const [activeFilter, setActiveFilter] = useState<ActiveFilter>('active')
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const params: MaterialSearch = {
    search,
    groupId,
    active: activeFilter === 'all' ? undefined : activeFilter === 'active',
    page,
    size: pageSize,
  }
  const { data, isFetching } = useMaterials(params)
  const groups = useMasterList<MaterialGroup>('material-groups')

  const [editing, setEditing] = useState<{ material?: Material } | null>(null)
  const openFromUrl = useCallback((item?: Material) => setEditing(item ? { material: item } : {}), [])
  useOpenFromUrl({ fetchOne: fetchMaterial, onOpen: openFromUrl, enabled: canManage })

  const newButton = canManage && (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({})}>
      {t('material.new')}
    </Button>
  )

  const fmt = (v: number | undefined, decimals: number) =>
    v == null ? '-' : Number(v).toLocaleString('en-IN', { maximumFractionDigits: decimals })

  const columns: TableColumnsType<Material> = [
    { title: t('common.code'), dataIndex: 'code', width: 160, render: (code: string) => <span className="code-badge">{code}</span> },
    {
      title: t('common.name'),
      dataIndex: 'name',
      render: (name: string, m) => (
        <>
          <div className="cell-title">{name}</div>
          <div className="cell-sub">{m.groupName}</div>
        </>
      ),
    },
    { title: t('material.unit'), dataIndex: 'unitCode', width: 90, render: (u: string) => <Tag>{u}</Tag> },
    { title: t('category.hsn'), dataIndex: 'hsnCode', width: 100, responsive: ['lg'], render: (v?: string) => v ?? '-' },
    { title: t('category.gst'), dataIndex: 'gstRate', width: 90, align: 'right', responsive: ['lg'], render: (v?: number) => (v == null ? '-' : `${Number(v)}%`) },
    {
      title: t('material.reorderLevel'),
      dataIndex: 'reorderLevel',
      width: 140,
      align: 'right',
      responsive: ['md'],
      render: (v: number | undefined, m) => fmt(v, m.unitDecimals),
    },
    { title: t('common.status'), dataIndex: 'active', width: 110, responsive: ['sm'], render: (a: boolean) => <StatusTag active={a} /> },
  ]
  if (canManage) {
    columns.push({
      key: 'actions',
      width: 56,
      render: (_, material) => (
        <Button type="text" icon={<EditOutlined />} aria-label={t('common.edit')} onClick={() => setEditing({ material })} />
      ),
    })
  }

  return (
    <>
      <PageHeader title={t('material.title')} subtitle={t('material.subtitle')} icon={<InboxOutlined />} extra={newButton} />

      <FadeIn delay={0.05} className="toolbar">
        <Input.Search
          allowClear
          placeholder={t('design.searchPlaceholder')}
          onSearch={(v) => {
            setSearch(v.trim() || undefined)
            setPage(0)
          }}
          style={{ maxWidth: 260 }}
        />
        <Select
          allowClear
          placeholder={t('material.allGroups')}
          aria-label={t('material.allGroups')}
          value={groupId}
          onChange={(v) => {
            setGroupId(v)
            setPage(0)
          }}
          style={{ minWidth: 200 }}
          options={(groups.data ?? []).map((g) => ({ value: g.id, label: g.name }))}
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
          <Table<Material>
            rowKey="id"
            columns={columns}
            dataSource={data?.content}
            loading={isFetching}
            locale={{ emptyText: isFetching ? ' ' : <EmptyState title={t('material.empty')} action={newButton} /> }}
            onRow={(material) => (canManage ? { onDoubleClick: () => setEditing({ material }) } : {})}
            pagination={{
              current: page + 1,
              pageSize,
              total: data?.totalElements,
              showSizeChanger: true,
              hideOnSinglePage: true,
              onChange: (p, s) => {
                setPage(s !== pageSize ? 0 : p - 1)
                setPageSize(s)
              },
            }}
          />
        </Card>
      </FadeIn>

      <MaterialFormDrawer open={editing !== null} material={editing?.material} onClose={() => setEditing(null)} />
    </>
  )
}
