import { useEffect } from 'react'
import { Alert, App, Button, Drawer, Form, Input, Space } from 'antd'
import { useTranslation } from 'react-i18next'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import type { Role, RoleRequest } from '../../api/types'
import { blankToUndefined } from '../../api/validation'
import { useDrawerSize } from '../../components/useDrawerSize'
import { useAuth } from '../../auth/AuthContext'
import { usePermissions, useSaveRole } from './api'
import PermissionPicker from './PermissionPicker'

interface Props {
  open: boolean
  role?: Role
  onClose: () => void
}

export default function RoleFormDrawer({ open, role, onClose }: Props) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const { can } = useAuth()
  const drawerSize = useDrawerSize()
  const [form] = Form.useForm<RoleRequest>()
  const save = useSaveRole()
  const permissions = usePermissions(open)
  const readOnly = role?.systemRole === true || !can('ROLE_MANAGE')

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(role ?? { permissions: [] })
  }, [open, role, form])

  const onFinish = async (values: RoleRequest) => {
    try {
      await save.mutateAsync({ id: role?.id, body: blankToUndefined(values) })
      message.success(t('common.saved'))
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  return (
    <Drawer
      title={role ? (readOnly ? role.name : t('role.edit')) : t('role.new')}
      open={open}
      onClose={onClose}
      size={drawerSize}
      destroyOnHidden
      extra={
        !readOnly && (
          <Space>
            <Button onClick={onClose}>{t('common.cancel')}</Button>
            <Button type="primary" loading={save.isPending} onClick={() => form.submit()}>
              {t('common.save')}
            </Button>
          </Space>
        )
      }
    >
      {role?.systemRole && <Alert type="info" showIcon title={t('role.systemHint')} style={{ marginBottom: 16 }} />}
      <Form form={form} layout="vertical" onFinish={onFinish} disabled={readOnly}>
        <Form.Item name="name" label={t('role.name')} rules={[{ required: true, message: t('common.required') }]}>
          <Input maxLength={80} placeholder="Kiln Supervisor" />
        </Form.Item>
        <Form.Item name="description" label={t('role.description')}>
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
        <Form.Item name="permissions" label={t('role.permissions')}>
          <PermissionPicker permissions={permissions.data ?? []} disabled={readOnly} />
        </Form.Item>
      </Form>
    </Drawer>
  )
}
