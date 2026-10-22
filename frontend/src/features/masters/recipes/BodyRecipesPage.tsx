import { useCallback, useEffect, useMemo, useState } from 'react'
import { App, Button, Card, Col, Drawer, Form, Input, InputNumber, Row, Select, Space, Switch, Table, type TableColumnsType } from 'antd'
import { EditOutlined, ExperimentOutlined, MinusCircleOutlined, PlusOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../../components/PageHeader'
import StatusTag from '../../../components/StatusTag'
import EmptyState from '../../../components/EmptyState'
import { FadeIn } from '../../../components/motion'
import { useDrawerSize } from '../../../components/useDrawerSize'
import { useDebounced } from '../../../components/useDebounced'
import { useOpenFromUrl } from '../../../components/useOpenFromUrl'
import { useAuth } from '../../../auth/AuthContext'
import { applyFieldErrors, errorMessage } from '../../../api/errors'
import { patterns, toUpper } from '../../../api/validation'
import type { BodyRecipe, BodyRecipeRequest } from '../../../api/types'
import { useMasterList, useSaveMaster } from '../masterApi'
import { useMaterials } from '../materials/api'

interface MaterialPick {
  value: number
  label: string
  unit: string
}

/**
 * Material dropdown that searches the server as you type (a plant can have hundreds of materials,
 * mostly spares, so loading them all would be wasteful). Materials already on the recipe are always shown.
 */
function useMaterialPicks(search: string, known: MaterialPick[]) {
  const debounced = useDebounced(search, 250)
  const { data, isFetching } = useMaterials({ search: debounced || undefined, active: true, page: 0, size: 30 })
  const options = useMemo(() => {
    const map = new Map<number, MaterialPick>(known.map((k) => [k.value, k]))
    for (const m of data?.content ?? []) map.set(m.id, { value: m.id, label: `${m.code} · ${m.name}`, unit: m.unitCode })
    return [...map.values()]
  }, [data, known])
  return { options, isFetching }
}

function RecipeFormDrawer({ open, recipe, onClose }: { open: boolean; recipe?: BodyRecipe; onClose: () => void }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const drawerSize = useDrawerSize(620)
  const [form] = Form.useForm<BodyRecipeRequest>()
  const save = useSaveMaster<BodyRecipe, BodyRecipeRequest>('body-recipes')
  const [search, setSearch] = useState('')
  const known = useMemo<MaterialPick[]>(
    () => (recipe?.lines ?? []).map((l) => ({ value: l.materialId, label: `${l.materialCode} · ${l.materialName}`, unit: l.unitCode })),
    [recipe],
  )
  const { options, isFetching } = useMaterialPicks(search, known)
  const unitOf = (id?: number) => options.find((o) => o.value === id)?.unit
  const lines = Form.useWatch('lines', form) as BodyRecipeRequest['lines'] | undefined

  // Materials weighed in TON should make up the whole body: show their total as a percentage.
  const tonTotal = (lines ?? []).reduce((sum, l) => sum + (l && unitOf(l.materialId) === 'TON' ? l.qtyPerTon ?? 0 : 0), 0)

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(recipe
      ? { ...recipe, lines: recipe.lines.map((l) => ({ materialId: l.materialId, qtyPerTon: l.qtyPerTon })) }
      : { active: true, lines: [{} as BodyRecipeRequest['lines'][number]] })
  }, [open, recipe, form])

  const onFinish = async (values: BodyRecipeRequest) => {
    try {
      await save.mutateAsync({ id: recipe?.id, body: values })
      message.success(t('common.saved'))
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }

  return (
    <Drawer
      title={recipe ? `${t('recipe.edit')} · ${recipe.code}` : t('recipe.new')}
      open={open}
      onClose={onClose}
      size={drawerSize}
      destroyOnHidden
      extra={
        <Space>
          <Button onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="primary" loading={save.isPending} onClick={() => form.submit()}>{t('common.save')}</Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical" onFinish={onFinish}>
        <Row gutter={16}>
          <Col xs={10}>
            <Form.Item name="code" label={t('common.code')} normalize={toUpper}
              rules={[required, { pattern: patterns.code, message: t('company.invalidCode') }]}>
              <Input maxLength={20} placeholder="GVT-STD" />
            </Form.Item>
          </Col>
          <Col xs={14}>
            <Form.Item name="name" label={t('common.name')} rules={[required]}>
              <Input maxLength={80} />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="description" label={t('recipe.description')}>
          <Input maxLength={255} />
        </Form.Item>

        <div className="form-section">{t('recipe.materials')} · {t('recipe.perTon')}</div>
        <Form.List name="lines">
          {(fields, { add, remove }) => (
            <>
              {fields.map((field, index) => {
                const unit = unitOf(lines?.[index]?.materialId)
                const qty = lines?.[index]?.qtyPerTon
                return (
                  <Row key={field.key} gutter={8} align="top">
                    <Col flex="auto">
                      <Form.Item
                        name={[field.name, 'materialId']}
                        rules={[required, {
                          // the server checks too; this just says it sooner
                          validator: async (_, v?: number) => {
                            if (v && (lines ?? []).filter((l) => l?.materialId === v).length > 1) throw new Error(t('recipe.twice'))
                          },
                        }]}
                      >
                        <Select showSearch={{ filterOption: false, onSearch: setSearch }} options={options}
                          loading={isFetching} aria-label={t('store.material')} />
                      </Form.Item>
                    </Col>
                    <Col style={{ width: 170 }}>
                      <Form.Item name={[field.name, 'qtyPerTon']} rules={[required]}
                        extra={unit === 'TON' && qty ? `${t('recipe.share')} ${(qty * 100).toFixed(1)} %` : undefined}>
                        <InputNumber<number> min={0} step={0.01} suffix={unit} style={{ width: '100%' }} aria-label={t('recipe.perTon')} />
                      </Form.Item>
                    </Col>
                    <Col style={{ width: 32, paddingTop: 4 }}>
                      {fields.length > 1 && (
                        <Button type="text" danger icon={<MinusCircleOutlined />} aria-label={t('common.delete')} onClick={() => remove(field.name)} />
                      )}
                    </Col>
                  </Row>
                )
              })}
              <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add({})}>{t('recipe.addMaterial')}</Button>
            </>
          )}
        </Form.List>
        <div className={`recipe-total${Math.abs(tonTotal - 1) < 0.0005 ? ' ok' : ''}`} title={t('recipe.bodyTotalHint')}>
          {t('recipe.bodyTotal')}: <strong>{(tonTotal * 100).toFixed(1)} %</strong>
        </div>

        <Form.Item name="active" label={t('common.status')} valuePropName="checked" style={{ marginTop: 16 }}>
          <Switch checkedChildren={t('common.active')} unCheckedChildren={t('common.inactive')} />
        </Form.Item>
      </Form>
    </Drawer>
  )
}

