import { useCallback, useState } from 'react'
import { useOpenFromUrl } from '../../components/useOpenFromUrl'
import { Button, Card, Table, type TableColumnsType } from 'antd'
import { BankOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../components/PageHeader'
import StatusTag from '../../components/StatusTag'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import type { Company } from '../../api/types'
import { useCompanies } from './api'
import CompanyFormDrawer from './CompanyFormDrawer'

export default function CompaniesPage() {
  const { t } = useTranslation()
  const { can } = useAuth()
  const canManage = can('COMPANY_MANAGE')
  const { data, isLoading } = useCompanies()
  // drawer state: closed / new / editing a company
  const [editing, setEditing] = useState<{ company?: Company } | null>(null)
  // Search palette / links: ?open=<id> edits a record, ?new=1 opens the empty form.
  const openFromUrl = useCallback((item?: Company) => setEditing(item ? { company: item } : {}), [])
  useOpenFromUrl({ items: data, onOpen: openFromUrl, enabled: canManage })

  const newButton = canManage && (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({})}>
      {t('company.new')}
    </Button>
  )

  const columns: TableColumnsType<Company> = [
    { title: t('company.code'), dataIndex: 'code', width: 120, render: (code: string) => <span className="code-badge">{code}</span> },
    {
      title: t('company.name'),
      dataIndex: 'name',
      render: (name: string, c) => (
        <>
          <div className="cell-title">{name}</div>
          {c.legalName && <div className="cell-sub">{c.legalName}</div>}
        </>
      ),
    },
    { title: t('company.gstin'), dataIndex: 'gstin', responsive: ['md'], render: (v?: string) => v ?? '-' },
    { title: t('company.city'), dataIndex: 'city', responsive: ['lg'], render: (v?: string) => v ?? '-' },
    { title: t('common.status'), dataIndex: 'active', width: 110, render: (active: boolean) => <StatusTag active={active} /> },
  ]
  if (canManage) {
    columns.push({
      key: 'actions',
      width: 56,
      render: (_, company) => (
        <Button type="text" icon={<EditOutlined />} aria-label={t('common.edit')} onClick={() => setEditing({ company })} />
      ),
    })
  }

  return (
    <>
      <PageHeader title={t('company.title')} subtitle={t('company.subtitle')} icon={<BankOutlined />} extra={newButton} />
      <FadeIn delay={0.08}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<Company>
            rowKey="id"
            columns={columns}
            dataSource={data}
            loading={isLoading}
            pagination={false}
            locale={{ emptyText: isLoading ? ' ' : <EmptyState title={t('company.empty')} action={newButton} /> }}
            onRow={(company) => (canManage ? { onDoubleClick: () => setEditing({ company }) } : {})}
          />
        </Card>
      </FadeIn>
      <CompanyFormDrawer open={editing !== null} company={editing?.company} onClose={() => setEditing(null)} />
    </>
  )
}
