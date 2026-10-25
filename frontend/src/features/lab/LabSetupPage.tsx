import { useEffect, useState } from 'react'
import { App, Button, Card, Input, InputNumber, Select, Space, Tag } from 'antd'
import { BulbOutlined, DeleteOutlined, ExperimentOutlined, PlusOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import { FadeIn } from '../../components/motion'
import { useAuth } from '../../auth/AuthContext'
import { errorMessage } from '../../api/errors'
import { type ParameterLine, useLabMaterials, useLabParameters, useSaveLabParameters } from './labApi'

/** Tests usual for each kind of raw material in a tile plant: one tap adds the ones not there yet. No ranges: the plant sets its own. */
const PRESETS: Record<string, { code: string; unit: string }[]> = {
  clay: [
    { code: 'MOIST', unit: '%' }, { code: 'RES63', unit: '%' }, { code: 'DSHR', unit: '%' }, { code: 'FSHR', unit: '%' },
    { code: 'WA', unit: '%' }, { code: 'LSTAR', unit: 'L*' },
  ],
  feldspar: [{ code: 'MOIST', unit: '%' }, { code: 'RES63', unit: '%' }, { code: 'FE2O3', unit: '%' }, { code: 'LSTAR', unit: 'L*' }],
  quartz: [{ code: 'MOIST', unit: '%' }, { code: 'RES63', unit: '%' }, { code: 'FE2O3', unit: '%' }, { code: 'LSTAR', unit: 'L*' }],
  talc: [{ code: 'MOIST', unit: '%' }, { code: 'LOI', unit: '%' }],
  coal: [{ code: 'MOIST', unit: '%' }, { code: 'ASH', unit: '%' }, { code: 'GCV', unit: 'kcal/kg' }],
}

/**
 * /masters/lab-tests - which tests each material gets when it arrives, and the range the plant accepts. A material
 * with tests waits for the lab at every GRN; one without goes straight into stock.
 */
export default function LabSetupPage() {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const { can } = useAuth()
  const canManage = can('MASTER_MANAGE')
  const materials = useLabMaterials()
  const [materialId, setMaterialId] = useState<number>()
  const params = useLabParameters(materialId)
  const save = useSaveLabParameters()
  const [lines, setLines] = useState<ParameterLine[]>([])

  // start on the first material that has tests, else the first one
  useEffect(() => {
    if (materialId === undefined && materials.data?.length) {
      setMaterialId((materials.data.find((m) => m.tests > 0) ?? materials.data[0]).materialId)
    }
  }, [materials.data, materialId])
  useEffect(() => {
    setLines((params.data ?? []).filter((p) => p.active).map(({ id, code, name, unit, minValue, maxValue }) => ({ id, code, name, unit, minValue, maxValue })))
  }, [params.data])

  const chosen = materials.data?.find((m) => m.materialId === materialId)
  const change = (i: number, patch: Partial<ParameterLine>) => setLines((all) => all.map((l, j) => (j === i ? { ...l, ...patch } : l)))
  const addPreset = (key: string) => setLines((all) => [
    ...all,
    ...PRESETS[key].filter((p) => !all.some((l) => l.code === p.code)).map((p) => ({ code: p.code, name: t(`lab.params.${p.code}`), unit: p.unit })),
  ])

  const onSave = async () => {
    if (!materialId) return
    if (lines.some((l) => !l.code.trim() || !l.name.trim())) {
      message.error(t('lab.codeAndName'))
      return
    }
    try {
      await save.mutateAsync({ materialId, parameters: lines.map((l) => ({ ...l, code: l.code.trim().toUpperCase(), name: l.name.trim(), unit: l.unit?.trim() || undefined })) })
      message.success(t('common.saved'))
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  return (
    <>
      <PageHeader title={t('lab.setupTitle')} subtitle={t('lab.setupSubtitle')} icon={<ExperimentOutlined />} />
      <FadeIn delay={0.04} className="toolbar">
        <Select style={{ minWidth: 320 }} showSearch={{ optionFilterProp: 'label' }} value={materialId} onChange={setMaterialId}
          loading={materials.isLoading} placeholder={t('lab.chooseMaterial')}
          options={(materials.data ?? []).map((m) => ({
            value: m.materialId,
            label: `${m.name} · ${m.code}${m.tests ? ` (${t('lab.testsN', { count: m.tests })})` : ''}`,
          }))} />
        {chosen && (chosen.tests > 0
          ? <Tag color="orange" bordered={false}>{t('lab.waitsForLab')}</Tag>
          : <Tag bordered={false}>{t('lab.straightToStock')}</Tag>)}
      </FadeIn>

      {materials.data && materials.data.length === 0 && <div className="panel"><EmptyState title={t('lab.noMaterials')} /></div>}

      {chosen && (
        <FadeIn delay={0.08}>
          <Card className="data-card lab-setup">
            {canManage && (
              <div className="pm-suggest">
                <div className="pm-suggest-head"><BulbOutlined /> {t('lab.presetsHead')}</div>
                <div className="pm-suggest-chips">
                  {Object.keys(PRESETS).map((k) => <Tag key={k} className="pm-chip" onClick={() => addPreset(k)}>{t(`lab.presets.${k}`)}</Tag>)}
                </div>
              </div>
            )}
            {lines.length === 0 && <p className="cell-sub">{t('lab.noTestsYet')}</p>}
            {lines.length > 0 && (
              <div className="lab-param lab-param-head">
                <span>{t('lab.code')}</span><span>{t('lab.testName')}</span><span>{t('lab.unit')}</span>
                <span>{t('lab.min')}</span><span>{t('lab.max')}</span><span />
              </div>
            )}
            {lines.map((l, i) => (
              <div key={l.id ?? `new-${i}`} className="lab-param">
                <Input value={l.code} maxLength={10} disabled={!canManage} aria-label={t('lab.code')}
                  onChange={(e) => change(i, { code: e.target.value.toUpperCase() })} />
                <Input value={l.name} maxLength={60} disabled={!canManage} aria-label={t('lab.testName')} onChange={(e) => change(i, { name: e.target.value })} />
                <Input value={l.unit} maxLength={15} disabled={!canManage} aria-label={t('lab.unit')} onChange={(e) => change(i, { unit: e.target.value })} />
                <InputNumber<number> value={l.minValue} disabled={!canManage} placeholder={t('lab.open')} aria-label={t('lab.min')}
                  onChange={(v) => change(i, { minValue: v ?? undefined })} />
                <InputNumber<number> value={l.maxValue} disabled={!canManage} placeholder={t('lab.open')} aria-label={t('lab.max')}
                  onChange={(v) => change(i, { maxValue: v ?? undefined })} />
                {canManage ? <Button type="text" danger icon={<DeleteOutlined />} aria-label={t('common.delete')}
                  onClick={() => setLines((all) => all.filter((_, j) => j !== i))} /> : <span />}
              </div>
            ))}
            {canManage && (
              <Space className="lab-setup-actions">
                <Button icon={<PlusOutlined />} onClick={() => setLines((all) => [...all, { code: '', name: '' }])}>{t('lab.addTest')}</Button>
                <Button type="primary" loading={save.isPending} onClick={onSave}>{t('common.save')}</Button>
              </Space>
            )}
            <p className="cell-sub lab-setup-hint">{t('lab.setupHint')}</p>
          </Card>
        </FadeIn>
      )}
    </>
  )
}
