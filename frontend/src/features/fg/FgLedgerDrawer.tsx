import { Button, Drawer, Space, Spin, Table, type TableColumnsType } from 'antd'
import { Link, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useDrawerSize } from '../../components/useDrawerSize'
import type { FgMove, FgStockRow } from '../../api/types'
import { dateTime, qty, shortDate } from '../store/format'
import { useAuth } from '../../auth/AuthContext'
import { useFgLedger } from './fgApi'
import { FG_DOC_TYPES, sourcePath } from './fgDocTypes'

/** Where a stock row's boxes came from and went to, oldest first, with the balance after each move. */
export default function FgLedgerDrawer({ row, onClose }: { row: FgStockRow | null; onClose: () => void }) {
  const { t } = useTranslation()
  const drawerSize = useDrawerSize(640)
  const navigate = useNavigate()
  const { can } = useAuth()
  const { data, isLoading } = useFgLedger(row?.id)
  const s = data?.stock ?? row

  const columns: TableColumnsType<FgMove> = [
    { title: t('fg.doc.date'), dataIndex: 'moveDate', width: 120, render: (d: string) => shortDate(d) },
    {
      title: t('fg.source'),
      key: 'source',
      render: (_, m) => (
        <>
          <Link to={sourcePath(m.source, m.sourceId)} className="code-badge">{m.sourceNo}</Link>
          <div className="cell-sub">
            {t(`fg.sourceType.${m.source}`)}{m.reversal && ` · ${t('fg.reversal')}`} · {m.createdBy}, {dateTime(m.createdAt)}
          </div>
        </>
      ),
    },
    {
      title: t('finishing.boxes'),
      dataIndex: 'boxes',
      width: 90,
      align: 'right',
      render: (b: number) => <b className={b < 0 ? 'qty-out' : 'qty-in'}>{b > 0 ? '+' : ''}{b.toLocaleString('en-IN')}</b>,
    },
    { title: t('fg.balance'), dataIndex: 'balanceBoxes', width: 90, align: 'right', render: (b: number) => b.toLocaleString('en-IN') },
  ]

  return (
    <Drawer open={row !== null} onClose={onClose} size={drawerSize} destroyOnHidden
      title={s && `${s.designName} · ${s.gradeName}`}
      extra={s && s.boxes > 0 && can('FG_ENTRY') && (
        // start a breakage or regrade document with this batch already filled in
        <Space>
          <Button icon={FG_DOC_TYPES.BREAKAGE.icon} onClick={() => navigate(`${FG_DOC_TYPES.BREAKAGE.path}/new?stock=${s.id}`)}>{t('fg.doc.short.BREAKAGE')}</Button>
          <Button icon={FG_DOC_TYPES.REGRADE.icon} onClick={() => navigate(`${FG_DOC_TYPES.REGRADE.path}/new?stock=${s.id}`)}>{t('fg.doc.short.REGRADE')}</Button>
        </Space>
      )}>
      {s && (
        <div className="fact-grid" style={{ marginBottom: 16 }}>
          <div><span className="stat-label">{t('fg.batch')}</span><b>{s.lotId ? <Link to={`/production/lots/${s.lotId}`}>{s.batchCode}</Link> : s.batchCode}</b></div>
          <div><span className="stat-label">{t('finishing.shade')} · {t('finishing.caliber')}</span><b>{s.shade} · {s.caliber}</b></div>
          <div><span className="stat-label">{t('fg.pillar')}</span><b>{s.godownCode} · {s.pillarCode}</b></div>
          <div><span className="stat-label">{t('finishing.boxes')}</span><b>{s.boxes.toLocaleString('en-IN')}</b></div>
          <div><span className="stat-label">{t('finishing.area')}</span><b>{s.sqm != null ? `${qty(s.sqm, 0)} m²` : '–'}</b></div>
          <div><span className="stat-label">{t('fg.pallets')}</span><b>{s.pallets ?? '–'}</b></div>
        </div>
      )}
      {isLoading ? <div className="center-block"><Spin /></div> : (
        <Table<FgMove> rowKey="id" size="small" columns={columns} dataSource={data?.moves} pagination={false}
          rowClassName={(m) => (m.reversal ? 'row-cancelled' : '')} />
      )}
    </Drawer>
  )
}
