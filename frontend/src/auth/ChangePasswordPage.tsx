import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Alert, App, Button, Form, Input, Space } from 'antd'
import { motion } from 'motion/react'
import { useTranslation } from 'react-i18next'
import { api } from '../api/client'
import { applyFieldErrors, errorMessage } from '../api/errors'
import { PASSWORD_MAX, PASSWORD_MIN } from '../api/validation'
import { useAuth } from './AuthContext'
import AuthShell from './AuthShell'

interface ChangePasswordForm {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

/** 0-4: length, upper+lower case, a digit, a symbol or 12+ characters. */
function strength(pw: string): number {
  if (!pw) return 0
  let score = pw.length >= PASSWORD_MIN ? 1 : 0
  if (/[a-z]/.test(pw) && /[A-Z]/.test(pw)) score++
  if (/\d/.test(pw)) score++
  if (/[^A-Za-z0-9]/.test(pw) || pw.length >= 12) score++
  return score
}
const STRENGTH_COLORS = ['#E5DDD6', '#DC2626', '#D97706', '#65A30D', '#15803D']

function StrengthMeter({ password }: { password: string }) {
  const score = strength(password)
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 6, margin: '-12px 0 20px' }} aria-hidden>
      {[1, 2, 3, 4].map((i) => (
        <div key={i} style={{ height: 5, borderRadius: 4, background: '#EDE5DE', overflow: 'hidden' }}>
          <motion.div
            style={{ height: '100%', background: STRENGTH_COLORS[score], originX: 0 }}
            animate={{ scaleX: score >= i ? 1 : 0 }}
            transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
          />
        </div>
      ))}
    </div>
  )
}

/** Shown on first login (forced) or from the user menu. */
export default function ChangePasswordPage() {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const { user, refresh, logout } = useAuth()
  const navigate = useNavigate()
  const [form] = Form.useForm<ChangePasswordForm>()
  const newPassword = Form.useWatch('newPassword', form) ?? ''
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const forced = user?.mustChangePassword ?? false

  const onFinish = async (values: ChangePasswordForm) => {
    setError(null)
    setSubmitting(true)
    try {
      await api.post('/auth/change-password', {
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      })
      await refresh()
      message.success(t('auth.passwordChanged'))
      navigate('/', { replace: true })
    } catch (err) {
      if (!applyFieldErrors(form, err)) setError(errorMessage(err, t('common.somethingWrong')))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthShell>
      <h1 className="auth-form-title">{t('auth.changeTitle')}</h1>
      <div className="auth-form-sub">{forced ? t('auth.changeForced') : t('auth.passwordLength')}</div>
      {error && <Alert type="error" title={error} showIcon style={{ marginBottom: 16 }} />}

      <Form form={form} layout="vertical" size="large" onFinish={onFinish} requiredMark={false}>
        <Form.Item name="currentPassword" label={t('auth.currentPassword')} rules={[{ required: true, message: t('common.required') }]}>
          <Input.Password autoComplete="current-password" />
        </Form.Item>
        <Form.Item
          name="newPassword"
          label={t('auth.newPassword')}
          rules={[
            { required: true, message: t('common.required') },
            { min: PASSWORD_MIN, max: PASSWORD_MAX, message: t('auth.passwordLength') },
          ]}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <StrengthMeter password={newPassword} />
        <Form.Item
          name="confirmPassword"
          label={t('auth.confirmPassword')}
          dependencies={['newPassword']}
          rules={[
            { required: true, message: t('common.required') },
            ({ getFieldValue }) => ({
              validator: (_, value) =>
                !value || value === getFieldValue('newPassword')
                  ? Promise.resolve()
                  : Promise.reject(new Error(t('auth.passwordsMismatch'))),
            }),
          ]}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Space style={{ marginTop: 8 }}>
          <Button type="primary" htmlType="submit" loading={submitting}>
            {t('common.save')}
          </Button>
          {forced ? (
            <Button onClick={logout}>{t('common.logout')}</Button>
          ) : (
            <Button onClick={() => navigate(-1)}>{t('common.cancel')}</Button>
          )}
        </Space>
      </Form>
    </AuthShell>
  )
}
