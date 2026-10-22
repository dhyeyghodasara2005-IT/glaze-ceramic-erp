import { useMemo, useState } from 'react'
import { Button, Card, Input, Segmented, Select, Space, Table, type TableColumnsType } from 'antd'
import { DatabaseOutlined, ExportOutlined, ImportOutlined, InboxOutlined, WarningOutlined, WalletOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import CountUp from '../../components/CountUp'
import { FadeIn, Stagger, StaggerItem } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import type { StockRow } from '../../api/types'
import { useStock } from './api'
import { DOC_TYPES } from './docTypes'
import { money, qty } from './format'
import LedgerDrawer from './LedgerDrawer'
import NoPlant from './NoPlant'

type Show = 'all' | 'inStock' | 'low'

/** "What do we have?" - every material of the plant with its stock, value and godown breakdown. */
export default function StockPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { can, currentPlant } = useAuth()
  const { data, isLoading } = useStock(currentPlant?.id)

  const [search, setSearch] = useState('')
  const [group, setGroup] = useState<string>()
  const [show, setShow] = useState<Show>('all')
  const [ledgerRow, setLedgerRow] = useState<StockRow | null>(null)

  // Filtering happens in the browser: a plant has a few hundred materials at most.
  const rows = useMemo(() => {
    const q = search.trim().toLowerCase()
    return (data ?? []).filter((r) =>
      (!q || r.materialCode.toLowerCase().includes(q) || r.materialName.toLowerCase().includes(q))
      && (!group || r.groupName === group)
      && (show === 'all' || (show === 'low' ? r.low : r.quantity > 0)),
    )
  }, [data, search, group, show])

  const totals = useMemo(() => {
    const all = data ?? []
    return {
      value: all.reduce((sum, r) => sum + r.value, 0),
      inStock: all.filter((r) => r.quantity > 0).length,
      low: all.filter((r) => r.low).length,
    }
  }, [data])
  const groups = useMemo(() => [...new Set((data ?? []).map((r) => r.groupName))].sort(), [data])

  if (!currentPlant) return <NoPlant />

  const canEntry = can('STORE_ENTRY')
  const actions = canEntry && (
    <Space wrap>
      <Button icon={<ExportOutlined />} onClick={() => navigate(`${DOC_TYPES.ISSUE.path}/new`)}>{t('store.newDoc.ISSUE')}</Button>
      <Button type="primary" icon={<ImportOutlined />} onClick={() => navigate(`${DOC_TYPES.GRN.path}/new`)}>{t('store.newDoc.GRN')}</Button>
    </Space>
  )

  const columns: TableColumnsType<StockRow> = [
    { title: t('common.code'), dataIndex: 'materialCode', width: 130, render: (code: string) => <span className="code-badge">{code}</span> },
    {
      title: t('store.material'),
      dataIndex: 'materialName',
      render: (name: string, r) => (
        <>
          <div className="cell-title">{name}</div>
          <div className="cell-sub">{r.groupName}</div>
        </>
      ),
    },
    {
      title: t('store.quantity'),
      dataIndex: 'quantity',
      width: 190,
      align: 'right',
      render: (v: number, r) => (
        <>
          <div className="cell-title">
            {qty(v, r.unitDecimals)} <span className="unit">{r.unitCode}</span>
          </div>
          {r.locations.length > 1 && (
            <div className="cell-sub">
              {r.locations.map((l) => `${l.locationCode} ${qty(l.quantity, r.unitDecimals)}`).join(' · ')}
            </div>
          )}
          {r.locations.length === 1 && <div className="cell-sub">{r.locations[0].locationName}</div>}
        </>
      ),
    },
    { title: t('store.avgRate'), dataIndex: 'averageRate', width: 120, align: 'right', responsive: ['lg'], render: (v?: number) => money(v) },
    { title: t('store.value'), dataIndex: 'value', width: 140, align: 'right', responsive: ['md'], render: (v: number) => money(v) },
    {
      title: t('store.reorderLevel'),
      dataIndex: 'reorderLevel',
      width: 130,
      align: 'right',
      responsive: ['sm'],
      render: (v: number | undefined, r) =>
        v == null ? <span className="cell-sub">–</span> : (
          <span className={r.low ? 'low-pill' : 'cell-sub'}>
            {r.low && <WarningOutlined />} {qty(v, r.unitDecimals)}
          </span>
        ),
    },
  ]

  const tiles: { key: Show | 'value'; icon: React.ReactNode; label: string; value: number; tint: [string, string]; prefix?: string }[] = [
    { key: 'value', icon: <WalletOutlined />, label: t('store.stockValue'), value: totals.value, tint: ['#FBF3EE', '#A84A23'], prefix: '₹ ' },
    { key: 'inStock', icon: <DatabaseOutlined />, label: t('store.materialsInStock'), value: totals.inStock, tint: ['#E9F3F3', '#285A5B'] },
    { key: 'low', icon: <WarningOutlined />, label: t('store.lowStock'), value: totals.low, tint: ['#FDF1E4', '#B45309'] },
  ]

  return (
    <>
      <PageHeader
        title={t('store.stockTitle')}
        subtitle={t('store.stockSubtitle', { plant: currentPlant.name })}
        icon={<InboxOutlined />}
        extra={actions}
      />

      <Stagger className="stat-grid">
        {tiles.map((tile) => (
          <StaggerItem key={tile.key}>
            {/* Clicking "Materials in stock" or "Low stock" filters the table below. */}
            <button
              type="button"
              className={`stat-card stat-button${show === tile.key ? ' selected' : ''}`}
              onClick={() => tile.key !== 'value' && setShow(show === tile.key ? 'all' : tile.key)}
            >
              <span className="stat-icon" style={{ background: tile.tint[0], color: tile.tint[1] }}>{tile.icon}</span>
              <span>
                <div className="stat-value">
                  {tile.prefix}
                  {data ? <CountUp value={tile.value} /> : '–'}
                </div>
                <div className="stat-label">{tile.label}</div>
              </span>
            </button>
          </StaggerItem>
        ))}
      </Stagger>

      <FadeIn delay={0.05} className="toolbar">
        <Input.Search
          allowClear
          placeholder={t('store.searchMaterial')}
          onChange={(e) => setSearch(e.target.value)}
          style={{ maxWidth: 280 }}
        />
        <Select
          allowClear
          placeholder={t('store.allGroups')}
          aria-label={t('store.allGroups')}
          value={group}
          onChange={setGroup}
          style={{ minWidth: 200 }}
          options={groups.map((g) => ({ value: g, label: g }))}
        />
        <Segmented<Show>
          value={show}
          onChange={setShow}
          options={[
            { value: 'all', label: t('store.showAll') },
            { value: 'inStock', label: t('store.showInStock') },
            { value: 'low', label: t('store.showLow') },
          ]}
        />
      </FadeIn>

      <FadeIn delay={0.1}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<StockRow>
            rowKey="materialId"
            columns={columns}
            dataSource={rows}
            loading={isLoading}
            rowClassName={(r) => `clickable-row${r.low ? ' row-low' : ''}`}
            onRow={(r) => ({ onClick: () => setLedgerRow(r) })}
            pagination={{ pageSize: 50, hideOnSinglePage: true, showSizeChanger: false }}
            locale={{
              emptyText: isLoading ? ' ' : data?.length
                ? <EmptyState title={t('store.emptyFilter')} />
                : <EmptyState title={t('store.emptyStock')} hint={t('store.emptyStockHint')} />,
            }}
          />
        </Card>
      </FadeIn>

      <LedgerDrawer plantId={currentPlant.id} row={ledgerRow} onClose={() => setLedgerRow(null)} />
    </>
  )
}
