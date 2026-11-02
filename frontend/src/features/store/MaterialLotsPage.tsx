import { useState } from 'react'
import { Card, Input, Segmented, Select, Table, Tag, type TableColumnsType } from 'antd'
import { GoldOutlined } from '@ant-design/icons'
import { useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import dayjs from 'dayjs'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import type { MaterialLotRow } from '../../api/types'
import { useGodowns, useMaterialLots, useStoreMaterials } from './api'
import { qty, shortDate } from './format'
import MaterialLotDrawer from './MaterialLotDrawer'
import NoPlant from './NoPlant'

type Show = 'open' | 'used' | 'all'

const PAGE_SIZE = 50

/**
 * /store/lots - raw material truck by truck: what is left of each and which is used next. Opening /store/lots?lot=5
 * shows that lot straight away, so a mill charge or a design lot can link to the truck behind it.
 */
export default function MaterialLotsPage() {
  const { t } = useTranslation()
  const { currentPlant } = useAuth()
  const [params, setParams] = useSearchParams()
  const [search, setSearch] = useState<string>()
  const [materialId, setMaterialId] = useState<number>()
  const [locationId, setLocationId] = useState<number>()
  const [show, setShow] = useState<Show>('open')
  const [page, setPage] = useState(0)

  const materials = useStoreMaterials()
  const godowns = useGodowns(currentPlant?.id)
  const lots = useMaterialLots(currentPlant ? {
    plantId: currentPlant.id, materialId, locationId, search, page, size: PAGE_SIZE,
    open: show === 'all' ? undefined : show === 'open',
  } : null)
  if (!currentPlant) return <NoPlant />

  const openId = Number(params.get('lot')) || undefined
  const openLot = (id?: number) => setParams(id ? { lot: String(id) } : {}, { replace: true })
  const changed = <T,>(set: (v: T) => void) => (v: T) => {
    set(v)
    setPage(0)
  }

  const columns: TableColumnsType<MaterialLotRow> = [
    {
      title: t('lots.lot'),
      dataIndex: 'lotNo',
      render: (no: string, l) => (
        <>
          <span className="code-badge">{no}</span>
          {!l.documentId && <Tag bordered={false} style={{ marginInlineStart: 8 }}>{t('lots.oldStock')}</Tag>}
          {l.cancelled && <Tag color="red" bordered={false} style={{ marginInlineStart: 8 }}>{t('lots.cancelled')}</Tag>}
          <div className="cell-sub">{[l.supplierName, l.vehicleNo].filter(Boolean).join(' · ')}</div>
        </>
      ),
    },
    {
      title: t('lots.material'),
      key: 'material',
      render: (_, l) => (
        <>
          <div className="cell-title">{l.materialName}</div>
          <div className="cell-sub">{l.materialCode} · {l.locationCode}</div>
        </>
      ),
    },
    {
      title: t('lots.received'),
      dataIndex: 'receivedOn',
      width: 150,
      responsive: ['sm'],
      render: (d: string) => {
        const days = dayjs().startOf('day').diff(dayjs(d), 'day')
        return (
          <>
            <div>{shortDate(d)}</div>
            <div className="cell-sub">{days > 0 ? t('lots.daysOld', { count: days }) : t('lots.today')}</div>
          </>
        )
      },
    },
    {
      title: t('lots.moisture'),
      dataIndex: 'moisturePct',
      width: 100,
      align: 'right',
      responsive: ['md'],
      render: (m?: number) => (m == null ? '–' : `${qty(m, 2)} %`),
    },
    {
      title: t('lots.left'),
      key: 'left',
      width: 130,
      align: 'right',
      render: (_, l) => (
        <>
          <b>{qty(l.quantityLeft, l.unitDecimals)} {l.unitCode}</b>
          <div className="cell-sub">{t('lots.ofIn', { n: qty(l.quantityIn, l.unitDecimals) })}</div>
        </>
      ),
    },
  ]

  return (
    <>
      <PageHeader title={t('lots.title')} subtitle={t('lots.subtitle')} icon={<GoldOutlined />} />
      <FadeIn delay={0.05} className="toolbar">
        <Input.Search allowClear placeholder={t('lots.search')} onSearch={(v) => changed(setSearch)(v.trim() || undefined)}
          style={{ maxWidth: 320 }} />
        <Select<number> allowClear showSearch={{ optionFilterProp: 'label' }} placeholder={t('lots.allMaterials')} value={materialId}
          onChange={changed(setMaterialId)} style={{ minWidth: 200 }} loading={materials.isLoading}
          options={(materials.data ?? []).map((m) => ({ value: m.id, label: `${m.code} · ${m.name}` }))} />
        <Select<number> allowClear placeholder={t('store.allGodowns')} value={locationId} onChange={changed(setLocationId)}
          style={{ minWidth: 170 }} options={(godowns.data ?? []).map((g) => ({ value: g.id, label: `${g.code} · ${g.name}` }))} />
        <Segmented<Show> value={show} onChange={changed(setShow)} options={[
          { value: 'open', label: t('lots.showOpen') },
          { value: 'used', label: t('lots.showUsed') },
          { value: 'all', label: t('lots.showAll') },
        ]} />
      </FadeIn>
      {show === 'open' && <div className="panel-hint" style={{ margin: '0 0 10px' }}>{t('lots.orderHint')}</div>}
      <FadeIn delay={0.1}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<MaterialLotRow>
            rowKey="id"
            columns={columns}
            dataSource={lots.data?.content ?? []}
            loading={lots.isFetching}
            rowClassName={(l) => `clickable-row${l.cancelled ? ' row-cancelled' : ''}`}
            onRow={(l) => ({ onClick: () => openLot(l.id) })}
            locale={{ emptyText: lots.isFetching ? ' ' : <EmptyState title={t('lots.empty')} hint={t('lots.emptyHint')} /> }}
            pagination={{
              current: page + 1,
              pageSize: PAGE_SIZE,
              total: lots.data?.totalElements,
              hideOnSinglePage: true,
              showSizeChanger: false,
              onChange: (p) => setPage(p - 1),
            }}
          />
        </Card>
      </FadeIn>
      <MaterialLotDrawer lotId={openId} onClose={() => openLot()} />
    </>
  )
}
