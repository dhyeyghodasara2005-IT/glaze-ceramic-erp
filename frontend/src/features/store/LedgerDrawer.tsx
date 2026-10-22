import { useState } from 'react'
import { Drawer, Select, Table, Tag, type TableColumnsType } from 'antd'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useDrawerSize } from '../../components/useDrawerSize'
import type { LedgerRow, StockRow } from '../../api/types'
import { useLedger } from './api'
import { DOC_TYPES } from './docTypes'
import { money, qty, shortDate } from './format'

const PAGE_SIZE = 50

/** Every movement of one material in a plant, newest first, with a link to the document behind it. */
export default function LedgerDrawer({ plantId, row, onClose }: { plantId: number; row: StockRow | null; onClose: () => void }) {
  const { t } = useTranslation()
  const drawerSize = useDrawerSize(860)
  const [locationId, setLocationId] = useState<number>()
  const [page, setPage] = useState(0)
  const { data, isFetching } = useLedger(
    row ? { plantId, materialId: row.materialId, locationId, page, size: PAGE_SIZE } : null,
  )

  const d = row?.unitDecimals ?? 3
  const columns: TableColumnsType<LedgerRow> = [
    { title: t('store.date'), dataIndex: 'txnDate', width: 110, render: shortDate },
    {
      title: t('store.document'),
      dataIndex: 'docNo',
      render: (no: string, r) => (
        <>
          <Link to={`${DOC_TYPES[r.docType].path}/${r.documentId}`} className="cell-title">{no}</Link>
          {r.reversal && <Tag color="red" style={{ marginInlineStart: 8 }}>{t('store.reversal')}</Tag>}
        </>
      ),
    },
    { title: t('store.godown'), dataIndex: 'locationCode', width: 90, responsive: ['sm'] },
    {
      title: t('store.in'),
      key: 'in',
      width: 100,
      align: 'right',
      render: (_, r) => (r.quantity > 0 ? <span className="qty-in">+{qty(r.quantity, d)}</span> : ''),
    },
    {
      title: t('store.out'),
      key: 'out',
      width: 100,
      align: 'right',
      render: (_, r) => (r.quantity < 0 ? <span className="qty-out">−{qty(-r.quantity, d)}</span> : ''),
    },
    { title: t('store.rate'), dataIndex: 'rate', width: 110, align: 'right', responsive: ['md'], render: (v: number) => money(v) },
    { title: t('store.value'), dataIndex: 'value', width: 130, align: 'right', responsive: ['lg'], render: (v: number) => money(v) },
    {
      title: t('store.balance'),
      dataIndex: 'balanceQty',
      width: 100,
      align: 'right',
      render: (v: number) => <span className="cell-title">{qty(v, d)}</span>,
    },
  ]

  return (
    <Drawer
      title={row ? `${row.materialCode} · ${row.materialName}` : ''}
      open={row !== null}
      onClose={onClose}
      size={drawerSize}
      destroyOnHidden
      afterOpenChange={(open) => {
        if (!open) {
          setLocationId(undefined)
          setPage(0)
        }
      }}
    >
      {row && (
        <>
          <div className="ledger-head">
            <div>
              <div className="stat-value">{qty(row.quantity, d)} <small>{row.unitCode}</small></div>
              <div className="stat-label">{money(row.value)}</div>
            </div>
            <Select
              allowClear
              placeholder={t('store.allGodowns')}
              aria-label={t('store.godown')}
              value={locationId}
              onChange={(v) => {
                setLocationId(v)
                setPage(0)
              }}
              style={{ minWidth: 180 }}
              options={row.locations.map((l) => ({ value: l.locationId, label: `${l.locationCode} · ${l.locationName}` }))}
            />
          </div>
          <div className="panel-hint" style={{ margin: '0 0 10px' }}>{t('store.balanceHint')}</div>
          <Table<LedgerRow>
            rowKey="id"
            size="small"
            columns={columns}
            dataSource={data?.content}
            loading={isFetching}
            rowClassName={(r) => (r.reversal ? 'row-reversal' : '')}
            locale={{ emptyText: isFetching ? ' ' : t('store.noMovements') }}
            pagination={{
              current: page + 1,
              pageSize: PAGE_SIZE,
              total: data?.totalElements,
              hideOnSinglePage: true,
              showSizeChanger: false,
              onChange: (p) => setPage(p - 1),
            }}
          />
        </>
      )}
    </Drawer>
  )
}
