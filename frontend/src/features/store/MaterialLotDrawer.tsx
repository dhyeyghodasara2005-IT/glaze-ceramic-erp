import { Alert, Drawer, Spin, Table, Tag, type TableColumnsType } from 'antd'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useDrawerSize } from '../../components/useDrawerSize'
import { useAuth } from '../../auth/AuthContext'
import type { MaterialLotUse } from '../../api/types'
import { useGrnLabStatus } from '../lab/labApi'
import { useMaterialReach } from '../production/api'
import { useMaterialLot } from './api'
import { DOC_TYPES } from './docTypes'
import { money, qty, shortDate } from './format'

/** One lot: where it came from, the lab's verdict, what used it and which tiles it reached. */
export default function MaterialLotDrawer({ lotId, onClose }: { lotId?: number; onClose: () => void }) {
  const { t } = useTranslation()
  const { can } = useAuth()
  const size = useDrawerSize(720)
  const detail = useMaterialLot(lotId)
  const lot = detail.data?.lot
  const mayTrace = can('PRODUCTION_VIEW')
  const reach = useMaterialReach(lotId, mayTrace)
  const lab = useGrnLabStatus(lot?.docType === 'GRN' ? lot.documentId : undefined, can('LAB_VIEW') || can('LAB_ENTRY') || can('STORE_VIEW'))
  const verdict = lab.data?.find((s) => s.materialId === lot?.materialId && s.verdict)

  const d = lot?.unitDecimals ?? 3
  const columns: TableColumnsType<MaterialLotUse> = [
    { title: t('lots.date'), dataIndex: 'date', width: 110, render: shortDate },
    {
      title: t('lots.document'),
      dataIndex: 'docNo',
      render: (no: string, u) => <Link to={`${DOC_TYPES[u.docType].path}/${u.documentId}`} className="cell-title">{no}</Link>,
    },
    {
      title: t('lots.usedFor'),
      key: 'to',
      responsive: ['sm'],
      render: (_, u) => [u.machineCode, u.issuedTo].filter(Boolean).join(' · ') || '–',
    },
    { title: t('lots.quantity'), dataIndex: 'quantity', width: 110, align: 'right', render: (q: number) => qty(q, d) },
  ]

  return (
    <Drawer title={lot?.lotNo ?? ''} open={lotId !== undefined} onClose={onClose} size={size} destroyOnHidden>
      {detail.isLoading && <div className="center-block"><Spin /></div>}
      {lot && (
        <>
          <div className="ledger-head">
            <div>
              <div className="stat-value">{qty(lot.quantityLeft, d)} <small>{lot.unitCode}</small></div>
              <div className="stat-label">{t('lots.ofIn', { n: qty(lot.quantityIn, d) })}</div>
            </div>
            <span>
              {lot.cancelled && <Tag color="red" bordered={false}>{t('lots.cancelled')}</Tag>}
              {!lot.cancelled && lot.quantityLeft === 0 && <Tag bordered={false}>{t('lots.usedUp')}</Tag>}
            </span>
          </div>
          {!lot.documentId && <Alert className="doc-alert" type="info" showIcon title={t('lots.oldStockHint')} />}

          <div className="fact-grid" style={{ marginBottom: 20 }}>
            <div><span className="stat-label">{t('lots.material')}</span><b>{lot.materialName}</b><span className="cell-sub">{lot.materialCode}</span></div>
            <div><span className="stat-label">{t('lots.godown')}</span><b>{lot.locationName}</b></div>
            <div><span className="stat-label">{t('lots.received')}</span><b>{shortDate(lot.receivedOn)}</b></div>
            {lot.supplierName && <div><span className="stat-label">{t('lots.supplier')}</span><b>{lot.supplierName}</b></div>}
            {lot.vehicleNo && <div><span className="stat-label">{t('lots.vehicle')}</span><b>{lot.vehicleNo}</b></div>}
            <div><span className="stat-label">{t('lots.moisture')}</span><b>{lot.moisturePct != null ? `${qty(lot.moisturePct, 2)} %` : '–'}</b></div>
            {verdict?.verdict && (
              <div>
                <span className="stat-label">{t('lots.labResult')}</span>
                <b>{t(`lots.labVerdict.${verdict.verdict}`)}</b>
                <span className="cell-sub">{verdict.testNo}</span>
              </div>
            )}
            <div title={t('lots.rateHint')}><span className="stat-label">{t('lots.rate')}</span><b>{money(lot.rate)}</b></div>
            {lot.documentId && lot.docType && (
              <div>
                <span className="stat-label">{t('lots.source')}</span>
                <Link to={`${DOC_TYPES[lot.docType].path}/${lot.documentId}`}><b>{t(`store.docName.${lot.docType}`)}</b></Link>
              </div>
            )}
          </div>

          <div className="form-section">{t('lots.uses')}</div>
          <Table<MaterialLotUse>
            rowKey="id"
            size="small"
            columns={columns}
            dataSource={detail.data?.uses ?? []}
            pagination={false}
            locale={{ emptyText: t('lots.noUses') }}
            style={{ marginBottom: 20 }}
          />

          {mayTrace && reach.data && (
            <>
              <div className="form-section">{t('lots.reachTitle')}</div>
              {reach.data.batches.length === 0 ? <div className="cell-sub">{t('lots.reachNone')}</div> : (
                <>
                  <div className="stat-label">{t('lots.reachBatches')}</div>
                  <div className="trace-batches" style={{ marginBottom: 12 }}>
                    {reach.data.batches.map((b) => (
                      <Link key={b.batchId} to={`/production/body-batches/${b.batchId}`}>
                        <Tag>{b.batchNo} · {qty(b.quantity, d)} {lot.unitCode}</Tag>
                      </Link>
                    ))}
                  </div>
                  <div className="stat-label">{t('lots.reachDesigns')}</div>
                  {reach.data.designLots.length === 0 ? <div className="cell-sub">{t('lots.reachNoDesigns')}</div> : (
                    <div className="trace-batches">
                      {reach.data.designLots.map((l) => (
                        <Link key={l.lotId} to={`/production/lots/${l.lotId}`}><Tag>{l.lotNo} · {l.designName}</Tag></Link>
                      ))}
                    </div>
                  )}
                </>
              )}
            </>
          )}
        </>
      )}
    </Drawer>
  )
}