export default function BodyRecipesPage() {
  const { t } = useTranslation()
  const { can } = useAuth()
  const canManage = can('MASTER_MANAGE')
  const { data, isLoading } = useMasterList<BodyRecipe>('body-recipes')
  const [editing, setEditing] = useState<{ recipe?: BodyRecipe } | null>(null)
  const openFromUrl = useCallback((item?: BodyRecipe) => setEditing(item ? { recipe: item } : {}), [])
  useOpenFromUrl({ items: data, onOpen: openFromUrl, enabled: canManage })

  const newButton = canManage && (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({})}>{t('recipe.new')}</Button>
  )

  const columns: TableColumnsType<BodyRecipe> = [
    { title: t('common.code'), dataIndex: 'code', width: 140, render: (code: string) => <span className="code-badge">{code}</span> },
    {
      title: t('common.name'),
      dataIndex: 'name',
      render: (name: string, r) => (
        <>
          <div className="cell-title">{name}</div>
          <div className="cell-sub">
            {r.lines.map((l) => `${l.materialCode} ${l.unitCode === 'TON' ? `${+(l.qtyPerTon * 100).toFixed(1)}%` : `${l.qtyPerTon} ${l.unitCode}`}`).join(' · ')}
          </div>
        </>
      ),
    },
    { title: t('common.status'), dataIndex: 'active', width: 120, responsive: ['sm'], render: (a: boolean) => <StatusTag active={a} /> },
  ]
  if (canManage) {
    columns.push({
      key: 'actions',
      width: 56,
      render: (_, recipe) => <Button type="text" icon={<EditOutlined />} aria-label={t('common.edit')} onClick={() => setEditing({ recipe })} />,
    })
  }

  return (
    <>
      <PageHeader title={t('recipe.title')} subtitle={t('recipe.subtitle')} icon={<ExperimentOutlined />} extra={newButton} />
      <FadeIn delay={0.08}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<BodyRecipe>
            rowKey="id"
            columns={columns}
            dataSource={data}
            loading={isLoading}
            pagination={false}
            locale={{ emptyText: isLoading ? ' ' : <EmptyState title={t('recipe.empty')} action={newButton} /> }}
            onRow={(recipe) => (canManage ? { onDoubleClick: () => setEditing({ recipe }) } : {})}
          />
        </Card>
      </FadeIn>
      <RecipeFormDrawer open={editing !== null} recipe={editing?.recipe} onClose={() => setEditing(null)} />
    </>
  )
}
