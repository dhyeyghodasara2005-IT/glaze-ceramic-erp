import { Card, Empty, Spin } from 'antd'
import { CarOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../components/PageHeader'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import type { SalesOrderSummary } from '../../api/types'
import NoPlant from '../store/NoPlant'
import { shortDate } from '../store/format'
import { useSalesOrders } from './salesApi'

/** /sales/loading - the loader's home: confirmed orders waiting for their truck, with how far each is loaded. */
export default function LoadingListPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { currentPlant } = useAuth()
  const { data, isLoading } = useSalesOrders(currentPlant
    ? { plantId: currentPlant.id, status: 'CONFIRMED', page: 0, size: 100 }
    : null)

  if (!currentPlant) return <NoPlant />

  return (
    <>
      <PageHeader title={t('loading.listTitle')} subtitle={t('loading.listSubtitle')} icon={<CarOutlined />} />
      {isLoading && <div className="center-block"><Spin size="large" /></div>}
      {data?.content.length === 0 && <Card className="data-card"><Empty description={t('loading.listEmpty')} /></Card>}
      <div className="loading-cards">
        {data?.content.map((o, i) => (
          <FadeIn key={o.id} delay={0.03 * i}>
            <button type="button" className="loading-card" onClick={() => navigate(`/sales/orders/${o.id}/loading`)}>
              <OrderCard order={o} />
            </button>
          </FadeIn>
        ))}
      </div>
    </>
  )
}

function OrderCard({ order: o }: { order: SalesOrderSummary }) {
  const { t } = useTranslation()
  const pct = o.boxes ? Math.min(100, Math.round((o.loadedBoxes / o.boxes) * 100)) : 0
  return (
    <>
      <div className="loading-card-top">
        <span className="code-badge">{o.slipNo}</span>
        <span className="cell-sub">{shortDate(o.slipDate)}</span>
        {o.truckNo && <span className="loading-card-truck"><CarOutlined /> {o.truckNo}</span>}
      </div>
      <div className="cell-title">{o.partyName}</div>
      <div className="cell-sub">
        {t('sales.productsN', { count: o.lines })} · {t('finishing.boxesN', { n: o.boxes.toLocaleString('en-IN') })}
        {o.plannedBoxes < o.boxes && <span className="short"> · {t('loading.notPlannedN', { n: (o.boxes - o.plannedBoxes).toLocaleString('en-IN') })}</span>}
      </div>
      <div className="load-progress">
        <div className="load-progress-bar"><span style={{ width: `${pct}%` }} /></div>
        <span className="load-progress-text">{t('loading.progress', { loaded: o.loadedBoxes.toLocaleString('en-IN'), total: o.boxes.toLocaleString('en-IN') })}</span>
      </div>
    </>
  )
}
