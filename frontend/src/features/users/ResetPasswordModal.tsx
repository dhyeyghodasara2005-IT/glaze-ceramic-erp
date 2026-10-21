import { App, Form, Input, Modal } from 'antd'
import { useTranslation } from 'react-i18next'
import { applyFieldErrors, errorMessage } from '../../api/errors'
import type { User } from '../../api/types'
import { PASSWORD_MAX, PASSWORD_MIN } from '../../api/validation'
import { useResetPassword } from './api'

export default function ResetPasswordModal({ user, onClose }: { user?: User; onClose: () => void }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const [form] = Form.useForm<{ newPassword: string }>()
  const reset = useResetPassword()

  const onFinish = async ({ newPassword }: { newPassword: string }) => {
    if (!user) return
    try {
      await reset.mutateAsync({ id: user.id, newPassword })
      message.success(t('user.resetDone'))
      onClose()
    } catch (err) {
      if (!applyFieldErrors(form, err)) message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  return (
    <Modal
      title={`${t('user.resetPassword')}: ${user?.fullName ?? ''}`}
      open={user !== undefined}
      onCancel={onClose}
      onOk={() => form.submit()}
      okText={t('common.save')}
      cancelText={t('common.cancel')}
      confirmLoading={reset.isPending}
      destroyOnHidden
    >
      <Form form={form} layout="vertical" onFinish={onFinish} preserve={false}>
        <Form.Item
          name="newPassword"
          label={t('user.password')}
          extra={t('user.passwordHint')}
          rules={[
            { required: true, message: t('common.required') },
            { min: PASSWORD_MIN, max: PASSWORD_MAX, message: t('auth.passwordLength') },
          ]}
        >
          <Input.Password autoComplete="new-password" autoFocus />
        </Form.Item>
      </Form>
    </Modal>
  )
}
