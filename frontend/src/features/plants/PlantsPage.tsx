import { useState } from 'react'
import { Button, Card, Flex, Select, Table, type TableColumnsType } from 'antd'
import { ApartmentOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../components/PageHeader'
import StatusTag from '../../components/StatusTag'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import type { Plant } from '../../api/types'
import { useCompanies } from '../companies/api'
import { usePlants } from './api'
import PlantFormDrawer from './PlantFormDrawer'

export default function PlantsPage() {
  const { t } = useTranslation()
  const { can } = useAuth()
  const canManage = can('PLANT_MANAGE')
  const [companyId, setCompanyId] = useState<number | undefined>()
  const { data, isLoading } = usePlants(companyId)
  const companies = useCompanies(can('COMPANY_VIEW'))
  const [editing, setEditing] = useState<{ plant?: Plant } | null>(null)

  const newButton = canManage && (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({})}>
      {t('plant.new')}
    </Button>
  )

  const columns: TableColumnsType<Plant> = [
    { title: t('plant.code'), dataIndex: 'code', width: 130, render: (code: string) => <span className="code-badge">{code}</span> },
    {
      title: t('plant.name'),
      dataIndex: 'name',
      render: (name: string, p) => (
        <>
          <div className="cell-title">{name}</div>
          <div className="cell-sub">{[p.companyName, p.city].filter(Boolean).join(' · ')}</div>
        </>
      ),
    },
    {
      title: t('plant.capacity'),
      dataIndex: 'capacitySqmPerDay',
      align: 'right',
      responsive: ['md'],
      render: (v?: number) => (v == null ? '-' : v.toLocaleString('en-IN')),
    },
    { title: t('common.status'), dataIndex: 'active', width: 110, render: (a: boolean) => <StatusTag active={a} /> },
  ]
  if (canManage) {
    columns.push({
      key: 'actions',
      width: 56,
      render: (_, plant) => (
        <Button type="text" icon={<EditOutlined />} aria-label={t('common.edit')} onClick={() => setEditing({ plant })} />
      ),
    })
  }

  return (
    <>
      <PageHeader
        title={t('plant.title')}
        subtitle={t('plant.subtitle')}
        icon={<ApartmentOutlined />}
        extra={
          <Flex gap={8} wrap>
            {companies.data && companies.data.length > 1 && (
              <Select
                allowClear
                placeholder={t('plant.allCompanies')}
                value={companyId}
                onChange={setCompanyId}
                style={{ minWidth: 180 }}
                options={companies.data.map((c) => ({ value: c.id, label: c.name }))}
              />
            )}
            {newButton}
          </Flex>
        }
      />
      <FadeIn delay={0.08}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<Plant>
            rowKey="id"
            columns={columns}
            dataSource={data}
            loading={isLoading}
            pagination={false}
            locale={{ emptyText: isLoading ? ' ' : <EmptyState title={t('plant.empty')} action={newButton} /> }}
            onRow={(plant) => (canManage ? { onDoubleClick: () => setEditing({ plant }) } : {})}
          />
        </Card>
      </FadeIn>
      <PlantFormDrawer open={editing !== null} plant={editing?.plant} onClose={() => setEditing(null)} />
    </>
  )
}
