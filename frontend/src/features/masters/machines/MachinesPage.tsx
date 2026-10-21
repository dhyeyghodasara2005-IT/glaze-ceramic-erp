import { useCallback, useState } from 'react'
import { Button, Card, Select, Table, Tag, type TableColumnsType } from 'antd'
import { EditOutlined, PlusOutlined, ToolOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../../components/PageHeader'
import StatusTag from '../../../components/StatusTag'
import EmptyState from '../../../components/EmptyState'
import { FadeIn } from '../../../components/motion'
import { useOpenFromUrl } from '../../../components/useOpenFromUrl'
import { useAuth } from '../../../auth/AuthContext'
import { MACHINE_TYPES, type Machine, type MachineType } from '../../../api/types'
import { useMasterList } from '../masterApi'
import MachineFormDrawer from './MachineFormDrawer'

/** Tag colour by process stage, so a line-up reads like the factory floor. */
const STAGE_COLOR: Record<MachineType, string> = {
  BALL_MILL: 'gold',
  SPRAY_DRYER: 'gold',
  PRESS: 'blue',
  DRYER: 'blue',
  GLAZE_LINE: 'purple',
  DIGITAL_PRINTER: 'purple',
  KILN: 'volcano',
  POLISHING_LINE: 'green',
  SORTING_LINE: 'green',
  PACKING_LINE: 'green',
  UTILITY: 'default',
  OTHER: 'default',
}

const ALL = 0 // "all plants" option value

export default function MachinesPage() {
  const { t } = useTranslation()
  const { can, user, currentPlant } = useAuth()
  const canManage = can('MASTER_MANAGE')
  const { data, isLoading } = useMasterList<Machine>('machines')

  // Start with the plant chosen in the header; the user can switch to another or to all.
  const [plantFilter, setPlantFilter] = useState<number>(currentPlant?.id ?? ALL)
  const [typeFilter, setTypeFilter] = useState<MachineType>()
  const rows = (data ?? []).filter(
    (m) => (plantFilter === ALL || m.plantId === plantFilter) && (!typeFilter || m.type === typeFilter),
  )

  const [editing, setEditing] = useState<{ machine?: Machine } | null>(null)
  const openFromUrl = useCallback((item?: Machine) => setEditing(item ? { machine: item } : {}), [])
  useOpenFromUrl({ items: data, onOpen: openFromUrl, enabled: canManage })

  const newButton = canManage && (user?.plants.length ?? 0) > 0 && (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({})}>
      {t('machine.new')}
    </Button>
  )

  const columns: TableColumnsType<Machine> = [
    { title: t('common.code'), dataIndex: 'code', width: 130, render: (code: string) => <span className="code-badge">{code}</span> },
    {
      title: t('common.name'),
      dataIndex: 'name',
      render: (name: string, m) => (
        <>
          <div className="cell-title">{name}</div>
          {(m.make || m.model) && <div className="cell-sub">{[m.make, m.model].filter(Boolean).join(' · ')}</div>}
        </>
      ),
    },
    {
      title: t('machine.type'),
      dataIndex: 'type',
      width: 170,
      render: (type: MachineType) => <Tag color={STAGE_COLOR[type]}>{t(`machineType.${type}`)}</Tag>,
    },
    { title: t('common.plant'), dataIndex: 'plantCode', width: 110, responsive: ['lg'] },
    {
      title: t('machine.capacity'),
      key: 'capacity',
      width: 170,
      align: 'right',
      responsive: ['md'],
      render: (_, m) => (m.capacity == null ? '-' : `${Number(m.capacity).toLocaleString('en-IN')} ${m.capacityUnit ?? ''}`),
    },
    { title: t('common.status'), dataIndex: 'active', width: 110, responsive: ['sm'], render: (a: boolean) => <StatusTag active={a} /> },
  ]
  if (canManage) {
    columns.push({
      key: 'actions',
      width: 56,
      render: (_, machine) => (
        <Button type="text" icon={<EditOutlined />} aria-label={t('common.edit')} onClick={() => setEditing({ machine })} />
      ),
    })
  }

  return (
    <>
      <PageHeader title={t('machine.title')} subtitle={t('machine.subtitle')} icon={<ToolOutlined />} extra={newButton} />

      <FadeIn delay={0.05} className="toolbar">
        {(user?.plants.length ?? 0) > 1 && (
          <Select
            value={plantFilter}
            onChange={setPlantFilter}
            aria-label={t('common.plant')}
            style={{ minWidth: 180 }}
            options={[
              { value: ALL, label: t('common.allPlants') },
              ...(user?.plants ?? []).map((p) => ({ value: p.id, label: p.name })),
            ]}
          />
        )}
        <Select
          allowClear
          placeholder={t('machine.allTypes')}
          aria-label={t('machine.allTypes')}
          value={typeFilter}
          onChange={setTypeFilter}
          style={{ minWidth: 180 }}
          options={MACHINE_TYPES.map((type) => ({ value: type, label: t(`machineType.${type}`) }))}
        />
      </FadeIn>

      <FadeIn delay={0.1}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<Machine>
            rowKey="id"
            columns={columns}
            dataSource={rows}
            loading={isLoading}
            pagination={false}
            locale={{ emptyText: isLoading ? ' ' : <EmptyState title={t('machine.empty')} hint={t('machine.emptyHint')} action={newButton} /> }}
            onRow={(machine) => (canManage ? { onDoubleClick: () => setEditing({ machine }) } : {})}
          />
        </Card>
      </FadeIn>

      <MachineFormDrawer open={editing !== null} machine={editing?.machine} onClose={() => setEditing(null)} />
    </>
  )
}
