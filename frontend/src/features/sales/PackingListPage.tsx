import { useEffect } from 'react'
import { Button, Spin } from 'antd'
import { PrinterOutlined } from '@ant-design/icons'
import { useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { BRAND } from '../../brand'
import type { SalesOrder, SalesPick } from '../../api/types'
import { qty, shortDate } from '../store/format'
import { useSalesOrder } from './salesApi'

/** The six sign-offs printed on your loading slips, kept on the packing list. */
const SIGN_OFFS = ['logistics', 'qc', 'loadingSupervisor', 'driver', 'security', 'warehouse'] as const

/**
 * /print/sales-orders/12 - packing list and gate pass of a dispatched order, on one A4 page, outside the app's
 * frame. Every batch, shade and caliber loaded is listed: dealers need the shade to re-order.
 */
export default function PackingListPage() {
  const { id } = useParams()
  const { t } = useTranslation()
  const { data: order, isLoading } = useSalesOrder(Number(id))
  useEffect(() => {
    if (order) document.title = `${t('print.packingList')} ${order.dispatchNo ?? order.slipNo}`
  }, [order, t])

  if (isLoading) return <div className="center-block"><Spin size="large" /></div>
  if (!order) return <div className="print-page">{t('store.notFound')}</div>

  const loaded = order.picks.filter((p) => (p.loadedBoxes ?? 0) > 0)
  const per = (p: SalesPick, field: 'sqm' | 'weightKg') => {
    const line = order.lines.find((l) => l.id === p.lineId)
    const total = line?.[field]
    return total == null || !line?.boxes ? undefined : (total / line.boxes) * (p.loadedBoxes ?? 0)
  }
  const sum = (field: 'sqm' | 'weightKg') => {
    const parts = loaded.map((p) => per(p, field))
    return parts.some((v) => v === undefined) ? undefined : parts.reduce((s, v) => s! + v!, 0)
  }
  const boxes = order.totals.loadedBoxes

  return (
    <div className="print-page">
      <div className="print-actions no-print">
        <Button type="primary" icon={<PrinterOutlined />} onClick={() => window.print()}>{t('print.print')}</Button>
        {order.status !== 'DISPATCHED' && <span className="cell-sub">{t('print.notDispatched')}</span>}
      </div>

      <header className="print-head">
        <div>
          <div className="print-brand">{BRAND.name} · {order.plantCode}</div>
          <h1>{t('print.packingList')}</h1>
        </div>
        <dl className="print-facts">
          <dt>{t('print.dispatchNo')}</dt><dd>{order.dispatchNo ?? '–'}</dd>
          <dt>{t('print.date')}</dt><dd>{shortDate(order.dispatchDate)}</dd>
          <dt>{t('sales.slipNo')}</dt><dd>{order.slipNo} · {shortDate(order.slipDate)}</dd>
          {order.orderRef && <><dt>{t('sales.orderRef')}</dt><dd>{order.orderRef}</dd></>}
        </dl>
      </header>

      <section className="print-parties">
        <div>
          <div className="print-label">{t(`sales.partyLabel.${order.orderType}`)}</div>
          <b>{order.party.name}</b> ({order.party.code})
          <div>{[order.party.city, order.party.state].filter(Boolean).join(', ')}</div>
          {order.party.gstin && <div>GSTIN {order.party.gstin}</div>}
        </div>
        <div>
          <div className="print-label">{t('loading.truckSection')}</div>
          <b>{order.truckNo ?? '–'}</b>
          <div>{[order.driverName, order.driverMobile].filter(Boolean).join(' · ') || '–'}</div>
          {order.transporter && <div>{order.transporter.name}{order.lrNo && ` · LR ${order.lrNo}`}</div>}
          {!order.transporter && order.lrNo && <div>LR {order.lrNo}</div>}
        </div>
      </section>

      <table className="print-table">
        <thead>
          <tr>
            <th>#</th><th>{t('print.product')}</th><th>{t('fg.pillar')}</th><th>{t('fg.batch')}</th>
            <th>{t('finishing.shade')}</th><th>{t('finishing.caliber')}</th>
            <th className="num">{t('finishing.boxes')}</th><th className="num">m²</th><th className="num">kg</th>
          </tr>
        </thead>
        <tbody>
          {order.lines.map((l) => {
            const rows = loaded.filter((p) => p.lineId === l.id)
            return rows.map((p, i) => (
              <tr key={p.id} className={i === 0 ? 'first' : undefined}>
                <td>{i === 0 ? l.lineNo : ''}</td>
                <td>{i === 0 && <><b>{l.design?.name} · {l.grade?.name}</b><div className="print-sub">{l.description}</div></>}</td>
                <td>{p.pillarCode}</td><td>{p.batchCode}</td><td>{p.shade}</td><td>{p.caliber}</td>
                <td className="num">{(p.loadedBoxes ?? 0).toLocaleString('en-IN')}</td>
                <td className="num">{qty(per(p, 'sqm'), 2)}</td>
                <td className="num">{qty(per(p, 'weightKg'), 0)}</td>
              </tr>
            ))
          })}
        </tbody>
        <tfoot>
          <tr>
            <td colSpan={6}>{t('print.total')}</td>
            <td className="num">{boxes.toLocaleString('en-IN')}</td>
            <td className="num">{qty(sum('sqm'), 2)}</td>
            <td className="num">{qty(sum('weightKg'), 0)}</td>
          </tr>
        </tfoot>
      </table>

      <section className="print-signs">
        {SIGN_OFFS.map((s) => <div key={s} className="print-sign">{t(`print.sign.${s}`)}</div>)}
      </section>

      <div className="print-cut" aria-hidden>✂</div>

      <section className="print-gate">
        <h2>{t('print.gatePass')}</h2>
        <dl className="print-facts wide">
          <dt>{t('print.dispatchNo')}</dt><dd>{order.dispatchNo ?? '–'}</dd>
          <dt>{t('print.date')}</dt><dd>{shortDate(order.dispatchDate)}</dd>
          <dt>{t('loading.truckNo')}</dt><dd>{order.truckNo ?? '–'}</dd>
          <dt>{t('loading.driverName')}</dt><dd>{order.driverName ?? '–'}</dd>
          <dt>{t(`sales.partyLabel.${order.orderType}`)}</dt><dd>{order.party.name}</dd>
          <dt>{t('finishing.boxes')}</dt><dd>{boxes.toLocaleString('en-IN')} · {qty(sum('weightKg'), 0)} kg</dd>
        </dl>
        <div className="print-signs two">
          <div className="print-sign">{t('print.sign.security')}</div>
          <div className="print-sign">{t('print.sign.driver')}</div>
        </div>
      </section>
      <GateNote order={order} />
    </div>
  )
}

function GateNote({ order }: { order: SalesOrder }) {
  const { t } = useTranslation()
  return <p className="print-foot">{t('print.foot', { by: order.dispatchedBy ?? '–', slip: order.slipNo })}</p>
}
