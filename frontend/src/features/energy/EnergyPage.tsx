import { useState } from 'react'
import { Alert, Button, Card, Segmented, Table, Tag, type TableColumnsType } from 'antd'
import { ThunderboltOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import dayjs from 'dayjs'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn, Stagger, StaggerItem } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import type { EnergyUse } from '../../api/types'
import NoPlant from '../store/NoPlant'
import { BarList, ChartCard, TrendChart, fmt } from '../dashboard/charts'
import { useEnergyUse } from './energyApi'
import { KindTag, useUnit } from './energyParts'

type Period = 'week' | 'month30' | 'thisMonth' | 'quarter'

function rangeOf(period: Period) {
  const today = dayjs()
  const from = period === 'week' ? today.subtract(6, 'day')
    : period === 'month30' ? today.subtract(29, 'day')
      : period === 'thisMonth' ? today.startOf('month') : today.subtract(89, 'day')
  return { from: from.format('YYYY-MM-DD'), to: today.format('YYYY-MM-DD') }
}

type Row = EnergyUse['meters'][number]
type Day = EnergyUse['days'][number]

/**
 * The days from the first to the last one with a reading. A day nobody read (today's main meters are read tomorrow
 * morning) is not a day nothing was used, so the line stops instead of dropping to 0.
 */
function readDays(days: Day[], value: (d: Day) => number | undefined) {
  const read = days.flatMap((d, i) => (value(d) != null ? [i] : []))
  return read.length ? days.slice(read[0], read[read.length - 1] + 1) : []
}

/**
 * /energy/use - what the plant burned: gas and power from the main meters (what the bills are on) and per m² packed,
 * the kiln's gas per m² fired, coal per tonne of powder, the DG set; power by section; which readings are missing.
 */
