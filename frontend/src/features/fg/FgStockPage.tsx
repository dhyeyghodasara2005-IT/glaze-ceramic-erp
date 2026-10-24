import { useMemo, useState } from 'react'
import { Button, Card, Input, Select, Space, Table, Tag, type TableColumnsType } from 'antd'
import { AppstoreOutlined, BlockOutlined, GoldOutlined, StarFilled, TableOutlined } from '@ant-design/icons'
import { Link, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import CountUp from '../../components/CountUp'
import { FadeIn, Stagger, StaggerItem } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import type { FgStockRow } from '../../api/types'
import NoPlant from '../store/NoPlant'
import { qty } from '../store/format'
import GradeSplit from '../production/GradeSplit'
import { useFastMovers, useFgGodowns, useFgStock, useSetFastMover } from './fgApi'
import FgLedgerDrawer from './FgLedgerDrawer'
import { FG_DOC_TYPES } from './fgDocTypes'

/** One design with all its batches: the table's top-level row. */
interface DesignGroup {
  key: string
  designId: number
  designName: string
  designCode: string
  sizeCode: string
  boxes: number
  sqm: number
  /** boxes per grade, best grade first */
  grades: { name: string; sortOrder: number; boxes: number }[]
  rows: FgStockRow[]
}

const n = (v: number) => v.toLocaleString('en-IN')

/** "What can we sell?" - finished-goods stock by design, then batch, grade, shade, caliber and pillar. */
export default function FgStockPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { can, currentPlant } = useAuth()
  const { data, isLoading } = useFgStock(currentPlant ? { plantId: currentPlant.id } : null)
  const godowns = useFgGodowns(currentPlant?.id)
  const [search, setSearch] = useState('')
  const [godownId, setGodownId] = useState<number>()
  const [gradeId, setGradeId] = useState<number>()
  const [ledgerRow, setLedgerRow] = useState<FgStockRow | null>(null)
  const fastMovers = useFastMovers()
  const setFastMover = useSetFastMover()

  // Filtering happens in the browser, like the store's stock page: one plant's stock is a few thousand rows at most.
  const rows = useMemo(() => {
    const q = search.trim().toLowerCase()
    return (data ?? []).filter((r) =>
      (!godownId || r.godownId === godownId)
      && (!gradeId || r.gradeId === gradeId)
      && (!q || [r.designName, r.designCode, r.batchCode, r.shade, r.pillarCode].some((v) => v.toLowerCase().includes(q))),
    )
  }, [data, search, godownId, gradeId])

  // The rows arrive sorted by design, so one pass groups them.
  const groups = useMemo(() => {
    const map = new Map<number, DesignGroup>()
    for (const r of rows) {
      const g = map.get(r.designId) ?? { key: String(r.designId), designId: r.designId, designName: r.designName, designCode: r.designCode,
        sizeCode: r.sizeCode, boxes: 0, sqm: 0, grades: [], rows: [] }
      g.boxes += r.boxes
      g.sqm += r.sqm ?? 0
      const grade = g.grades.find((x) => x.name === r.gradeName)
      if (grade) grade.boxes += r.boxes
      else g.grades.push({ name: r.gradeName, sortOrder: r.gradeSortOrder, boxes: r.boxes })
      g.rows.push(r)
      map.set(r.designId, g)
    }
    for (const g of map.values()) g.grades.sort((a, b) => a.sortOrder - b.sortOrder)
    return [...map.values()]
  }, [rows])

  const byGrade = useMemo(() => {
    const map = new Map<number, { key: string; name: string; sortOrder: number; boxes: number; pieces: number }>()
    for (const r of rows) {
      const g = map.get(r.gradeId) ?? { key: String(r.gradeId), name: r.gradeName, sortOrder: r.gradeSortOrder, boxes: 0, pieces: 0 }
      g.boxes += r.boxes
      g.pieces += r.boxes   // the bar is split by boxes here
      map.set(r.gradeId, g)
    }
    return [...map.values()].sort((a, b) => a.sortOrder - b.sortOrder)
  }, [rows])

  const gradeOptions = useMemo(() => {
    const seen = new Map<number, { value: number; label: string; order: number }>()
    for (const r of data ?? []) seen.set(r.gradeId, { value: r.gradeId, label: r.gradeName, order: r.gradeSortOrder })
    return [...seen.values()].sort((a, b) => a.order - b.order)
  }, [data])

  if (!currentPlant) return <NoPlant />

  const totals = {
    boxes: rows.reduce((s, r) => s + r.boxes, 0),
    sqm: rows.reduce((s, r) => s + (r.sqm ?? 0), 0),
    designs: groups.length,
    pillars: new Set(rows.map((r) => r.pillarId)).size,
  }
  const tiles = [
    { key: 'boxes', icon: <GoldOutlined />, label: t('fg.boxesInStock'), value: totals.boxes, tint: ['#FDEEE8', '#A8401F'] },
    { key: 'sqm', icon: <BlockOutlined />, label: t('fg.sqmInStock'), value: Math.round(totals.sqm), suffix: ' m²', tint: ['#F3F1E6', '#6B5A16'] },
    { key: 'designs', icon: <AppstoreOutlined />, label: t('fg.designs'), value: totals.designs, tint: ['#EEF2F8', '#34507A'] },
    { key: 'pillars', icon: <TableOutlined />, label: t('fg.pillarsInUse'), value: totals.pillars, tint: ['#E9F3F3', '#285A5B'] },
  ]

  const batchColumns: TableColumnsType<FgStockRow> = [
    {
      title: t('fg.batch'),
      dataIndex: 'batchCode',
      render: (b: string, r) => (r.lotId
        ? <Link to={`/production/lots/${r.lotId}`} onClick={(e) => e.stopPropagation()} className="code-badge">{b}</Link>
        : <span className="code-badge">{b}</span>),
    },
    { title: t('finishing.grade'), dataIndex: 'gradeName', width: 120, render: (g: string) => <b>{g}</b> },
    { title: t('finishing.shade'), dataIndex: 'shade', width: 90 },
    { title: t('finishing.caliber'), dataIndex: 'caliber', width: 90 },
    { title: t('fg.pillar'), key: 'pillar', width: 110, render: (_, r) => <span className="code-badge">{r.godownCode} · {r.pillarCode}</span> },
    { title: t('finishing.boxes'), dataIndex: 'boxes', width: 90, align: 'right', render: (b: number) => <b>{n(b)}</b> },
    { title: t('finishing.area'), dataIndex: 'sqm', width: 100, align: 'right', responsive: ['sm'], render: (v?: number) => (v != null ? `${qty(v, 0)} m²` : '–') },
    { title: t('fg.pallets'), dataIndex: 'pallets', width: 80, align: 'right', responsive: ['md'], render: (v?: number) => v ?? '–' },
  ]

  const columns: TableColumnsType<DesignGroup> = [
    {
      key: 'fast',
      width: 44,
      title: <StarFilled aria-label={t('fg.fastMover')} title={t('fg.fastMoverHint')} />,
      render: (_, g) => {
        const on = fastMovers.data?.includes(g.designId) ?? false
        return (
          // a best seller's new pallets are suggested next to the loading point (step 6a)
          <button type="button" className={`star-toggle${on ? ' on' : ''}`} disabled={!can('FG_ENTRY')}
            aria-label={t('fg.fastMover')} aria-pressed={on} title={t('fg.fastMoverHint')}
            onClick={(e) => {
              e.stopPropagation()
              setFastMover.mutate({ designId: g.designId, fastMover: !on })
            }}>
            <StarFilled />
          </button>
        )
      },
    },
    {
      title: t('fg.design'),
      dataIndex: 'designName',
      render: (name: string, g) => (
        <>
          <div className="cell-title">{name}</div>
          <div className="cell-sub">{g.designCode} · {g.sizeCode}</div>
        </>
      ),
    },
    {
      title: t('finishing.grade'),
      key: 'grades',
      responsive: ['md'],
      render: (_, g) => g.grades.map((x) => <Tag key={x.name}>{x.name} <b>{n(x.boxes)}</b></Tag>),
    },
    { title: t('finishing.boxes'), dataIndex: 'boxes', width: 110, align: 'right', render: (b: number) => <b>{n(b)}</b> },
    { title: t('finishing.area'), dataIndex: 'sqm', width: 120, align: 'right', responsive: ['sm'], render: (v: number) => `${qty(v, 0)} m²` },
  ]

  return (
    <>
      <PageHeader title={t('fg.stockTitle')} subtitle={t('fg.stockSubtitle', { plant: currentPlant.name })} icon={<GoldOutlined />}
        extra={(
          <Space wrap>
            <Button icon={<TableOutlined />} onClick={() => navigate('/fg/godowns')}>{t('menu.fgGodowns')}</Button>
            {can('FG_ENTRY') && (['OPENING', 'BREAKAGE', 'REGRADE'] as const).map((type) => (
              <Button key={type} icon={FG_DOC_TYPES[type].icon} onClick={() => navigate(`${FG_DOC_TYPES[type].path}/new`)}>
                {t(`fg.doc.short.${type}`)}
              </Button>
            ))}
          </Space>
        )} />

      <Stagger className="stat-grid">
        {tiles.map((tile) => (
          <StaggerItem key={tile.key}>
            <div className="stat-card">
              <span className="stat-icon" style={{ background: tile.tint[0], color: tile.tint[1] }}>{tile.icon}</span>
              <span>
                <div className="stat-value">{data ? <CountUp value={tile.value} /> : '–'}{tile.suffix}</div>
                <div className="stat-label">{tile.label}</div>
              </span>
            </div>
          </StaggerItem>
        ))}
      </Stagger>

      {byGrade.length > 0 && (
        <FadeIn delay={0.04} className="panel doc-panel">
          <div className="form-section">{t('fg.byGrade')}</div>
          <GradeSplit grades={byGrade} />
        </FadeIn>
      )}

      <FadeIn delay={0.06} className="toolbar">
        <Input.Search allowClear placeholder={t('fg.searchStock')} onChange={(e) => setSearch(e.target.value)} style={{ maxWidth: 300 }} />
        <Select allowClear placeholder={t('fg.allGodowns')} aria-label={t('fg.allGodowns')} value={godownId} onChange={setGodownId}
          style={{ minWidth: 180 }} options={(godowns.data ?? []).map((g) => ({ value: g.id, label: `${g.code} · ${g.name}` }))} />
        <Select allowClear placeholder={t('fg.allGrades')} aria-label={t('fg.allGrades')} value={gradeId} onChange={setGradeId}
          style={{ minWidth: 160 }} options={gradeOptions} />
      </FadeIn>

      <FadeIn delay={0.1}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<DesignGroup>
            rowKey="key"
            columns={columns}
            dataSource={groups}
            loading={isLoading}
            pagination={{ pageSize: 25, hideOnSinglePage: true, showSizeChanger: false }}
            expandable={{
              // the batches of a design, one row per grade + shade + caliber + pillar
              expandedRowRender: (g) => (
                <Table<FgStockRow> rowKey="id" size="small" columns={batchColumns} dataSource={g.rows} pagination={false}
                  rowClassName="clickable-row" onRow={(r) => ({ onClick: () => setLedgerRow(r) })} />
              ),
              expandRowByClick: true,
            }}
            rowClassName="clickable-row"
            locale={{
              emptyText: isLoading ? ' ' : data?.length
                ? <EmptyState title={t('store.emptyFilter')} />
                : <EmptyState title={t('fg.emptyStock')} hint={t('fg.emptyStockHint')} />,
            }}
          />
        </Card>
      </FadeIn>

      <FgLedgerDrawer row={ledgerRow} onClose={() => setLedgerRow(null)} />
    </>
  )
}
