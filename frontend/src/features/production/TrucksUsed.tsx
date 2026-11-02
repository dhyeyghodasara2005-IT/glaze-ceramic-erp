import { Tag } from 'antd'
import { GoldOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../auth/AuthContext'
import { qty, shortDate } from '../store/format'
import { useTrucks } from './api'

/** The trucks of raw material behind a mill charge or a design lot, with what the lab or the GRN found. */
export default function TrucksUsed({ kind, id }: { kind: 'body-batches' | 'lots'; id: number }) {
  const { t } = useTranslation()
  const { can } = useAuth()
  const trucks = useTrucks(kind, id)
  if (!trucks.data) return null
  return (
    <>
      <div className="form-section"><GoldOutlined /> {t('lots.trucksTitle')}</div>
      {trucks.data.length === 0 && <div className="cell-sub">{t('lots.trucksEmpty')}</div>}
      {trucks.data.length > 0 && (
        <div className="panel-hint" style={{ margin: '0 0 10px' }}>{t(kind === 'lots' ? 'lots.trucksHint' : 'lots.trucksBatchHint')}</div>
      )}
      <div className="trace-list">
        {trucks.data.map((x) => (
          <div key={x.lotId} className="trace-row">
            <span className="trace-tonnes">{qty(x.quantity, x.unitDecimals)} {x.unitCode}</span>
            <span>
              <span className="cell-title">{x.materialName}</span>{' '}
              {can('STORE_VIEW')
                ? <Link to={`/store/lots?lot=${x.lotId}`} className="code-badge">{x.lotNo}</Link>
                : <span className="code-badge">{x.lotNo}</span>}
              {x.oldStock && <Tag bordered={false} style={{ marginInlineStart: 8 }}>{t('lots.oldStock')}</Tag>}
              <div className="cell-sub">
                {[x.supplierName, x.vehicleNo, shortDate(x.receivedOn),
                  x.moisturePct != null ? `${t('lots.moisture')} ${qty(x.moisturePct, 2)} %` : null,
                  x.dryQuantity != null ? t('lots.dryIn', { qty: `${qty(x.dryQuantity, x.unitDecimals)} ${x.unitCode}` }) : null].filter(Boolean).join(' · ')}
              </div>
              {kind === 'lots' && (
                <div className="trace-batches">{x.batches.map((b) => <Tag key={b}>{b}</Tag>)}</div>
              )}
            </span>
          </div>
        ))}
      </div>
    </>
  )
}
