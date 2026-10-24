import { useEffect } from 'react'
import { App, Button, Drawer, Form, InputNumber, Segmented, Select, Switch, Table, type TableColumnsType } from 'antd'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useDrawerSize } from '../../components/useDrawerSize'
import { useAuth } from '../../auth/AuthContext'
import { errorMessage } from '../../api/errors'
import { PILLAR_KINDS, PILLAR_ZONES, type FgStockRow, type GodownMapPillar, type PillarKind, type PillarZone } from '../../api/types'
import { qty } from '../store/format'
import { useUpdatePillar } from './fgApi'

interface Values {
  capacityPallets?: number
  active: boolean
  zone: PillarZone
  walkOrder?: number
  kind: PillarKind
}

/** One pillar: what stands under it, and (warehouse) its capacity and open / closed. */
export default function PillarDrawer({ pillar, godownCode, onClose }: {
  pillar?: GodownMapPillar
  godownCode: string
  onClose: () => void
}) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const drawerSize = useDrawerSize(560)
  const { can } = useAuth()
  const update = useUpdatePillar()
  const [form] = Form.useForm<Values>()

  useEffect(() => {
    if (pillar) {
      form.setFieldsValue({
        capacityPallets: pillar.capacityPallets, active: pillar.active, zone: pillar.zone, walkOrder: pillar.walkOrder, kind: pillar.kind,
      })
    }
  }, [pillar, form])

  const onSave = async (v: Values) => {
    try {
      await update.mutateAsync({
        id: pillar!.id,
        body: { capacityPallets: v.capacityPallets ?? undefined, active: v.active, zone: v.zone, walkOrder: v.walkOrder ?? undefined, kind: v.kind },
      })
      message.success(t('common.saved'))
      onClose()
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const columns: TableColumnsType<FgStockRow> = [
    {
      title: t('fg.design'),
      key: 'design',
      render: (_, r) => (
        <>
          <div className="cell-title">{r.designName}</div>
          <div className="cell-sub">{r.lotId ? <Link to={`/production/lots/${r.lotId}`}>{r.batchCode}</Link> : r.batchCode}</div>
        </>
      ),
    },
    { title: t('finishing.grade'), dataIndex: 'gradeName', width: 100 },
    { title: `${t('finishing.shade')} · ${t('finishing.caliber')}`, key: 'sc', width: 110, render: (_, r) => `${r.shade} · ${r.caliber}` },
    { title: t('finishing.boxes'), dataIndex: 'boxes', width: 80, align: 'right', render: (b: number) => <b>{b.toLocaleString('en-IN')}</b> },
  ]

  return (
    <Drawer open={pillar !== undefined} onClose={onClose} size={drawerSize} destroyOnHidden
      title={pillar && `${godownCode} · ${t('fg.pillar')} ${pillar.code}`}>
      {pillar && (
        <>
          <div className="fact-grid" style={{ marginBottom: 16 }}>
            <div><span className="stat-label">{t('finishing.boxes')}</span><b>{pillar.boxes.toLocaleString('en-IN')}</b></div>
            <div>
              <span className="stat-label">{t('fg.pallets')}</span>
              <b>{pillar.palletsUnknown && pillar.pallets === 0 ? '–' : pillar.pallets}{pillar.capacityPallets ? ` / ${pillar.capacityPallets}` : ''}</b>
            </div>
            <div><span className="stat-label">{t('finishing.area')}</span><b>{qty(pillar.stock.reduce((s, r) => s + (r.sqm ?? 0), 0), 0)} m²</b></div>
          </div>
          {pillar.palletsUnknown && <p className="cell-sub">{t('fg.palletsUnknown')}</p>}
          <Table<FgStockRow> rowKey="id" size="small" columns={columns} dataSource={pillar.stock} pagination={false}
            locale={{ emptyText: t('fg.emptyPillar') }} />
          {can('FG_ENTRY') && (
            <Form<Values> form={form} layout="vertical" onFinish={onSave} style={{ marginTop: 20 }}>
              <div className="form-section">{t('fg.pillarSettings')}</div>
              <Form.Item name="zone" label={t('fg.zoneLabel')}>
                <Segmented<PillarZone> options={PILLAR_ZONES.map((z) => ({ value: z, label: t(`fg.zone.${z}`) }))} />
              </Form.Item>
              <Form.Item name="walkOrder" label={t('fg.walkOrder')} tooltip={t('fg.walkOrderHint')}>
                <InputNumber<number> min={1} max={9999} precision={0} style={{ width: 200 }} />
              </Form.Item>
              <Form.Item name="kind" label={t('fg.kindLabel')}>
                <Select style={{ width: 200 }} options={PILLAR_KINDS.map((k) => ({ value: k, label: t(`fg.kind.${k}`) }))} />
              </Form.Item>
              <Form.Item name="capacityPallets" label={t('fg.capacity')} tooltip={t('fg.capacityHint')}>
                <InputNumber<number> min={1} max={9999} precision={0} suffix={t('fg.palletsShort')} style={{ width: 200 }} />
              </Form.Item>
              <Form.Item name="active" label={t('fg.open')} valuePropName="checked" extra={t('fg.closeHint')}>
                <Switch checkedChildren={t('fg.open')} unCheckedChildren={t('fg.closed')} />
              </Form.Item>
              <Button type="primary" htmlType="submit" loading={update.isPending}>{t('common.save')}</Button>
            </Form>
          )}
        </>
      )}
    </Drawer>
  )
}
