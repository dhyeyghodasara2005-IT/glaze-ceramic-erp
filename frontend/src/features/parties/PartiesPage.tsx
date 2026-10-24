import { useCallback, useState } from 'react'
import { Button, Card, Input, Segmented, Select, Table, Tag, type TableColumnsType } from 'antd'
import { CarOutlined, EditOutlined, PlusOutlined, ReconciliationOutlined, ShopOutlined, ShoppingCartOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../components/PageHeader'
import StatusTag from '../../components/StatusTag'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useOpenFromUrl } from '../../components/useOpenFromUrl'
import { useAuth } from '../../auth/AuthContext'
import { STATE_OPTIONS } from '../../api/indianStates'
import { isCustomer, type Party, type PartyType } from '../../api/types'
import { fetchParty, useParties, type PartySearch } from './api'
import PartyFormDrawer from './PartyFormDrawer'

type ActiveFilter = 'all' | 'active' | 'inactive'

const ICONS: Record<PartyType, React.ReactNode> = {
  DEALER: <ShopOutlined />,
  SUPPLIER: <ShoppingCartOutlined />,
  TRANSPORTER: <CarOutlined />,
  JOBWORK: <ReconciliationOutlined />,
}

/**
 * One screen for every party type. App.tsx mounts it once per type:
 * /parties/dealers, /parties/suppliers, /parties/transporters, /parties/principals.
 */
export default function PartiesPage({ type }: { type: PartyType }) {
  const { t } = useTranslation()
  const { can } = useAuth()
  const canManage = can('PARTY_MANAGE')
  const billed = isCustomer(type)     // dealers and jobwork principals have a credit limit

  const [search, setSearch] = useState<string>()
  const [stateCode, setStateCode] = useState<string>()
  const [activeFilter, setActiveFilter] = useState<ActiveFilter>('active')
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const params: PartySearch = {
    type,
    search,
    stateCode,
    active: activeFilter === 'all' ? undefined : activeFilter === 'active',
    page,
    size: pageSize,
  }
  const { data, isFetching } = useParties(params)

  const [editing, setEditing] = useState<{ party?: Party } | null>(null)
  const openFromUrl = useCallback((item?: Party) => setEditing(item ? { party: item } : {}), [])
  useOpenFromUrl({ fetchOne: fetchParty, onOpen: openFromUrl, enabled: canManage })

  const newButton = canManage && (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({})}>
      {t(`party.new.${type}`)}
    </Button>
  )

  const columns: TableColumnsType<Party> = [
    { title: t('common.code'), dataIndex: 'code', width: 120, render: (code: string) => <span className="code-badge">{code}</span> },
    {
      title: t('party.name'),
      dataIndex: 'name',
      render: (name: string, p) => (
        <>
          <div className="cell-title">{name}</div>
          <div className="cell-sub">
            {[p.city, p.state ?? (p.country !== 'India' ? p.country : undefined)].filter(Boolean).join(', ') || '-'}
          </div>
        </>
      ),
    },
    {
      title: type === 'TRANSPORTER' ? t('party.transporterId') : t('company.gstin'),
      dataIndex: 'gstin',
      width: 180,
      responsive: ['md'],
      render: (v: string | undefined, p) => v ?? <Tag>{t(`gstReg.${p.gstRegistration}`)}</Tag>,
    },
    { title: t('party.mobile'), dataIndex: 'mobile', width: 140, responsive: ['lg'], render: (v?: string) => v ?? '-' },
    ...(billed
      ? [{
          title: t('party.creditLimit'),
          key: 'credit',
          width: 170,
          align: 'right' as const,
          responsive: ['xl' as const],
          render: (_: unknown, p: Party) =>
            p.creditLimit == null ? '-' : (
              <>
                <div>₹ {Number(p.creditLimit).toLocaleString('en-IN')}</div>
                {p.creditDays != null && <div className="cell-sub">{p.creditDays} {t('party.days')}</div>}
              </>
            ),
        }]
      : []),
    { title: t('common.status'), dataIndex: 'active', width: 110, responsive: ['sm'], render: (a: boolean) => <StatusTag active={a} /> },
  ]
  if (canManage) {
    columns.push({
      key: 'actions',
      width: 56,
      render: (_, party) => <Button type="text" icon={<EditOutlined />} aria-label={t('common.edit')} onClick={() => setEditing({ party })} />,
    })
  }

  return (
    <>
      <PageHeader title={t(`party.title.${type}`)} subtitle={t(`party.subtitle.${type}`)} icon={ICONS[type]} extra={newButton} />

      <FadeIn delay={0.05} className="toolbar">
        <Input.Search
          allowClear
          placeholder={t('party.searchPlaceholder')}
          onSearch={(v) => {
            setSearch(v.trim() || undefined)
            setPage(0)
          }}
          style={{ maxWidth: 300 }}
        />
        <Select
          allowClear
          showSearch={{ optionFilterProp: 'label' }}
          placeholder={t('party.allStates')}
          aria-label={t('party.allStates')}
          value={stateCode}
          onChange={(v) => {
            setStateCode(v)
            setPage(0)
          }}
          style={{ minWidth: 200 }}
          options={STATE_OPTIONS}
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
          <Table<Party>
            rowKey="id"
            columns={columns}
            dataSource={data?.content}
            loading={isFetching}
            locale={{ emptyText: isFetching ? ' ' : <EmptyState title={t(`party.empty.${type}`)} action={newButton} /> }}
            onRow={(party) => (canManage ? { onDoubleClick: () => setEditing({ party }) } : {})}
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

      <PartyFormDrawer open={editing !== null} type={type} party={editing?.party} onClose={() => setEditing(null)} />
    </>
  )
}
