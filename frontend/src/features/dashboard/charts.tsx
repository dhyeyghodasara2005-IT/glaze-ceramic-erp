import { useEffect, useMemo, useRef, useState, type KeyboardEvent, type ReactNode } from 'react'
import { Segmented, Tooltip } from 'antd'
import { BarChartOutlined, TableOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import dayjs from 'dayjs'

/*
 * The dashboard's charts, drawn as plain SVG (no chart library): thin marks, hairline grid, a 2px white gap between
 * touching marks, a crosshair on lines and a tooltip on every bar. Colours come from CSS variables (--viz-*) whose
 * values were checked with the colour validator (contrast and colour-blind separation).
 */

/** 1,234 -> "1.2K", 12,345 -> "12K": short numbers for axis ticks. */
export function compact(n: number) {
  if (Math.abs(n) >= 100_000) return `${Math.round(n / 1000)}K`
  if (Math.abs(n) >= 1000) return `${(n / 1000).toFixed(1).replace(/\.0$/, '')}K`
  return `${Math.round(n)}`
}

export const fmt = (n?: number | null, decimals = 0) =>
  n == null ? '–' : n.toLocaleString('en-IN', { maximumFractionDigits: decimals })

/** The width of an element, kept up to date (the SVG is drawn at the width it really has). */
function useWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null)
  const [width, setWidth] = useState(0)
  useEffect(() => {
    if (!ref.current) return
    const ro = new ResizeObserver(([entry]) => setWidth(Math.round(entry.contentRect.width)))
    ro.observe(ref.current)
    return () => ro.disconnect()
  }, [])
  return [ref, width] as const
}

/** Round the top of the axis up to a clean number: 1,137 -> 1,200. */
function niceMax(max: number) {
  if (max <= 0) return 1
  const step = 10 ** Math.floor(Math.log10(max))
  for (const m of [1, 1.2, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10]) if (m * step >= max) return m * step
  return 10 * step
}

// ------------------------------------------------------------------ the card around a chart

export interface TableView {
  columns: string[]
  rows: (string | number)[][]
}