export default function EnergyPage() {
  const { t } = useTranslation()
  const { can, currentPlant } = useAuth()
  const unit = useUnit()
  const [period, setPeriod] = useState<Period>('week')
  const range = rangeOf(period)
  const query = useEnergyUse(currentPlant ? { plantId: currentPlant.id, ...range } : null)
  const d = query.data
  if (!currentPlant) return <NoPlant />

  const columns: TableColumnsType<Row> = [
    {
      title: t('energy.meter'),
      key: 'meter',
      render: (_, r) => (
        <>
          <div className="cell-title">{r.meter.name} {r.meter.main && <Tag color="volcano" bordered={false}>{t('energy.mainTag')}</Tag>}</div>
          <div className="cell-sub">{r.meter.code} · {t(`energy.every.${r.meter.readEvery}`)}</div>
        </>
      ),
    },
    { title: t('energy.kind'), key: 'kind', width: 150, responsive: ['md'], render: (_, r) => <KindTag meter={r.meter} /> },
    { title: t('energy.usedCol'), key: 'used', width: 140, align: 'right', render: (_, r) => <b>{r.used == null ? '–' : `${fmt(r.used, 1)} ${unit(r.meter.unit)}`}</b> },
    { title: t('energy.perDay'), key: 'perDay', width: 120, align: 'right', responsive: ['sm'], render: (_, r) => (r.perDay == null ? '–' : fmt(r.perDay, 1)) },
    {
      title: t('energy.readingsCol'),
      key: 'readings',
      width: 120,
      align: 'right',
      render: (_, r) => {
        const missing = r.expected - r.readings
        return missing > 0 ? <Tag color="orange" bordered={false}>{t('energy.readingsOf', { n: r.readings, of: r.expected })}</Tag>
          : <span className="cell-sub">{t('energy.readingsOf', { n: r.readings, of: r.expected })}</span>
      },
    },
    {
      title: t('energy.lastReading'),
      key: 'last',
      width: 170,
      responsive: ['lg'],
      render: (_, r) => (r.last ? (
        <>
          <div>{r.last.reading != null ? fmt(r.last.reading, 3) : `${fmt(r.last.used, 2)} ${unit(r.meter.unit)}`}</div>
          <div className="cell-sub">{dayjs(r.last.readOn).format('D MMM')}{r.last.shiftCode ? ` · ${r.last.shiftCode}` : ''}</div>
        </>
      ) : '–'),
    },
  ]

  const missing = d ? [...d.lastRound.missing, ...d.lastRound.dayMissing] : []
  return (
    <>
      <PageHeader title={t('energy.useTitle')} subtitle={t('energy.useSubtitle')} icon={<ThunderboltOutlined />}
        extra={can('METER_ENTRY') && <Link to="/energy/readings"><Button type="primary">{t('energy.enterReadings')}</Button></Link>} />
      <FadeIn delay={0.04} className="toolbar">
        <Segmented<Period> value={period} onChange={setPeriod} options={[
          { value: 'week', label: t('dashboard.period.week') },
          { value: 'month30', label: t('dashboard.period.month30') },
          { value: 'thisMonth', label: t('dashboard.period.thisMonth') },
          { value: 'quarter', label: t('maint.period90') },
        ]} />
        <span className="cell-sub">{dayjs(range.from).format('D MMM')} – {dayjs(range.to).format('D MMM YYYY')}</span>
      </FadeIn>

      {d && missing.length > 0 && (
        <FadeIn delay={0.06}>
          <Alert type="warning" showIcon className="energy-missing"
            title={t('energy.missingTitle', { count: missing.length })}
            description={
              <>
                {d.lastRound.missing.length > 0 && (
                  <div>{t('energy.missingShift', { date: dayjs(d.lastRound.date).format('D MMM'), shift: d.lastRound.shiftCode, names: d.lastRound.missing.join(', ') })}</div>
                )}
                {d.lastRound.dayMissing.length > 0 && (
                  <div>{t('energy.missingDay', { date: dayjs(d.lastRound.day).format('D MMM'), names: d.lastRound.dayMissing.join(', ') })}</div>
                )}
              </>
            } />
        </FadeIn>
      )}

      {d && d.meters.length === 0 && (
        <div className="panel"><EmptyState title={t('energy.noMeters')} hint={t('energy.noMetersHint')}
          action={can('MASTER_VIEW') && <Link to="/masters/meters"><Button>{t('energy.setUpMeters')}</Button></Link>} /></div>
      )}

      {d && d.meters.length > 0 && <Use d={d} />}

      {d && d.meters.length > 0 && (
        <FadeIn delay={0.16}>
          <h2 className="section-title">{t('energy.metersTitle')}</h2>
          <Card className="data-card" styles={{ body: { padding: 0 } }}>
            <Table<Row> rowKey={(r) => r.meter.id} columns={columns} dataSource={d.meters} pagination={false} />
          </Card>
        </FadeIn>
      )}
    </>
  )
}

