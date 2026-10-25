import { useCallback, useState } from 'react'
import { Button, Card, Switch, Table, Tag, type TableColumnsType } from 'antd'
import { DashboardOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../components/PageHeader'
import StatusTag from '../../components/StatusTag'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useOpenFromUrl } from '../../components/useOpenFromUrl'
import { useAuth } from '../../auth/AuthContext'
import type { Meter } from '../../api/types'
import NoPlant from '../store/NoPlant'
import { useMeters } from './energyApi'
import { KindTag } from './energyParts'
import MeterFormDrawer from './MeterFormDrawer'

/** /masters/meters - the plant's meters (and the fuels that are weighed): what each measures and how often it's read. */
export default function MetersPage() {
  const { t } = useTranslation()
  const { can, currentPlant } = useAuth()
  const canManage = can('MASTER_MANAGE')
  const [showOff, setShowOff] = useState(false)
  const [editing, setEditing] = useState<{ meter?: Meter } | null>(null)
  const meters = useMeters(currentPlant?.id, showOff)
  const openFromUrl = useCallback((item?: Meter) => setEditing(item ? { meter: item } : {}), [])
  useOpenFromUrl({ items: meters.data, onOpen: openFromUrl, enabled: canManage })
  if (!currentPlant) return <NoPlant />

  const newButton = canManage && (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({})}>{t('energy.newMeter')}</Button>
  )
  const columns: TableColumnsType<Meter> = [
    { title: t('common.code'), dataIndex: 'code', width: 140, render: (code: string) => <span className="code-badge">{code}</span> },
    {
      title: t('common.name'),
      dataIndex: 'name',
      render: (name: string, m) => (
        <>
          <div className="cell-title">{name} {m.main && <Tag color="volcano" bordered={false}>{t('energy.mainTag')}</Tag>}</div>
          {m.machine && <div className="cell-sub">{m.machine.name} · {m.machine.code}</div>}
        </>
      ),
    },
    { title: t('energy.kind'), key: 'kind', width: 200, render: (_, m) => <KindTag meter={m} /> },
    { title: t('energy.readEvery'), dataIndex: 'readEvery', width: 170, responsive: ['md'], render: (e: Meter['readEvery']) => t(`energy.every.${e}`) },
    { title: t('common.status'), dataIndex: 'active', width: 110, responsive: ['sm'], render: (a: boolean) => <StatusTag active={a} /> },
  ]
  if (canManage) {
    columns.push({
      key: 'actions',
      width: 56,
      render: (_, meter) => <Button type="text" icon={<EditOutlined />} aria-label={t('common.edit')} onClick={() => setEditing({ meter })} />,
    })
  }

  return (
    <>
      <PageHeader title={t('energy.metersTitle')} subtitle={t('energy.metersSubtitle')} icon={<DashboardOutlined />} extra={newButton} />
      <FadeIn delay={0.05} className="toolbar">
        <label className="pm-toggle"><Switch size="small" checked={showOff} onChange={setShowOff} /> {t('energy.showOff')}</label>
      </FadeIn>
      <FadeIn delay={0.1}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<Meter>
            rowKey="id"
            columns={columns}
            dataSource={meters.data ?? []}
            loading={meters.isLoading}
            pagination={false}
            locale={{ emptyText: meters.isLoading ? ' ' : <EmptyState title={t('energy.noMeters')} hint={t('energy.noMetersHint')} action={newButton} /> }}
            onRow={(meter) => (canManage ? { onDoubleClick: () => setEditing({ meter }) } : {})}
          />
        </Card>
      </FadeIn>
      <MeterFormDrawer open={editing !== null} plantId={currentPlant.id} meter={editing?.meter} onClose={() => setEditing(null)} />
    </>
  )
}
