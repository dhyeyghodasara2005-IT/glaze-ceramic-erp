import { useCallback, useState } from 'react'
import { App, Button, Card, Popconfirm, Space, Table, Tag, type TableColumnsType } from 'antd'
import { DeleteOutlined, EditOutlined, PercentageOutlined, PlusOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import dayjs from 'dayjs'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useOpenFromUrl } from '../../components/useOpenFromUrl'
import { useAuth } from '../../auth/AuthContext'
import { errorMessage } from '../../api/errors'
import type { CostRate } from '../../api/types'
import NoPlant from '../store/NoPlant'
import { fmt } from '../dashboard/charts'
import { useUnit } from '../energy/energyParts'
import { useCostRates, useDeleteCostRate } from './costingApi'
import CostRateDrawer from './CostRateDrawer'

/** /costing/rates - what a unit of gas, power and fuel costs, from which date. */
export default function CostRatesPage() {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const { can, currentPlant } = useAuth()
  const canManage = can('COST_MANAGE')
  const unit = useUnit()
  const rates = useCostRates(currentPlant?.id)
  const remove = useDeleteCostRate()
  const [editing, setEditing] = useState<{ rate?: CostRate } | null>(null)
  const openFromUrl = useCallback((item?: CostRate) => setEditing(item ? { rate: item } : {}), [])
  useOpenFromUrl({ items: rates.data, onOpen: openFromUrl, enabled: canManage })
  if (!currentPlant) return <NoPlant />

  // the rate in force today: the latest one that has started, per fuel and unit (the list is newest first)
  const today = dayjs()
  const current = new Set<number>()
  const seen = new Set<string>()
  for (const r of rates.data ?? []) {
    const key = `${r.kind}/${r.unit}`
    if (!seen.has(key) && !dayjs(r.validFrom).isAfter(today, 'day')) {
      seen.add(key)
      current.add(r.id)
    }
  }

  const onDelete = async (id: number) => {
    try {
      await remove.mutateAsync(id)
      message.success(t('costing.rateDeleted'))
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const newButton = canManage && (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({})}>{t('costing.newRate')}</Button>
  )
  const columns: TableColumnsType<CostRate> = [
    {
      title: t('costing.fuel'),
      key: 'fuel',
      render: (_, r) => (
        <>
          <span className="cell-title">{t(`energy.kinds.${r.kind}`)}</span>{' '}
          {current.has(r.id) && <Tag color="green" bordered={false}>{t('costing.inForce')}</Tag>}
          {r.note && <div className="cell-sub">{r.note}</div>}
        </>
      ),
    },
    { title: t('costing.rate'), key: 'rate', width: 170, align: 'right', render: (_, r) => <b>₹ {fmt(r.rate, 4)} / {unit(r.unit)}</b> },
    { title: t('costing.validFrom'), dataIndex: 'validFrom', width: 140, render: (d: string) => dayjs(d).format('D MMM YYYY') },
    { title: t('costing.enteredBy'), dataIndex: 'by', width: 140, responsive: ['md'] },
  ]
  if (canManage) {
    columns.push({
      key: 'actions',
      width: 112,
      render: (_, rate) => (
        <Space size={0}>
          <Button type="text" icon={<EditOutlined />} aria-label={t('common.edit')} onClick={() => setEditing({ rate })} />
          <Popconfirm title={t('costing.deleteRateConfirm')} okText={t('common.delete')} cancelText={t('common.cancel')} onConfirm={() => onDelete(rate.id)}>
            <Button type="text" danger icon={<DeleteOutlined />} aria-label={t('common.delete')} />
          </Popconfirm>
        </Space>
      ),
    })
  }

  return (
    <>
      <PageHeader title={t('costing.ratesTitle')} subtitle={t('costing.ratesSubtitle')} icon={<PercentageOutlined />} extra={newButton} />
      <FadeIn delay={0.08}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<CostRate> rowKey="id" columns={columns} dataSource={rates.data ?? []} loading={rates.isLoading} pagination={false}
            locale={{ emptyText: rates.isLoading ? ' ' : <EmptyState title={t('costing.noRates')} hint={t('costing.noRatesHint')} action={newButton} /> }} />
        </Card>
      </FadeIn>
      <CostRateDrawer open={editing !== null} plantId={currentPlant.id} rate={editing?.rate} onClose={() => setEditing(null)} />
    </>
  )
}
