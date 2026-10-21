import { useCallback, useEffect, useState } from 'react'
import { useOpenFromUrl } from '../../../components/useOpenFromUrl'
import { App, Button, Card, Col, Drawer, Form, Input, InputNumber, Row, Space, Switch, Table, Tag, type TableColumnsType } from 'antd'
import { EditOutlined, PlusOutlined, TrophyOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../../components/PageHeader'
import StatusTag from '../../../components/StatusTag'
import EmptyState from '../../../components/EmptyState'
import { FadeIn } from '../../../components/motion'
import { useDrawerSize } from '../../../components/useDrawerSize'
import { useAuth } from '../../../auth/AuthContext'
import { applyFieldErrors, errorMessage } from '../../../api/errors'
import { blankToUndefined, patterns, toUpper } from '../../../api/validation'
import type { Grade, GradeRequest } from '../../../api/types'
import { useMasterList, useSaveMaster } from '../masterApi'

function GradeFormDrawer({ open, grade, nextOrder, onClose }: {
  open: boolean
  grade?: Grade
  /** suggested sort order for a new grade (after the last one) */
  nextOrder: number
  onClose: () => void
}) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const drawerSize = useDrawerSize(480)
  const [form] = Form.useForm<GradeRequest>()
  const save = useSaveMaster<Grade, GradeRequest>('grades')

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(grade ?? { active: true, sellable: true, sortOrder: nextOrder })
  }, [open, grade, nextOrder, form])

  const onFinish = async (values: GradeRequest) => {
    try {
      await save.mutateAsync({ id: grade?.id, body: blankToUndefined(values) })
      message.success(t('common.saved'))
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }

  return (
    <Drawer
      title={grade ? t('grade.edit') : t('grade.new')}
      open={open}
      onClose={onClose}
      size={drawerSize}
      destroyOnHidden
      extra={
        <Space>
          <Button onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="primary" loading={save.isPending} onClick={() => form.submit()}>
            {t('common.save')}
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical" onFinish={onFinish}>
        <Form.Item
          name="code"
          label={t('common.code')}
          normalize={toUpper}
          rules={[required, { pattern: patterns.code, message: t('company.invalidCode') }]}
        >
          <Input maxLength={20} placeholder="PREMIUM" />
        </Form.Item>
        <Form.Item name="name" label={t('common.name')} rules={[required]}>
          <Input maxLength={60} placeholder="Premium" />
        </Form.Item>
        <Row gutter={16}>
          <Col xs={12}>
            <Form.Item name="sortOrder" label={t('grade.sortOrder')} tooltip={t('grade.sortHint')} rules={[required]}>
              <InputNumber min={0} max={999} precision={0} inputMode="numeric" style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={12}>
            <Form.Item name="sellable" label={t('grade.sellable')} tooltip={t('grade.sellableHint')} valuePropName="checked">
              <Switch checkedChildren={t('common.yes')} unCheckedChildren={t('common.no')} />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="active" label={t('common.status')} valuePropName="checked">
          <Switch checkedChildren={t('common.active')} unCheckedChildren={t('common.inactive')} />
        </Form.Item>
      </Form>
    </Drawer>
  )
}

export default function GradesPage() {
  const { t } = useTranslation()
  const { can } = useAuth()
  const canManage = can('MASTER_MANAGE')
  const { data, isLoading } = useMasterList<Grade>('grades')
  const [editing, setEditing] = useState<{ grade?: Grade } | null>(null)
  // Search palette / links: ?open=<id> edits a record, ?new=1 opens the empty form.
  const openFromUrl = useCallback((item?: Grade) => setEditing(item ? { grade: item } : {}), [])
  useOpenFromUrl({ items: data, onOpen: openFromUrl, enabled: canManage })
  const nextOrder = (data ?? []).filter((g) => g.sellable).reduce((max, g) => Math.max(max, g.sortOrder), 0) + 1

  const newButton = canManage && (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({})}>
      {t('grade.new')}
    </Button>
  )

  const columns: TableColumnsType<Grade> = [
    { title: '#', dataIndex: 'sortOrder', width: 64, align: 'center', render: (n: number) => <span className="roadmap-step">{n}</span> },
    { title: t('common.code'), dataIndex: 'code', width: 150, render: (code: string) => <span className="code-badge">{code}</span> },
    {
      title: t('common.name'),
      dataIndex: 'name',
      render: (name: string, g) => (
        <Space>
          <span className="cell-title">{name}</span>
          {!g.sellable && <Tag color="red">{t('grade.notSellable')}</Tag>}
        </Space>
      ),
    },
    { title: t('common.status'), dataIndex: 'active', width: 120, responsive: ['md'], render: (a: boolean) => <StatusTag active={a} /> },
  ]
  if (canManage) {
    columns.push({
      key: 'actions',
      width: 56,
      render: (_, grade) => (
        <Button type="text" icon={<EditOutlined />} aria-label={t('common.edit')} onClick={() => setEditing({ grade })} />
      ),
    })
  }

  return (
    <>
      <PageHeader title={t('grade.title')} subtitle={t('grade.subtitle')} icon={<TrophyOutlined />} extra={newButton} />
      <FadeIn delay={0.08}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<Grade>
            rowKey="id"
            columns={columns}
            dataSource={data}
            loading={isLoading}
            pagination={false}
            locale={{ emptyText: isLoading ? ' ' : <EmptyState title={t('grade.empty')} action={newButton} /> }}
            onRow={(grade) => (canManage ? { onDoubleClick: () => setEditing({ grade }) } : {})}
          />
        </Card>
      </FadeIn>
      <GradeFormDrawer open={editing !== null} grade={editing?.grade} nextOrder={nextOrder} onClose={() => setEditing(null)} />
    </>
  )
}