/** The tiles and charts. Each figure says what it was divided by; a figure nobody read shows a dash. */
function Use({ d }: { d: EnergyUse }) {
  const { t } = useTranslation()
  const unit = useUnit()
  const x = d.totals
  const none = t('energy.nothingRead')
  const tiles = [
    { key: 'kiln', label: t('energy.tileKiln'), value: x.kilnGasPerSqm, decimals: 3, suffix: `${unit(x.kilnGasUnit)}/m²`,
      sub: x.kilnGas == null ? none : t('energy.tileKilnSub', { n: fmt(x.kilnGas), unit: unit(x.kilnGasUnit), sqm: fmt(x.sqmFired) }) },
    { key: 'power', label: t('energy.tilePower'), value: x.powerPerSqm, decimals: 3, suffix: 'kWh/m²',
      sub: x.powerMain == null ? none : t('energy.tilePowerSub', { n: fmt(x.powerMain), sqm: fmt(x.sqmPacked) }) },
    { key: 'gas', label: t('energy.tileGas'), value: x.gasPerSqm, decimals: 3, suffix: `${unit(x.gasMainUnit)}/m²`,
      sub: x.gasMain == null ? none : t('energy.tileGasSub', { n: fmt(x.gasMain), unit: unit(x.gasMainUnit) }) },
    { key: 'coal', label: t('energy.tileCoal'), value: x.coalKg, decimals: 0, suffix: 'kg',
      sub: x.coalKg == null ? none : x.coalPerTon != null ? t('energy.tileCoalSub', { n: fmt(x.coalPerTon, 1) }) : t('energy.tileCoalNoPowder') },
    { key: 'dg', label: t('energy.tileDg'), value: x.dgHours, decimals: 1, suffix: 'h',
      sub: x.dieselLitres == null ? (x.dgHours == null ? none : t('energy.tileDgNoDiesel'))
        : t('energy.tileDgSub', { n: fmt(x.dieselLitres), per: fmt(x.dieselPerHour, 1) }) },
  ]
  const gasDays = readDays(d.days, (day) => day.gasMain)
  const powerDays = readDays(d.days, (day) => day.powerMain)
  const rest = d.powerParts.find((p) => p.meterId == null)
  return (
    <>
      <Stagger className="dash-headline">
        {tiles.map((tile) => (
          <StaggerItem key={tile.key} className="dash-tile">
            <div className="stat-label">{tile.label}</div>
            <div className="dash-tile-value">
              {tile.value == null ? '–' : fmt(tile.value, tile.decimals)}<span className="dash-unit">{tile.value == null ? '' : ` ${tile.suffix}`}</span>
            </div>
            <div className="dash-tile-sub">{tile.sub}</div>
          </StaggerItem>
        ))}
      </Stagger>

      <div className="dash-grid">
        {gasDays.length > 1 && (
          <FadeIn delay={0.1}>
            <ChartCard title={t('energy.gasPerDay', { unit: unit(x.gasMainUnit) })} hint={t('energy.gasPerDayHint')}
              table={{ columns: [t('dashboard.date'), unit(x.gasMainUnit)], rows: gasDays.map((day) => [dayjs(day.date).format('D MMM'), day.gasMain ?? 0]) }}>
              <TrendChart dates={gasDays.map((day) => day.date)} unit={unit(x.gasMainUnit)}
                series={[{ key: 'gas', label: t('energy.gasMainSeries'), color: 'var(--viz-1)', values: gasDays.map((day) => day.gasMain ?? 0) }]} />
            </ChartCard>
          </FadeIn>
        )}
        {powerDays.length > 1 && (
          <FadeIn delay={0.12}>
            <ChartCard title={t('energy.powerPerDay')} hint={t('energy.powerPerDayHint')}
              table={{ columns: [t('dashboard.date'), 'kWh'], rows: powerDays.map((day) => [dayjs(day.date).format('D MMM'), day.powerMain ?? 0]) }}>
              <TrendChart dates={powerDays.map((day) => day.date)} unit="kWh"
                series={[{ key: 'power', label: t('energy.powerMainSeries'), color: 'var(--viz-2)', values: powerDays.map((day) => day.powerMain ?? 0) }]} />
            </ChartCard>
          </FadeIn>
        )}
        {d.powerParts.length > 0 && (
          <FadeIn delay={0.14}>
            <ChartCard title={t('energy.bySection')} hint={rest && rest.kwh < 0 ? t('energy.sectionsOver') : t('energy.bySectionHint')}
              table={{ columns: [t('energy.meter'), 'kWh'], rows: d.powerParts.map((p) => [p.name ?? t('energy.notOnSection'), p.kwh]) }}>
              <BarList unit="kWh" color="var(--viz-2)"
                items={d.powerParts.filter((p) => p.kwh > 0).map((p) => ({
                  key: String(p.meterId ?? 'rest'), label: p.name ?? t('energy.notOnSection'), value: p.kwh, muted: p.meterId == null,
                }))} />
            </ChartCard>
          </FadeIn>
        )}
      </div>
    </>
  )
}
