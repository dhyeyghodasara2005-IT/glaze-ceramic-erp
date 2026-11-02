import { useState } from 'react'
import { Alert, App, Button, Card, DatePicker, Table, type TableColumnsType } from 'antd'
import { SunOutlined, SyncOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import dayjs from 'dayjs'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn, Stagger, StaggerItem } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import { errorMessage } from '../../api/errors'
import type { WeatherShift } from '../../api/types'
import NoPlant from '../store/NoPlant'
import { qty, shortDate } from '../store/format'
import { useCollectWeather, useWeatherShifts, useWeatherStatus } from './weatherApi'

const FORMAT = 'YYYY-MM-DD'

/** /production/weather - what the weather was during each production shift, kept beside production for later analysis. */
export default function WeatherPage() {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const { can, currentPlant } = useAuth()
  const [range, setRange] = useState<[string, string]>([dayjs().subtract(13, 'day').format(FORMAT), dayjs().format(FORMAT)])
  const status = useWeatherStatus(currentPlant?.id)
  const shifts = useWeatherShifts(currentPlant ? { plantId: currentPlant.id, from: range[0], to: range[1] } : null)
  const collect = useCollectWeather()
  if (!currentPlant) return <NoPlant />

  const s = status.data
  const time = s?.latestHour ? dayjs(s.latestHour).format('DD MMM, h a') : ''
  const onFetch = async () => {
    try {
      await collect.mutateAsync(currentPlant.id)
      message.success(t('weather.fetched'))
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }
  const fetchButton = s?.enabled && s.hasLocation && can('PLANT_MANAGE') && (
    <Button icon={<SyncOutlined />} loading={collect.isPending} onClick={onFetch}>{t('weather.fetchNow')}</Button>
  )
  const tiles = s?.latestHour ? [
    { key: 'temperature', label: t('weather.temperature'), value: qty(s.temperatureC, 1), unit: '°C' },
    { key: 'humidity', label: t('weather.humidity'), value: qty(s.humidityPct, 0), unit: '%' },
    { key: 'rain', label: t('weather.rain'), value: qty(s.rainMm, 2), unit: 'mm' },
  ] : []

  const columns: TableColumnsType<WeatherShift> = [
    { title: t('weather.date'), dataIndex: 'prodDate', width: 120, render: shortDate },
    { title: t('weather.shift'), key: 'shift', width: 80, render: (_, r) => <><span className="cell-title">{r.shiftCode}</span> <span className="cell-sub">{r.shiftName}</span></> },
    {
      title: t('weather.temperature'),
      key: 'temperature',
      align: 'right',
      render: (_, r) => (
        <>
          <b style={{ whiteSpace: 'nowrap' }}>{qty(r.temperatureAvg, 1)} °C</b>
          <div className="cell-sub" style={{ whiteSpace: 'nowrap' }}>{t('weather.range', { min: qty(r.temperatureMin, 1), max: qty(r.temperatureMax, 1) })}</div>
        </>
      ),
    },
    { title: t('weather.humidity'), dataIndex: 'humidityAvg', width: 80, align: 'right', render: (v: number) => <span style={{ whiteSpace: 'nowrap' }}>{qty(v, 0)} %</span> },
    { title: t('weather.rain'), dataIndex: 'rainMm', width: 100, align: 'right', responsive: ['sm'], render: (v: number) => (v > 0 ? `${qty(v, 1)} mm` : '–') },
    { title: t('weather.hours'), dataIndex: 'hours', width: 80, align: 'right', responsive: ['md'] },
  ]

  return (
    <>
      <PageHeader title={t('weather.title')} subtitle={t('weather.subtitle')} icon={<SunOutlined />} extra={fetchButton} />

      <FadeIn delay={0.04}>
        {s && !s.enabled && <Alert className="doc-alert" type="info" showIcon title={t('weather.offTitle')} description={t('weather.offHint')} />}
        {s && !s.hasLocation && (
          <Alert className="doc-alert" type="warning" showIcon title={t('weather.noLocationTitle')} description={t('weather.noLocationHint')}
            action={can('PLANT_VIEW') && <Link to="/plants">{t('weather.openPlants')}</Link>} />
        )}
        {s?.enabled && s.stale && <Alert className="doc-alert" type="warning" showIcon title={t('weather.staleTitle', { time })} description={t('weather.staleHint')} />}
      </FadeIn>

      {tiles.length > 0 && (
        <Stagger className="dash-headline">
          {tiles.map((tile) => (
            <StaggerItem key={tile.key} className="dash-tile">
              <div className="stat-label">{tile.label}</div>
              <div className="dash-tile-value">{tile.value}<span className="dash-unit"> {tile.unit}</span></div>
              <div className="dash-tile-sub">{t('weather.asOf', { time })}</div>
            </StaggerItem>
          ))}
        </Stagger>
      )}

      <FadeIn delay={0.08} className="toolbar">
        <DatePicker.RangePicker allowClear={false} format="DD MMM YYYY" value={[dayjs(range[0]), dayjs(range[1])]}
          disabledDate={(d) => d.isAfter(dayjs(), 'day')}
          onChange={(v) => v?.[0] && v[1] && setRange([v[0].format(FORMAT), v[1].format(FORMAT)])} />
      </FadeIn>
      <FadeIn delay={0.1}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<WeatherShift>
            rowKey={(r) => `${r.prodDate}-${r.shiftId}`}
            columns={columns}
            dataSource={shifts.data ?? []}
            loading={shifts.isFetching}
            pagination={{ pageSize: 50, hideOnSinglePage: true, showSizeChanger: false }}
            locale={{ emptyText: shifts.isFetching ? ' ' : <EmptyState title={t('weather.empty')} hint={t('weather.emptyHint')} /> }}
          />
        </Card>
      </FadeIn>
      {tiles.length > 0 && <p className="cell-sub" style={{ marginTop: 10 }}>{t('weather.source')}</p>}
    </>
  )
}
