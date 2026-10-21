import { useCallback, useState } from 'react'
import { useOpenFromUrl } from '../../../components/useOpenFromUrl'
import { Button, Card, Table, type TableColumnsType } from 'antd'
import { BorderOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../../components/PageHeader'
import StatusTag from '../../../components/StatusTag'
import EmptyState from '../../../components/EmptyState'
import { FadeIn } from '../../../components/motion'
import { useAuth } from '../../../auth/AuthContext'
import type { TileSize } from '../../../api/types'
import { useSizes } from './api'
import SizeFormDrawer from './SizeFormDrawer'

const num = (v?: number, digits = 0) =>
  v == null ? '-' : Number(v).toLocaleString('en-IN', { minimumFractionDigits: digits, maximumFractionDigits: digits })

export default function SizesPage() {
  const { t } = useTranslation()
  const { can } = useAuth()
  const canManage = can('MASTER_MANAGE')
  const { data, isLoading } = useSizes()
  const [editing, setEditing] = useState<{ size?: TileSize } | null>(null)
  // Search palette / links: ?open=<id> edits a record, ?new=1 opens the empty form.
  const openFromUrl = useCallback((item?: TileSize) => setEditing(item ? { size: item } : {}), [])
  useOpenFromUrl({ items: data, onOpen: openFromUrl, enabled: canManage })

  const newButton = canManage && (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({})}>
      {t('size.new')}
    </Button>
  )

  const columns: TableColumnsType<TileSize> = [
    { title: t('size.code'), dataIndex: 'code', width: 130, render: (code: string) => <span className="code-badge">{code}</span> },
    {
      title: t('size.name'),
      dataIndex: 'name',
      render: (name: string, s) => (
        <>
          <div className="cell-title">{name}</div>
          <div className="cell-sub">
            {s.lengthMm} × {s.widthMm} mm · {num(s.thicknessMm, 1)} mm
          </div>
        </>
      ),
    },
    { title: t('size.pcsPerBox'), dataIndex: 'pcsPerBox', align: 'right', width: 130, responsive: ['sm'] },
    {
      title: t('size.sqmPerBox'),
      dataIndex: 'sqmPerBox',
      align: 'right',
      width: 140,
      render: (v: number) => <strong>{num(v, 4)}</strong>,
    },
    { title: t('size.boxWeight'), dataIndex: 'boxWeightKg', align: 'right', responsive: ['lg'], render: (v?: number) => num(v, 2) },
    { title: t('size.boxesPerPallet'), dataIndex: 'boxesPerPallet', align: 'right', responsive: ['xl'], render: (v?: number) => num(v) },
    { title: t('common.status'), dataIndex: 'active', width: 110, responsive: ['md'], render: (a: boolean) => <StatusTag active={a} /> },
  ]
  if (canManage) {
    columns.push({
      key: 'actions',
      width: 56,
      render: (_, size) => (
        <Button type="text" icon={<EditOutlined />} aria-label={t('common.edit')} onClick={() => setEditing({ size })} />
      ),
    })
  }

  return (
    <>
      <PageHeader title={t('size.title')} subtitle={t('size.subtitle')} icon={<BorderOutlined />} extra={newButton} />
      <FadeIn delay={0.08}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<TileSize>
            rowKey="id"
            columns={columns}
            dataSource={data}
            loading={isLoading}
            pagination={false}
            locale={{ emptyText: isLoading ? ' ' : <EmptyState title={t('size.empty')} action={newButton} /> }}
            onRow={(size) => (canManage ? { onDoubleClick: () => setEditing({ size }) } : {})}
          />
        </Card>
      </FadeIn>
      <SizeFormDrawer open={editing !== null} size={editing?.size} onClose={() => setEditing(null)} />
    </>
  )
}
