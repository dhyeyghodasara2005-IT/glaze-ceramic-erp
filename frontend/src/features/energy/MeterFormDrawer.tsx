import { useEffect } from 'react'
import { App, Button, Col, Drawer, Form, Input, InputNumber, Row, Segmented, Select, Space, Switch, Tag } from 'antd'
import { BulbOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import { KIND_UNITS, METER_KINDS, type Machine, type Meter, type MeterKind, type MeterUnit, type ReadEvery } from '../../api/types'
import { patterns, toUpper } from '../../api/validation'
import { useDrawerSize } from '../../components/useDrawerSize'
import { useMasterList } from '../masters/masterApi'
import { useSaveMeter } from './energyApi'

interface Values {
  code: string
  name: string
  kind: MeterKind
  unit: MeterUnit
  main: boolean
  machineId?: number
  readEvery: ReadEvery
  sortOrder?: number
  active: boolean
}

/** The meters the plant told us about (25 Oct 2026): one tap fills the form, then pick the machine if it has one. */
const PRESETS: { key: string; code: string; kind: MeterKind; unit: MeterUnit; main: boolean; readEvery: ReadEvery; sortOrder: number }[] = [
  { key: 'gasMain', code: 'GG-MAIN', kind: 'GAS', unit: 'SCM', main: true, readEvery: 'DAY', sortOrder: 1 },
  { key: 'kilnGas', code: 'KILN1-GAS', kind: 'GAS', unit: 'M3', main: false, readEvery: 'SHIFT', sortOrder: 2 },
  { key: 'powerMain', code: 'GEB-MAIN', kind: 'POWER', unit: 'KWH', main: true, readEvery: 'DAY', sortOrder: 10 },
  { key: 'sectionPower', code: 'PRESS-PWR', kind: 'POWER', unit: 'KWH', main: false, readEvery: 'SHIFT', sortOrder: 11 },
  { key: 'coal', code: 'SD-COAL', kind: 'COAL', unit: 'KG', main: false, readEvery: 'SHIFT', sortOrder: 20 },
  { key: 'dgHours', code: 'DG-HRS', kind: 'RUN_HOURS', unit: 'HOUR', main: false, readEvery: 'DAY', sortOrder: 30 },
  { key: 'diesel', code: 'DG-DIESEL', kind: 'DIESEL', unit: 'LITRE', main: false, readEvery: 'DAY', sortOrder: 31 },
  { key: 'propane', code: 'PROPANE', kind: 'PROPANE', unit: 'KG', main: false, readEvery: 'DAY', sortOrder: 40 },
  { key: 'lng', code: 'LNG', kind: 'LNG', unit: 'KG', main: false, readEvery: 'DAY', sortOrder: 41 },
]

/** Set up a meter, or change one. Its kind and unit stay once it has readings. */
export default function MeterFormDrawer({ open, plantId, meter, onClose }: {
  open: boolean
  plantId: number
  meter?: Meter
  onClose: () => void
}) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const size = useDrawerSize(560)
  const [form] = Form.useForm<Values>()
  const save = useSaveMeter()
  const machines = useMasterList<Machine>('machines', open)
  const kind = Form.useWatch('kind', form) as MeterKind | undefined

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(meter
      ? { ...meter, machineId: meter.machine?.id }
      : { kind: 'GAS', unit: 'SCM', main: false, readEvery: 'SHIFT', active: true })
  }, [open, meter, form])

  const usePreset = (p: (typeof PRESETS)[number]) =>
    form.setFieldsValue({ code: p.code, name: t(`energy.presets.${p.key}`), kind: p.kind, unit: p.unit, main: p.main,
      readEvery: p.readEvery, sortOrder: p.sortOrder })

  const onFinish = async (v: Values) => {
    try {
      await save.mutateAsync({
        id: meter?.id,
        body: {
          plantId, code: v.code, name: v.name.trim(), kind: v.kind, unit: v.unit,
          main: (v.kind === 'GAS' || v.kind === 'POWER') && v.main, machineId: v.machineId, readEvery: v.readEvery,
          sortOrder: v.sortOrder ?? 0, active: v.active,
        },
      })
      message.success(t('common.saved'))
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }
  const units = kind ? KIND_UNITS[kind] : []
  return (
    <Drawer open={open} onClose={onClose} size={size} destroyOnHidden title={meter ? t('energy.editMeter') : t('energy.newMeter')}
      extra={
        <Space>
          <Button onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="primary" loading={save.isPending} onClick={() => form.submit()}>{t('common.save')}</Button>
        </Space>
      }>
      {!meter && (
        <div className="pm-suggest">
          <div className="pm-suggest-head"><BulbOutlined /> {t('energy.presetsHead')}</div>
          <div className="pm-suggest-chips">
            {PRESETS.map((p) => (
              <Tag key={p.key} className="pm-chip" onClick={() => usePreset(p)}>{t(`energy.presets.${p.key}`)}</Tag>
            ))}
          </div>
        </div>
      )}
      <Form<Values> form={form} layout="vertical" onFinish={onFinish}>
        <Row gutter={16}>
          <Col xs={24} sm={10}>
            <Form.Item name="code" label={t('common.code')} normalize={toUpper}
              rules={[required, { pattern: patterns.code, message: t('company.invalidCode') }]}>
              <Input maxLength={20} placeholder="KILN1-GAS" />
            </Form.Item>
          </Col>
          <Col xs={24} sm={14}>
            <Form.Item name="name" label={t('common.name')} rules={[required, { whitespace: true, message: t('common.required') }]}>
              <Input maxLength={100} placeholder={t('energy.namePlaceholder')} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={14}>
            <Form.Item name="kind" label={t('energy.kind')} rules={[required]} extra={kind && t(`energy.kindHint.${kind}`)}>
              <Select options={METER_KINDS.map((k) => ({ value: k, label: t(`energy.kinds.${k}`) }))}
                onChange={(k: MeterKind) => form.setFieldsValue({ unit: KIND_UNITS[k][0], main: false })} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={10}>
            <Form.Item name="unit" label={t('energy.unit')} rules={[required]}>
              <Segmented<MeterUnit> block options={units.map((u) => ({ value: u, label: t(`energy.units.${u}`) }))} />
            </Form.Item>
          </Col>
          <Col xs={24}>
            <Form.Item name="readEvery" label={t('energy.readEvery')} rules={[required]}>
              <Segmented<ReadEvery> options={[
                { value: 'SHIFT', label: t('energy.every.SHIFT') },
                { value: 'DAY', label: t('energy.every.DAY') },
              ]} />
            </Form.Item>
          </Col>
          {(kind === 'GAS' || kind === 'POWER') && (
            <Col xs={24}>
              <Form.Item name="main" label={t('energy.main')} valuePropName="checked" extra={t('energy.mainHint')}>
                <Switch />
              </Form.Item>
            </Col>
          )}
          <Col xs={24} sm={16}>
            <Form.Item name="machineId" label={t('energy.machine')} extra={t('energy.machineHint')}>
              <Select allowClear showSearch={{ optionFilterProp: 'label' }} loading={machines.isLoading}
                options={(machines.data ?? []).filter((m) => m.plantId === plantId && (m.active || m.id === meter?.machine?.id))
                  .map((m) => ({ value: m.id, label: `${m.name} · ${m.code}` }))} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={8}>
            <Form.Item name="sortOrder" label={t('energy.sortOrder')}>
              <InputNumber min={0} max={999} precision={0} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>
        {meter && (
          <Form.Item name="active" label={t('common.status')} valuePropName="checked">
            <Switch checkedChildren={t('common.active')} unCheckedChildren={t('common.inactive')} />
          </Form.Item>
        )}
      </Form>
    </Drawer>
  )
}
