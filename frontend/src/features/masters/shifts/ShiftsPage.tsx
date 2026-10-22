import { useCallback, useEffect, useState } from 'react'
import { App, Button, Card, Col, Drawer, Form, Input, InputNumber, Row, Space, Switch, Table, Tag, TimePicker, type TableColumnsType } from 'antd'
import { ClockCircleOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import dayjs, { type Dayjs } from 'dayjs'
import PageHeader from '../../../components/PageHeader'
import StatusTag from '../../../components/StatusTag'
import EmptyState from '../../../components/EmptyState'
import { FadeIn } from '../../../components/motion'
import { useDrawerSize } from '../../../components/useDrawerSize'
import { useOpenFromUrl } from '../../../components/useOpenFromUrl'
import { useAuth } from '../../../auth/AuthContext'
import { applyFieldErrors, errorMessage } from '../../../api/errors'
import { toUpper } from '../../../api/validation'
import type { Shift, ShiftRequest } from '../../../api/types'
import { useMasterList, useSaveMaster } from '../masterApi'

const SHIFT_CODE = /^[A-Za-z0-9]{1,10}$/

/** Form values: the time pickers work with dayjs objects; the API wants "08:00". */
type ShiftForm = Omit<ShiftRequest, 'startTime' | 'endTime'> & { startTime: Dayjs; endTime: Dayjs }

/** "20:00:00" -> a dayjs on any date; only the time part matters. */
const toTime = (s: string) => dayjs(`2000-01-01T${s}`)

function ShiftFormDrawer({ open, shift, onClose }: { open: boolean; shift?: Shift; onClose: () => void }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const drawerSize = useDrawerSize(440)
  const [form] = Form.useForm<ShiftForm>()
  const save = useSaveMaster<Shift, ShiftRequest>('shifts')
  const start = Form.useWatch('startTime', form)
  const end = Form.useWatch('endTime', form)
  const overnight = start && end && !end.isAfter(start)

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(shift
      ? { ...shift, startTime: toTime(shift.startTime), endTime: toTime(shift.endTime) }
      : { active: true, sortOrder: 1 })
  }, [open, shift, form])

  const onFinish = async (v: ShiftForm) => {
    try {
      const body: ShiftRequest = { ...v, startTime: v.startTime.format('HH:mm'), endTime: v.endTime.format('HH:mm') }
      await save.mutateAsync({ id: shift?.id, body })
      message.success(t('common.saved'))
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }

  return (
    <Drawer
      title={shift ? `${t('shift.edit')} · ${shift.code}` : t('shift.new')}
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
          <Col xs={8}>
            <Form.Item name="code" label={t('common.code')} normalize={toUpper}
              rules={[required, { pattern: SHIFT_CODE, message: t('shift.invalidCode') }]}>
              <Input maxLength={10} placeholder="A" />
            </Form.Item>
          </Col>
          <Col xs={16}>
            <Form.Item name="name" label={t('common.name')} rules={[required]}>
              <Input maxLength={40} placeholder="Day" />
            </Form.Item>
          </Col>
          <Col xs={12}>
            <Form.Item name="startTime" label={t('shift.start')} rules={[required]}>
              <TimePicker format="HH:mm" minuteStep={15} style={{ width: '100%' }} needConfirm={false} />
            </Form.Item>
          </Col>
          <Col xs={12}>
            <Form.Item name="endTime" label={t('shift.end')} rules={[required]}
              extra={overnight ? <Tag color="purple">{t('shift.nextDay')}</Tag> : undefined}>
              <TimePicker format="HH:mm" minuteStep={15} style={{ width: '100%' }} needConfirm={false} />
            </Form.Item>
          </Col>
          <Col xs={12}>
            <Form.Item name="sortOrder" label={t('shift.sortOrder')} rules={[required]}>
              <InputNumber min={0} max={99} precision={0} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={12}>
            <Form.Item name="active" label={t('common.status')} valuePropName="checked">
              <Switch checkedChildren={t('common.active')} unCheckedChildren={t('common.inactive')} />
            </Form.Item>
          </Col>
        </Row>
      </Form>
    </Drawer>
  )
}

export default function ShiftsPage() {
  const { t } = useTranslation()
  const { can } = useAuth()
  const canManage = can('MASTER_MANAGE')
  const { data, isLoading } = useMasterList<Shift>('shifts')
  const [editing, setEditing] = useState<{ shift?: Shift } | null>(null)
  const openFromUrl = useCallback((item?: Shift) => setEditing(item ? { shift: item } : {}), [])
  useOpenFromUrl({ items: data, onOpen: openFromUrl, enabled: canManage })

  const newButton = canManage && (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({})}>{t('shift.new')}</Button>
  )

  const columns: TableColumnsType<Shift> = [
    { title: t('common.code'), dataIndex: 'code', width: 100, render: (code: string) => <span className="code-badge">{code}</span> },
    { title: t('common.name'), dataIndex: 'name', render: (name: string) => <span className="cell-title">{name}</span> },
    {
      title: `${t('shift.start')} – ${t('shift.end')}`,
      key: 'time',
      width: 220,
      render: (_, s) => (
        <span className="cell-title">
          {s.startTime.slice(0, 5)} – {s.endTime.slice(0, 5)}
          {s.crossesMidnight && <Tag color="purple" style={{ marginInlineStart: 8 }}>{t('shift.nextDay')}</Tag>}
        </span>
      ),
    },
    { title: t('common.status'), dataIndex: 'active', width: 120, responsive: ['sm'], render: (a: boolean) => <StatusTag active={a} /> },
  ]
  if (canManage) {
    columns.push({
      key: 'actions',
      width: 56,
      render: (_, shift) => <Button type="text" icon={<EditOutlined />} aria-label={t('common.edit')} onClick={() => setEditing({ shift })} />,
    })
  }

  return (
    <>
      <PageHeader title={t('shift.title')} subtitle={t('shift.subtitle')} icon={<ClockCircleOutlined />} extra={newButton} />
      <FadeIn delay={0.08}>
        <Card className="data-card" styles={{ body: { padding: 0 } }}>
          <Table<Shift>
            rowKey="id"
            columns={columns}
            dataSource={data}
            loading={isLoading}
            pagination={false}
            locale={{ emptyText: isLoading ? ' ' : <EmptyState title={t('shift.empty')} action={newButton} /> }}
            onRow={(shift) => (canManage ? { onDoubleClick: () => setEditing({ shift }) } : {})}
          />
        </Card>
      </FadeIn>
      <ShiftFormDrawer open={editing !== null} shift={editing?.shift} onClose={() => setEditing(null)} />
    </>
  )
}
