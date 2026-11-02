import { useCallback, useState } from 'react'
import { Button, Card, Input, Segmented, Select, Table, Tag, type TableColumnsType } from 'antd'
import { EditOutlined, IdcardOutlined, PlusOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../components/PageHeader'
import StatusTag from '../../components/StatusTag'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useOpenFromUrl } from '../../components/useOpenFromUrl'
import { useAuth } from '../../auth/AuthContext'
import { DEPARTMENTS, type Department, type Worker } from '../../api/types'
import NoPlant from '../store/NoPlant'
import { fetchWorker, useContractors, useWorkers } from './labourApi'
import { rupees } from './labourParts'
import WorkerFormDrawer from './WorkerFormDrawer'

type ActiveFilter = 'all' | 'active' | 'inactive'

/** /labour/workers - who works in the plant, under which contractor, in which department, and what a day costs. */
export default function WorkersPage() {
  const { t } = useTranslation()
  const { can, currentPlant } = useAuth()
  const canManage = can('LABOUR_MANAGE')
  const [search, setSearch] = useState<string>()
  const [department, setDepartment] = useState<Department>()
  const [contractorId, setContractorId] = useState<number>()
  const [activeFilter, setActiveFilter] = useState<ActiveFilter>('active')
  const workers = useWorkers(currentPlant ? {
    plantId: currentPlant.id, search, department, contractorId,
    active: activeFilter === 'all' ? undefined : activeFilter === 'active',
  } : null)
  const contractors = useContractors()
  const [editing, setEditing] = useState<{ worker?: Worker } | null>(null)
  const openFromUrl = useCallback((item?: Worker) => setEditing(item ? { worker: item } : {}), [])
  useOpenFromUrl({ fetchOne: fetchWorker, onOpen: openFromUrl, enabled: canManage })
  if (!currentPlant) return <NoPlant />

  const newButton = canManage && (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({})}>{t('labour.newWorker')}</Button>
  )
  const columns: TableColumnsType<Worker> = [
    { title: t('common.code'), dataIndex: 'code', width: 110, render: (code: string) => <span className="code-badge">{code}</span> },
    {
      title: t('common.name'),
      dataIndex: 'name',
      render: (name: string, w) => (
        <>
          <div className="cell-title">{name}</div>
          <div className="cell-sub">{[t(`labour.dept.${w.department}`), w.job].filter(Boolean).join(' · ')}</div>
        </>
      ),
    },
    {
      title: t('labour.contractor'),
      key: 'contractor',
      responsive: ['md'],
      render: (_, w) => (w.contractor ? w.contractor.name : <span className="cell-sub">{t('labour.ownRolls')}</span>),
    },
    {
      title: t('labour.punchLabel'),
      key: 'punch',
      width: 160,
      responsive: ['lg'],
      render: (_, w) => (
        <>
          <Tag bordered={false}>{t(`labour.punch.${w.punch}`)}</Tag>
          {w.deviceId && <span className="cell-sub">#{w.deviceId}</span>}
        </>
      ),
    },
    {
      title: t('labour.rates'),
      key: 'rates',
      width: 150,
      align: 'right',
      responsive: ['sm'],
      render: (_, w) => (w.dayRate == null ? <Tag color="orange" bordered={false}>{t('labour.noRate')}</Tag> : (
        <>
          <div>{t('labour.perDay', { n: rupees(w.dayRate) })}</div>
          {w.otRate != null && <div className="cell-sub">{t('labour.perOtHour', { n: rupees(w.otRate) })}</div>}
        </>
      )),
    },
    { title: t('common.status'), dataIndex: 'active', width: 110, responsive: ['sm'], render: (a: boolean) => <StatusTag active={a} /> },
  ]
  if (canManage) {
    columns.push({
      key: 'actions',
      width: 56,
      render: (_, worker) => <Button type="text" icon={<EditOutlined />} aria-label={t('common.edit')} onClick={() => setEditing({ worker })} />,
    })
  }

  return (
    <>
      <PageHeader title={t('labour.workersTitle')} subtitle={t('labour.workersSubtitle')} icon={<IdcardOutlined />} extra={newButton} />
      <FadeIn delay={0.05} className="toolbar">
        <Input.Search allowClear placeholder={t('labour.searchWorkers')} onSearch={(v) => setSearch(v.trim() || undefined)}
          style={{ maxWidth: 280 }} />
        <Select<Department> allowClear placeholder={t('labour.allDepartments')} value={department} onChange={setDepartment}
          style={{ minWidth: 180 }} options={DEPARTMENTS.map((d) => ({ value: d, label: t(`labour.dept.${d}`) }))} />
        <Select<number> allowClear showSearch={{ optionFilterProp: 'label' }} placeholder={t('labour.allContractors')}
          value={contractorId} onChange={setContractorId} style={{ minWidth: 200 }}
          options={(contractors.data ?? []).map((c) => ({ value: c.id, label: c.name }))} />
        <Segmented<ActiveFilter> value={activeFilter} onChange={setActiveFilter} options={[
          { value: 'active', label: t('common.active') },
          { value: 'inactive', label: t('labour.left') },
          { value: 'all', label: t('common.all') },
        ]} />
      </FadeIn>
      <FadeIn delay={0.1}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<Worker>
            rowKey="id"
            columns={columns}
            dataSource={workers.data ?? []}
            loading={workers.isFetching}
            pagination={{ pageSize: 50, showSizeChanger: false, hideOnSinglePage: true }}
            locale={{ emptyText: workers.isFetching ? ' ' : <EmptyState title={t('labour.noWorkers')} hint={t('labour.noWorkersHint')} action={newButton} /> }}
            onRow={(worker) => (canManage ? { onDoubleClick: () => setEditing({ worker }) } : {})}
          />
        </Card>
      </FadeIn>
      <WorkerFormDrawer open={editing !== null} plantId={currentPlant.id} worker={editing?.worker} onClose={() => setEditing(null)} />
    </>
  )
}
