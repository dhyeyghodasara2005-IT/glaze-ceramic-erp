import { useMemo, useState } from 'react'
import { App, Button, Popconfirm } from 'antd'
import { BgColorsOutlined, FireOutlined, FormatPainterOutlined, NodeExpandOutlined, SwapOutlined, ThunderboltOutlined } from '@ant-design/icons'
import { Link, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../components/PageHeader'
import CountUp from '../../components/CountUp'
import EmptyState from '../../components/EmptyState'
import { FadeIn, Stagger, StaggerItem } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import { errorMessage } from '../../api/errors'
import type { LotSummary } from '../../api/types'
import NoPlant from '../store/NoPlant'
import { qty, shortDate } from '../store/format'
import { LINE_PATHS, useLineOverview } from './lineApi'
import LotFunnel from './LotFunnel'
import LotStatusTag from './LotStatusTag'
import OpenLotModal from './OpenLotModal'
import { useCloseLot } from './pressApi'

/**
 * The glaze & kiln home: each digital printer and the design it runs (designs are started and changed here, since
 * the printer decides the design), every lot whose tiles may be in the line with its funnel, and today's figures.
 */
export default function LinePage() {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const navigate = useNavigate()
  const { can, currentPlant } = useAuth()
  const { data } = useLineOverview(currentPlant?.id)
  const closeLot = useCloseLot()
  const [starting, setStarting] = useState<{ printerId?: number } | null>(null)
  const running = useMemo(
    () => new Map<number, LotSummary>((data?.printers ?? []).filter((p) => p.lot).map((p) => [p.printerId, p.lot!])),
    [data],
  )

  if (!currentPlant) return <NoPlant />
  const canDesign = can('GLAZE_ENTRY')

  const subtitle = data
    ? data.shiftCode
      ? t('prod.bodySubtitle', { date: shortDate(data.prodDate), shift: `${data.shiftCode} (${data.shiftName})` })
      : t('prod.bodySubtitleNoShift', { date: shortDate(data.prodDate) })
    : currentPlant.name
  const pct = (rejects: number, tilesIn: number) => (tilesIn > 0 ? ((rejects * 100) / tilesIn).toFixed(1) : undefined)
  const glazeLoss = data && pct(data.today.glazeRejects, data.today.glazeIn)
  const kilnLoss = data && pct(data.today.kilnRejects, data.today.kilnIn)
  // Good tiles out of the presses today that the glaze line hasn't taken in yet: on the line or in compensators.
  const onTheLine = data ? data.today.pressGood - data.today.glazeIn : undefined

  const tiles = [
    { key: 'glazed', icon: <FormatPainterOutlined />, label: t('line.glazedToday'), value: data && data.today.glazeIn - data.today.glazeRejects,
      sub: glazeLoss && t('line.lossPct', { pct: glazeLoss }), tint: ['#F1EEF8', '#4B3D73'] },
    { key: 'between', icon: <NodeExpandOutlined />, label: t('line.beforeGlaze'), value: onTheLine !== undefined ? Math.max(onTheLine, 0) : undefined,
      sub: data && t('line.fromPresses', { n: data.today.pressGood.toLocaleString('en-IN') }), tint: ['#EEF2F7', '#334E68'] },
    { key: 'fired', icon: <FireOutlined />, label: t('line.firedToday'), value: data && Math.round(data.today.firedSqm), suffix: ' m²',
      sub: kilnLoss && t('line.lossPct', { pct: kilnLoss }), tint: ['#FDEEE8', '#A8401F'] },
    { key: 'gas', icon: <ThunderboltOutlined />, label: t('line.gasPerSqm'), text: data?.today.gasPerSqm != null ? qty(data.today.gasPerSqm, 2) : '–',
      sub: data && `${qty(data.today.gasScm, 0)} SCM`, tint: ['#E9F3F3', '#285A5B'] },
  ]

  const onCloseLot = async (lot: LotSummary) => {
    try {
      await closeLot.mutateAsync(lot.id)
      message.success(t('press.closedOk', { no: lot.lotNo }))
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  return (
    <>
      <PageHeader title={t('line.hubTitle')} subtitle={subtitle} icon={<NodeExpandOutlined />}
        extra={canDesign && (
          <Button icon={<SwapOutlined />} onClick={() => setStarting({})}>{t('press.startDesign')}</Button>
        )} />

      <Stagger className="stat-grid">
        {tiles.map((tile) => (
          <StaggerItem key={tile.key}>
            <div className="stat-card">
              <span className="stat-icon" style={{ background: tile.tint[0], color: tile.tint[1] }}>{tile.icon}</span>
              <span style={{ minWidth: 0 }}>
                <div className="stat-value">
                  {'text' in tile ? tile.text : tile.value === undefined ? '–' : <CountUp value={tile.value} />}
                  {tile.suffix}
                </div>
                <div className="stat-label">{tile.label}</div>
                {tile.sub && <div className="cell-sub">{tile.sub}</div>}
              </span>
            </div>
          </StaggerItem>
        ))}
      </Stagger>

      <FadeIn delay={0.1}>
        <h2 className="section-title"><BgColorsOutlined style={{ color: 'var(--brand-600)' }} /> {t('line.printers')}</h2>
        <p className="panel-hint" style={{ margin: '-4px 0 12px' }}>{t('line.printersHint')}</p>
        {data && data.printers.length === 0 && (
          <div className="panel"><EmptyState title={t('prod.noMachines', { type: t('machineType.DIGITAL_PRINTER') })} /></div>
        )}
        <div className="mill-grid">
          {data?.printers.map((p) => (
            <div key={p.printerId} className={`line-card${p.lot ? '' : ' idle'}`}>
              <div className="mill-card-head">
                <span className="code-badge">{p.printerCode}</span>
                {p.lot && <LotStatusTag status={p.lot.status} />}
              </div>
              {p.lot ? (
                <>
                  <Link to={`/production/lots/${p.lot.id}`} className="line-design">{p.lot.designName}</Link>
                  <div className="cell-sub">{p.lot.lotNo} · {p.lot.sizeCode} {p.lot.finishName}</div>
                  <div className="line-figures">
                    <span><b>{p.lot.glazed.toLocaleString('en-IN')}</b> {t('press.glazed').toLowerCase()}</span>
                    {p.lot.glazedSqm != null && <span><b>{qty(p.lot.glazedSqm, 0)}</b> m²</span>}
                  </div>
                </>
              ) : (
                <div className="cell-sub line-idle">{t('press.idle')}</div>
              )}
              {canDesign && (
                <div className="line-actions">
                  {p.lot && (
                    <Button type="primary" size="small" onClick={() => navigate(`${LINE_PATHS.GLAZE}/new?lot=${p.lot!.id}`)}>
                      {t('line.shortEntry.GLAZE')}
                    </Button>
                  )}
                  <Button size="small" onClick={() => setStarting({ printerId: p.printerId })}>
                    {p.lot ? t('press.changeDesign') : t('press.startDesign')}
                  </Button>
                  {p.lot && (
                    <Popconfirm title={t('press.closeTitle', { no: p.lot.lotNo })}
                      description={<div style={{ maxWidth: 260 }}>{t('press.closeHint')}</div>}
                      okText={t('press.closeLot')} cancelText={t('common.cancel')} onConfirm={() => onCloseLot(p.lot!)}>
                      <Button size="small" loading={closeLot.isPending}>{t('press.closeLot')}</Button>
                    </Popconfirm>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      </FadeIn>

      <FadeIn delay={0.14}>
        <h2 className="section-title" style={{ marginTop: 24 }}><NodeExpandOutlined style={{ color: 'var(--brand-600)' }} /> {t('line.lotsInLine')}</h2>
        {data && data.lots.length === 0 && (
          <div className="panel"><EmptyState title={t('line.noLots')} /></div>
        )}
        <div className="lot-line-list">
          {data?.lots.map(({ lot, flow }) => (
            <div key={lot.id} className="line-card">
              <div className="mill-card-head">
                <span>
                  <span className="code-badge">{lot.printerCode}</span>{' '}
                  <Link to={`/production/lots/${lot.id}`} className="cell-title">{lot.designName}</Link>
                  <span className="cell-sub"> · {lot.lotNo}</span>
                </span>
                <LotStatusTag status={lot.status} />
              </div>
              <LotFunnel flow={flow} compact part="line" />
              <div className="line-actions">
                {can('GLAZE_ENTRY') && (
                  <Button type="primary" size="small" onClick={() => navigate(`${LINE_PATHS.GLAZE}/new?lot=${lot.id}`)}>
                    {t('line.shortEntry.GLAZE')}
                  </Button>
                )}
                {can('KILN_ENTRY') && (
                  <Button type="primary" size="small" onClick={() => navigate(`${LINE_PATHS.KILN}/new?lot=${lot.id}`)}>
                    {t('line.shortEntry.KILN')}
                  </Button>
                )}
              </div>
            </div>
          ))}
        </div>
      </FadeIn>

      <OpenLotModal open={starting !== null} plantId={currentPlant.id} printerId={starting?.printerId} running={running}
        onClose={() => setStarting(null)} />
    </>
  )
}
