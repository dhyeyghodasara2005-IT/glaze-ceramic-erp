import { useMemo, useState } from 'react'
import { Button, Space, Spin } from 'antd'
import { ArrowLeftOutlined, EditOutlined, NodeIndexOutlined, PlusOutlined, TableOutlined } from '@ant-design/icons'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import type { GodownMapPillar } from '../../api/types'
import { useGodownMap } from './fgApi'
import AddPillarsModal from './AddPillarsModal'
import FgGodownDrawer from './FgGodownDrawer'
import LayoutModal from './LayoutModal'
import PillarDrawer from './PillarDrawer'

/** How full a pillar is: empty, has room, nearly full, full - or just "in use" when its capacity isn't set. */
function fullness(p: GodownMapPillar): 'closed' | 'empty' | 'ok' | 'busy' | 'full' | 'used' {
  if (!p.active) return 'closed'
  if (p.boxes === 0) return 'empty'
  if (!p.capacityPallets || p.palletsUnknown) return 'used'
  const share = p.pallets / p.capacityPallets
  return share >= 0.95 ? 'full' : share >= 0.7 ? 'busy' : 'ok'
}

/**
 * The godown floor as the forklift driver knows it: rows of pillars (A1 A2 A3 …, B1 B2 …), each tile
 * showing how full it is and which design and shade stands there. A tile opens the pillar.
 */
export default function GodownMapPage() {
  const { id } = useParams()
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { can, currentPlant } = useAuth()
  const { data, isLoading } = useGodownMap(Number(id))
  const [adding, setAdding] = useState(false)
  const [editing, setEditing] = useState(false)
  const [laying, setLaying] = useState(false)
  const [openId, setOpenId] = useState<number>()

  // A1 A2 … under "A", B1 B2 … under "B": the letters are the rows of pillars on the floor
  const rows = useMemo(() => {
    const map = new Map<string, GodownMapPillar[]>()
    for (const p of data?.pillars ?? []) {
      const letter = p.code.match(/^[A-Z]*/)?.[0] || '#'
      map.set(letter, [...(map.get(letter) ?? []), p])
    }
    return [...map.entries()]
  }, [data])

  if (isLoading) return <div className="center-block"><Spin size="large" /></div>
  if (!data) return <div className="panel"><EmptyState title={t('store.notFound')} action={<Link to="/fg/godowns">{t('common.back')}</Link>} /></div>

  const g = data.godown
  const canEntry = can('FG_ENTRY')
  const open = data.pillars.find((p) => p.id === openId)

  return (
    <>
      <PageHeader
        title={`${g.code} · ${g.name}`}
        subtitle={t('fg.mapSubtitle', { pillars: g.pillars, boxes: g.boxes.toLocaleString('en-IN') })}
        icon={<TableOutlined />}
        extra={(
          <Space wrap>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/fg/godowns')}>{t('common.back')}</Button>
            {canEntry && <Button icon={<EditOutlined />} onClick={() => setEditing(true)}>{t('common.edit')}</Button>}
            {canEntry && data.pillars.length > 0 && (
              <Button icon={<NodeIndexOutlined />} onClick={() => setLaying(true)}>{t('fg.setLayout')}</Button>
            )}
            {canEntry && <Button type="primary" icon={<PlusOutlined />} onClick={() => setAdding(true)}>{t('fg.addPillars')}</Button>}
          </Space>
        )}
      />

      <FadeIn delay={0.04} className="pillar-legend">
        {(['empty', 'ok', 'busy', 'full', 'used', 'closed'] as const).map((f) => (
          <span key={f}><i className={`pillar-dot ${f}`} />{t(`fg.fullness.${f}`)}</span>
        ))}
      </FadeIn>

      {data.pillars.length === 0 && (
        <div className="panel">
          <EmptyState title={t('fg.noPillars')} hint={t('fg.noPillarsHint')}
            action={canEntry && <Button type="primary" icon={<PlusOutlined />} onClick={() => setAdding(true)}>{t('fg.addPillars')}</Button>} />
        </div>
      )}

      {rows.map(([letter, pillars], i) => (
        <FadeIn key={letter} delay={0.06 + i * 0.03} className="pillar-row">
          <div className="pillar-row-label">{t('fg.rowN', { row: letter })}</div>
          <div className="pillar-grid">
            {pillars.map((p) => {
              const f = fullness(p)
              const main = p.stock[0]
              const pct = p.capacityPallets && !p.palletsUnknown ? Math.min(100, Math.round((p.pallets * 100) / p.capacityPallets)) : undefined
              return (
                <button key={p.id} type="button" className={`pillar-tile ${f}`} onClick={() => setOpenId(p.id)}
                  aria-label={`${t('fg.pillar')} ${p.code}`}>
                  <span className="pillar-head">
                    <span className="pillar-code">{p.code}</span>
                    {p.walkOrder != null && <span className="pillar-walk">{t('fg.walkShort', { n: p.walkOrder })}</span>}
                    {/* the zone only when it isn't the default; the use when it isn't plain storage */}
                    {(p.kind !== 'STORAGE' || p.zone !== 'NORMAL') && (
                      <span className="pillar-tag">{p.kind !== 'STORAGE' ? t(`fg.kind.${p.kind}`) : t(`fg.zone.${p.zone}`)}</span>
                    )}
                  </span>
                  {f === 'closed' ? (
                    <span className="cell-sub">{t('fg.closed')}</span>
                  ) : p.boxes === 0 ? (
                    <span className="cell-sub">{t('fg.fullness.empty')}</span>
                  ) : (
                    <>
                      <span className="pillar-load">
                        {pct !== undefined ? `${p.pallets}/${p.capacityPallets} ${t('fg.palletsShort')}` : t('finishing.boxesN', { n: p.boxes.toLocaleString('en-IN') })}
                      </span>
                      {pct !== undefined && <span className="pillar-bar"><span style={{ width: `${pct}%` }} /></span>}
                      <span className="pillar-what">{main.designName} · {main.shade}</span>
                      {p.stock.length > 1 && <span className="cell-sub">{t('fg.moreBatches', { count: p.stock.length - 1 })}</span>}
                    </>
                  )}
                </button>
              )
            })}
          </div>
        </FadeIn>
      ))}

      <PillarDrawer pillar={open} godownCode={g.code} onClose={() => setOpenId(undefined)} />
      {currentPlant && <AddPillarsModal open={adding} godownId={g.id} onClose={() => setAdding(false)} />}
      {currentPlant && <FgGodownDrawer open={editing} plantId={g.plantId} godown={g} onClose={() => setEditing(false)} />}
      {currentPlant && <LayoutModal open={laying} godownId={g.id} onClose={() => setLaying(false)} />}
    </>
  )
}
