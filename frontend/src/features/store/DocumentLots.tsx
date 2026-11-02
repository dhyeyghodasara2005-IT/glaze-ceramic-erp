import { Table, Tag, type TableColumnsType } from 'antd'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { FadeIn } from '../../components/motion'
import type { DocumentLot, StoreDocType } from '../../api/types'
import { useDocumentLots } from './api'
import { qty, shortDate } from './format'

/** On a posted document: the lots a GRN made, or the lots an issue took from. */
export default function DocumentLots({ documentId, type }: { documentId: number; type: StoreDocType }) {
  const { t } = useTranslation()
  const lots = useDocumentLots(documentId)
  if (!lots.data?.length) return null

  const made = lots.data.some((l) => l.lot.documentId === documentId)
  const columns: TableColumnsType<DocumentLot> = [
    {
      title: t('lots.lot'),
      key: 'lot',
      render: (_, { lot }) => (
        <>
          <Link to={`/store/lots?lot=${lot.id}`} className="code-badge">{lot.lotNo}</Link>
          {!lot.documentId && <Tag bordered={false} style={{ marginInlineStart: 8 }}>{t('lots.oldStock')}</Tag>}
        </>
      ),
    },
    {
      title: t('lots.material'),
      key: 'material',
      render: (_, { lot }) => (
        <>
          <div className="cell-title">{lot.materialName}</div>
          <div className="cell-sub">{lot.locationCode}</div>
        </>
      ),
    },
    ...(type === 'GRN' ? [] : [{
      title: t('lots.received'),
      key: 'received',
      width: 150,
      responsive: ['sm' as const],
      render: (_: unknown, { lot }: DocumentLot) => (
        <>
          <div>{shortDate(lot.receivedOn)}</div>
          <div className="cell-sub">{lot.supplierName}</div>
        </>
      ),
    }]),
    {
      title: t('lots.quantity'),
      key: 'quantity',
      width: 130,
      align: 'right',
      render: (_, { lot, quantity }) => `${qty(quantity, lot.unitDecimals)} ${lot.unitCode}`,
    },
  ]
  return (
    <FadeIn delay={0.12} className="panel doc-panel">
      <div className="form-section">{made ? t('lots.madeByDoc') : t('lots.takenByDoc')}</div>
      <Table<DocumentLot> rowKey={(l) => l.lot.id} size="small" columns={columns} dataSource={lots.data} pagination={false} />
    </FadeIn>
  )
}
