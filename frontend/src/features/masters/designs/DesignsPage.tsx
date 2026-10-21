import { useCallback, useState } from 'react'
import { useOpenFromUrl } from '../../../components/useOpenFromUrl'
import { api } from '../../../api/client'
import { Button, Card, Input, Segmented, Select, Table, Tag, type TableColumnsType } from 'antd'
import { EditOutlined, PictureOutlined, PlusOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import dayjs from 'dayjs'
import PageHeader from '../../../components/PageHeader'
import StatusTag from '../../../components/StatusTag'
import EmptyState from '../../../components/EmptyState'
import { FadeIn } from '../../../components/motion'
import { useAuth } from '../../../auth/AuthContext'
import type { Design, Finish, ProductCategory, TileSize } from '../../../api/types'
import { useMasterList } from '../masterApi'
import { useDesigns, type DesignSearch } from './api'
import DesignFormDrawer from './DesignFormDrawer'

type ActiveFilter = 'all' | 'active' | 'inactive'

/** Loads one record for ?open=<id> links (the list is paged, so it may not be on screen). */
const fetchOne = async (id: number) => (await api.get<Design>(`/masters/designs/${id}`)).data

export default function DesignsPage() {
  const { t } = useTranslation()
  const { can } = useAuth()
  const canManage = can('MASTER_MANAGE')

  // ---- filters (each change goes back to page 1) ----
  const [search, setSearch] = useState<string>()
  const [categoryId, setCategoryId] = useState<number>()
  const [sizeId, setSizeId] = useState<number>()
  const [finishId, setFinishId] = useState<number>()
  const [activeFilter, setActiveFilter] = useState<ActiveFilter>('active')
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const resetPage = <T,>(setter: (v: T) => void) => (v: T) => {
    setter(v)
    setPage(0)
  }

  const params: DesignSearch = {
    search,
    categoryId,
    sizeId,
    finishId,
    active: activeFilter === 'all' ? undefined : activeFilter === 'active',
    page,
    size: pageSize,
  }
  const { data, isFetching } = useDesigns(params)
  const categories = useMasterList<ProductCategory>('categories')
  const sizes = useMasterList<TileSize>('sizes')
  const finishes = useMasterList<Finish>('finishes')
  const [editing, setEditing] = useState<{ design?: Design } | null>(null)
  // Search palette / links: ?open=<id> edits a record, ?new=1 opens the empty form.
  const openFromUrl = useCallback((item?: Design) => setEditing(item ? { design: item } : {}), [])
  useOpenFromUrl({ fetchOne, onOpen: openFromUrl, enabled: canManage })

  const newButton = canManage && (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({})}>
      {t('design.new')}
    </Button>
  )

  const columns: TableColumnsType<Design> = [
    { title: t('design.code'), dataIndex: 'code', width: 150, render: (code: string) => <span className="code-badge">{code}</span> },
    {
      title: t('design.name'),
      dataIndex: 'name',
      render: (name: string, d) => (
        <>
          <div className="cell-title">{name}</div>
          <div className="cell-sub">
            {d.categoryCode} · {d.sizeCode} · {d.finishName}
          </div>
        </>
      ),
    },
    { title: t('design.category'), dataIndex: 'categoryCode', width: 110, responsive: ['xl'], render: (v: string) => <Tag>{v}</Tag> },
    { title: t('design.faces'), dataIndex: 'faces', width: 80, align: 'right', responsive: ['md'] },
    {
      title: t('design.launchDate'),
      dataIndex: 'launchDate',
      width: 130,
      responsive: ['lg'],
      render: (v?: string) => (v ? dayjs(v).format('DD MMM YYYY') : '-'),
    },
    { title: t('common.status'), dataIndex: 'active', width: 110, responsive: ['sm'], render: (a: boolean) => <StatusTag active={a} /> },
  ]
  if (canManage) {
    columns.push({
      key: 'actions',
      width: 56,
      render: (_, design) => (
        <Button type="text" icon={<EditOutlined />} aria-label={t('common.edit')} onClick={() => setEditing({ design })} />
      ),
    })
  }

  const filterSelect = (placeholder: string, value: number | undefined, onChange: (v?: number) => void,
    items: { id: number; label: string }[]) => (
    <Select
      allowClear
      placeholder={placeholder}
      aria-label={placeholder}
      value={value}
      onChange={resetPage(onChange)}
      style={{ minWidth: 150 }}
      options={items.map((i) => ({ value: i.id, label: i.label }))}
    />
  )

  return (
    <>
      <PageHeader title={t('design.title')} subtitle={t('design.subtitle')} icon={<PictureOutlined />} extra={newButton} />

      <FadeIn delay={0.05} className="toolbar">
        <Input.Search
          allowClear
          placeholder={t('design.searchPlaceholder')}
          onSearch={(v) => resetPage(setSearch)(v.trim() || undefined)}
          style={{ maxWidth: 260 }}
        />
        {filterSelect(t('design.allCategories'), categoryId, setCategoryId,
          (categories.data ?? []).map((c) => ({ id: c.id, label: c.code })))}
        {filterSelect(t('design.allSizes'), sizeId, setSizeId,
          (sizes.data ?? []).map((s) => ({ id: s.id, label: s.code })))}
        {filterSelect(t('design.allFinishes'), finishId, setFinishId,
          (finishes.data ?? []).map((f) => ({ id: f.id, label: f.name })))}
        <Segmented<ActiveFilter>
          value={activeFilter}
          onChange={resetPage(setActiveFilter)}
          options={[
            { value: 'active', label: t('common.active') },
            { value: 'inactive', label: t('common.inactive') },
            { value: 'all', label: t('common.all') },
          ]}
        />
      </FadeIn>

      <FadeIn delay={0.1}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<Design>
            rowKey="id"
            columns={columns}
            dataSource={data?.content}
            loading={isFetching}
            locale={{ emptyText: isFetching ? ' ' : <EmptyState title={t('design.empty')} action={newButton} /> }}
            onRow={(design) => (canManage ? { onDoubleClick: () => setEditing({ design }) } : {})}
            pagination={{
              current: page + 1, // antd counts pages from 1, Spring from 0
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

      <DesignFormDrawer open={editing !== null} design={editing?.design} onClose={() => setEditing(null)} />
    </>
  )
}
