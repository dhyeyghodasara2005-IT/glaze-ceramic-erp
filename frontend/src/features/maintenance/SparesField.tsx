import { useMemo } from 'react'
import { Button, Form, InputNumber, Select, type FormInstance } from 'antd'
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { qty } from '../store/format'
import { useSpareGodowns, useSpares, useSpareStock } from './maintenanceApi'

export interface SpareRow {
  materialId?: number
  locationId?: number
  quantity?: number
}

/**
 * The "spares used" rows of a maintenance form (a Form.List named "spares"): the spare (spares first, then other
 * materials), the godown with what it holds, and the quantity. Used by breakdowns (8a) and preventive jobs (8b).
 */
export default function SparesField({ form, plantId }: { form: FormInstance; plantId: number }) {
  const { t } = useTranslation()
  const spares = useSpares(true)
  const godowns = useSpareGodowns(plantId)
  const stock = useSpareStock(plantId)
  const rows = Form.useWatch('spares', form) as SpareRow[] | undefined

  const stockOf = useMemo(() => {
    const map = new Map<string, number>()
    for (const s of stock.data ?? []) map.set(`${s.materialId}:${s.locationId}`, s.quantity)
    return map
  }, [stock.data])
  const options = useMemo(() => {
    const list = spares.data ?? []
    const option = (s: (typeof list)[number]) => ({ value: s.id, label: `${s.name} · ${s.code}` })
    return [
      { label: t('maint.sparesGroup'), options: list.filter((s) => s.spare).map(option) },
      { label: t('maint.otherMaterials'), options: list.filter((s) => !s.spare).map(option) },
    ].filter((g) => g.options.length)
  }, [spares.data, t])
  const required = { required: true, message: t('common.required') }

  return (
    <>
      <div className="form-section">{t('maint.sparesUsed')} <span className="form-section-hint">· {t('maint.sparesHint')}</span></div>
      <Form.List name="spares">
        {(fields, { add, remove }) => (
          <>
            {fields.map((field, index) => {
              const row = rows?.[index]
              const unit = spares.data?.find((s) => s.id === row?.materialId)
              const there = row?.materialId && row?.locationId ? stockOf.get(`${row.materialId}:${row.locationId}`) ?? 0 : undefined
              return (
                <div key={field.key} className="bd-spare-row">
                  <Form.Item name={[field.name, 'materialId']} rules={[required]}>
                    <Select options={options} loading={spares.isLoading} showSearch={{ optionFilterProp: 'label' }}
                      placeholder={t('maint.chooseSpare')} popupMatchSelectWidth={false} />
                  </Form.Item>
                  <Form.Item name={[field.name, 'locationId']} rules={[required]}
                    extra={there !== undefined && (
                      <span className={there < (row?.quantity ?? 0) ? 'short' : undefined}>
                        {t('maint.inStore', { n: qty(there), unit: unit?.unit ?? '' })}
                      </span>
                    )}>
                    <Select options={(godowns.data ?? []).map((g) => ({ value: g.id, label: g.name }))} placeholder={t('maint.godown')} />
                  </Form.Item>
                  <Form.Item name={[field.name, 'quantity']} rules={[required]}>
                    <InputNumber<number> min={0} precision={unit?.decimals ?? 3} suffix={unit?.unit} style={{ width: '100%' }}
                      placeholder={t('maint.qty')} inputMode="decimal" />
                  </Form.Item>
                  <Button type="text" danger icon={<MinusCircleOutlined />} aria-label={t('common.delete')} onClick={() => remove(field.name)} />
                </div>
              )
            })}
            <Button type="dashed" block icon={<PlusOutlined />}
              onClick={() => add({ locationId: godowns.data?.length === 1 ? godowns.data[0].id : undefined })}>
              {t('maint.addSpare')}
            </Button>
          </>
        )}
      </Form.List>
    </>
  )
}