/** Title, a "Chart | Table" switch (every chart can be read as a table), and the chart. */
export function ChartCard({ title, hint, table, children, className }: {
  title: string
  hint?: ReactNode
  table?: TableView
  children: ReactNode
  className?: string
}) {
  const { t } = useTranslation()
  const [asTable, setAsTable] = useState(false)
  return (
    <section className={`panel viz-card${className ? ` ${className}` : ''}`}>
      <header className="viz-head">
        <div>
          <h3 className="viz-title">{title}</h3>
          {hint && <div className="viz-hint">{hint}</div>}
        </div>
        {table && (
          <Segmented size="small" value={asTable ? 'table' : 'chart'} onChange={(v) => setAsTable(v === 'table')}
            options={[
              { value: 'chart', icon: <BarChartOutlined />, title: t('dashboard.viewChart') },
              { value: 'table', icon: <TableOutlined />, title: t('dashboard.viewTable') },
            ]} />
        )}
      </header>
      {asTable && table ? (
        <div className="viz-table-wrap">
          <table className="viz-table">
            <thead><tr>{table.columns.map((c) => <th key={c}>{c}</th>)}</tr></thead>
            <tbody>
              {table.rows.map((r, i) => (
                <tr key={i}>{r.map((v, j) => <td key={j} className={typeof v === 'number' ? 'num' : undefined}>{typeof v === 'number' ? fmt(v, 2) : v}</td>)}</tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : children}
    </section>
  )
}

// ------------------------------------------------------------------ trend over days

export interface Series {
  key: string
  label: string
  /** a CSS colour, e.g. var(--viz-1) */
  color: string
  values: number[]
}

const M = { top: 12, right: 60, bottom: 26, left: 44 }
const HEIGHT = 240

/**
 * Lines over days on one axis (all series in the same unit), with a crosshair that snaps to the nearest day and a
 * tooltip listing every series. Arrow keys move it too. A single series gets a light area wash under its line.
 */
export function TrendChart({ dates, series, unit }: { dates: string[]; series: Series[]; unit: string }) {
  const { t } = useTranslation()
  const [ref, width] = useWidth<HTMLDivElement>()
  const [active, setActive] = useState<number>()
  const n = dates.length
  const plotW = Math.max(width - M.left - M.right, 10)
  const plotH = HEIGHT - M.top - M.bottom
  const max = niceMax(Math.max(0, ...series.flatMap((s) => s.values)))
  const x = (i: number) => M.left + (n <= 1 ? plotW / 2 : (i * plotW) / (n - 1))
  const y = (v: number) => M.top + plotH - (v / max) * plotH
  const ticks = [0, 0.25, 0.5, 0.75, 1].map((f) => f * max)
  // a few date labels: first, last, and evenly between (about one per 80px)
  const every = Math.max(1, Math.ceil(n / Math.max(2, Math.floor(plotW / 80))))
  const dateTicks = dates.map((_, i) => i).filter((i) => i === 0 || i === n - 1 || (i % every === 0 && n - 1 - i >= every / 2))

  const path = (values: number[]) => values.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join('')
  // end labels only when they don't collide (otherwise the legend and the tooltip carry the values)
  const ends = series.map((s) => ({ s, v: s.values[n - 1] ?? 0, y: y(s.values[n - 1] ?? 0) }))
  const endsFit = ends.every((a, i) => ends.every((b, j) => i === j || Math.abs(a.y - b.y) >= 16))

  const onMove = (clientX: number, rect: DOMRect) => {
    const px = clientX - rect.left - M.left
    setActive(Math.max(0, Math.min(n - 1, Math.round((px / plotW) * (n - 1)))))
  }
  const onKey = (e: KeyboardEvent) => {
    if (e.key === 'ArrowRight') setActive((a) => Math.min(n - 1, (a ?? -1) + 1))
    else if (e.key === 'ArrowLeft') setActive((a) => Math.max(0, (a ?? n) - 1))
    else return
    e.preventDefault()
  }
  const tipLeft = active === undefined ? 0 : Math.min(Math.max(x(active) + 12, 0), Math.max(width - 170, 0))

  return (
    <div className="trend" ref={ref}>
      {series.length > 1 && (
        <div className="viz-legend">
          {series.map((s) => <span key={s.key}><i className="key-line" style={{ background: s.color }} />{s.label}</span>)}
        </div>
      )}
      {width > 0 && (
        <svg width={width} height={HEIGHT} role="img" aria-label={t('dashboard.trendAria', { unit })}>
          {ticks.map((v) => (
            <g key={v}>
              <line className="viz-grid" x1={M.left} x2={M.left + plotW} y1={y(v)} y2={y(v)} />
              <text className="viz-tick" x={M.left - 8} y={y(v)} dy="0.32em" textAnchor="end">{compact(v)}</text>
            </g>
          ))}
          {dateTicks.map((i) => (
            <text key={i} className="viz-tick" x={x(i)} y={HEIGHT - 6} textAnchor={i === 0 ? 'start' : i === n - 1 ? 'end' : 'middle'}>
              {dayjs(dates[i]).format('D MMM')}
            </text>
          ))}
          {series.length === 1 && (
            <path d={`${path(series[0].values)}L${x(n - 1)},${y(0)}L${x(0)},${y(0)}Z`} fill={series[0].color} opacity={0.1} />
          )}
          {series.map((s) => (
            <path key={s.key} className="viz-line" d={path(s.values)} stroke={s.color} />
          ))}
          {ends.map(({ s, v, y: ey }) => (
            <g key={s.key}>
              <circle cx={x(n - 1)} cy={ey} r={4} fill={s.color} className="viz-dot" />
              {endsFit && <text className="viz-end" x={x(n - 1) + 8} y={ey} dy="0.32em">{compact(v)}</text>}
            </g>
          ))}
          {active !== undefined && (
            <g pointerEvents="none">
              <line className="viz-crosshair" x1={x(active)} x2={x(active)} y1={M.top} y2={M.top + plotH} />
              {series.map((s) => <circle key={s.key} cx={x(active)} cy={y(s.values[active] ?? 0)} r={4} fill={s.color} className="viz-dot" />)}
            </g>
          )}
          {/* the hit area is the whole plot: the reader aims at a day, not at a 2px line */}
          <rect x={M.left} y={M.top} width={plotW} height={plotH} fill="transparent" tabIndex={0} className="viz-hit"
            aria-label={t('dashboard.trendKeys')}
            onPointerMove={(e) => onMove(e.clientX, (e.currentTarget.ownerSVGElement as SVGSVGElement).getBoundingClientRect())}
            onPointerLeave={() => setActive(undefined)} onKeyDown={onKey} onBlur={() => setActive(undefined)} />
        </svg>
      )}
      {active !== undefined && (
        <div className="viz-tip" style={{ left: tipLeft, top: M.top + (series.length > 1 ? 28 : 0) }}>
          <div className="viz-tip-date">{dayjs(dates[active]).format('ddd, D MMM')}</div>
          {series.map((s) => (
            <div key={s.key} className="viz-tip-row">
              <i className="key-line" style={{ background: s.color }} />
              <b>{fmt(s.values[active], 1)} {unit}</b>
              <span>{s.label}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

// ------------------------------------------------------------------ part of a whole

export interface Part {
  key: string
  label: string
  value: number
  pct?: number
  color: string
}

/** One horizontal bar split into parts (white gaps between them), with the legend carrying every value. */
export function ShareBar({ parts, unit }: { parts: Part[]; unit: string }) {
  const total = parts.reduce((s, p) => s + p.value, 0)
  if (!total) return null
  return (
    <div className="share">
      <div className="share-bar" role="img" aria-label={parts.map((p) => `${p.label} ${p.pct ?? 0}%`).join(', ')}>
        {parts.filter((p) => p.value > 0).map((p) => (
          <Tooltip key={p.key} title={`${p.label}: ${fmt(p.value)} ${unit} (${fmt(p.pct, 1)}%)`}>
            <span className="share-seg" tabIndex={0} style={{ flexGrow: p.value, background: p.color }} />
          </Tooltip>
        ))}
      </div>
      <ul className="share-legend">
        {parts.map((p) => (
          <li key={p.key}>
            <i className="key-rect" style={{ background: p.color }} />
            <span className="share-name">{p.label}</span>
            <b>{fmt(p.pct, 1)}%</b>
            <span className="share-value">{fmt(p.value)} {unit}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

// ------------------------------------------------------------------ ranked bars

export interface BarItem {
  key: string
  label: string
  value: number
  /** shown after the value, e.g. "−2.5%" */
  note?: string
  muted?: boolean
}

/** Horizontal bars from one baseline, one colour, the value at each tip. */
export function BarList({ items, color = 'var(--viz-1)', unit }: { items: BarItem[]; color?: string; unit?: string }) {
  const max = useMemo(() => Math.max(1, ...items.map((i) => i.value)), [items])
  return (
    <ul className="bar-list">
      {items.map((item, i) => (
        <li key={item.key} className={item.muted ? 'muted' : undefined}>
          <div className="bar-label">{item.label}</div>
          <Tooltip title={`${item.label}: ${fmt(item.value)}${unit ? ` ${unit}` : ''}${item.note ? ` · ${item.note}` : ''}`}>
            <div className="bar-track" tabIndex={0}>
              <span className="bar-fill" style={{ width: `${(item.value / max) * 100}%`, background: color, animationDelay: `${i * 0.05}s` }} />
              <span className="bar-value">{fmt(item.value)}{item.note && <em>{item.note}</em>}</span>
            </div>
          </Tooltip>
        </li>
      ))}
    </ul>
  )
}

// ------------------------------------------------------------------ a ratio against a limit

/** A track (light step of the same colour) with the used part filled; full or over turns to the warning colour. */
export function Meter({ value, max, label, detail }: { value: number; max?: number; label: string; detail: string }) {
  const pct = max ? Math.min(100, Math.round((value / max) * 100)) : undefined
  const full = max !== undefined && value >= max
  return (
    <div className={`meter${full ? ' full' : ''}`}>
      <div className="meter-head">
        <span className="stat-label">{label}</span>
        <b>{pct === undefined ? '–' : `${pct}%`}</b>
      </div>
      <div className="meter-track"><span style={{ width: `${pct ?? 0}%` }} /></div>
      <div className="meter-detail">{detail}</div>
    </div>
  )
}
