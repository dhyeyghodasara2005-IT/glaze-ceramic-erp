import { useEffect } from 'react'
import { App, Button, Drawer, Form, Input, Space, Switch } from 'antd'
import { useTranslation } from 'react-i18next'
import { useDrawerSize } from '../../components/useDrawerSize'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import type { FgGodown, FgGodownRequest } from '../../api/types'
import { toUpper } from '../../api/validation'
import { useSaveFgGodown } from './fgApi'

type Values = Omit<FgGodownRequest, 'plantId'>

/** New / edit finished-goods godown: just a code and a name. Its pillars are added on the map. */
export default function FgGodownDrawer({ open, plantId, godown, onClose, onSaved }: {
  open: boolean
  plantId: number
  godown?: FgGodown
  onClose: () => void
  onSaved?: (g: FgGodown) => void
}) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const drawerSize = useDrawerSize(440)
  const [form] = Form.useForm<Values>()
  const save = useSaveFgGodown()

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(godown ?? { active: true })
  }, [open, godown, form])

  const onFinish = async (v: Values) => {
    try {
      const saved = await save.mutateAsync({ id: godown?.id, body: { ...v, plantId, name: v.name.trim() } })
      message.success(t('common.saved'))
      onSaved?.(saved)
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const required = { required: true, message: t('common.required') }
  return (
    <Drawer open={open} onClose={onClose} size={drawerSize} destroyOnHidden
      title={godown ? t('fg.editGodown') : t('fg.newGodown')}
      extra={(
        <Space>
          <Button onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="primary" loading={save.isPending} onClick={() => form.submit()}>{t('common.save')}</Button>
        </Space>
      )}>
      <Form<Values> form={form} layout="vertical" onFinish={onFinish}>
        <Form.Item name="code" label={t('common.code')} normalize={toUpper}
          rules={[required, { pattern: /^[A-Z0-9-]+$/, message: t('fg.codeRule') }]}>
          <Input maxLength={20} placeholder="FG-1" />
        </Form.Item>
        <Form.Item name="name" label={t('common.name')} rules={[required]}>
          <Input maxLength={80} placeholder={t('fg.godownNameHint')} />
        </Form.Item>
        {godown && (
          <Form.Item name="active" label={t('common.status')} valuePropName="checked">
            <Switch checkedChildren={t('common.active')} unCheckedChildren={t('common.inactive')} />
          </Form.Item>
        )}
      </Form>
    </Drawer>
  )
}
