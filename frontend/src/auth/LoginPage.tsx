import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Alert, Button, Form, Input } from 'antd'
import { ArrowRightOutlined, LockOutlined, UserOutlined } from '@ant-design/icons'
import { AnimatePresence, motion } from 'motion/react'
import { useTranslation } from 'react-i18next'
import { errorMessage } from '../api/errors'
import { useAuth } from './AuthContext'
import AuthShell from './AuthShell'

interface LoginForm {
  username: string
  password: string
}

export default function LoginPage() {
  const { t } = useTranslation()
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // Already logged in? Go straight in.
  if (user) return <Navigate to="/" replace />

  const onFinish = async ({ username, password }: LoginForm) => {
    setError(null)
    setSubmitting(true)
    try {
      const u = await login(username.trim(), password)
      const from = (location.state as { from?: string } | null)?.from
      navigate(u.mustChangePassword ? '/change-password' : (from ?? '/'), { replace: true })
    } catch (err) {
      setError(errorMessage(err, t('auth.loginFailed')))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthShell>
      <h1 className="auth-form-title">{t('auth.welcome')}</h1>
      <div className="auth-form-sub">{t('auth.welcomeSub')}</div>

      {/* The error slides in, and gives a small shake so it is noticed. */}
      <AnimatePresence>
        {error && (
          <motion.div
            key={error}
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto', x: [0, -6, 6, -4, 4, 0] }}
            exit={{ opacity: 0, height: 0 }}
            transition={{ duration: 0.4 }}
          >
            <Alert type="error" title={error} showIcon style={{ marginBottom: 16 }} />
          </motion.div>
        )}
      </AnimatePresence>

      <Form<LoginForm> layout="vertical" size="large" onFinish={onFinish} requiredMark={false}>
        <Form.Item name="username" label={t('auth.username')} rules={[{ required: true, message: t('common.required') }]}>
          <Input prefix={<UserOutlined />} autoComplete="username" autoCapitalize="none" autoFocus />
        </Form.Item>
        <Form.Item name="password" label={t('auth.password')} rules={[{ required: true, message: t('common.required') }]}>
          <Input.Password prefix={<LockOutlined />} autoComplete="current-password" />
        </Form.Item>
        <Button
          type="primary"
          htmlType="submit"
          block
          loading={submitting}
          icon={<ArrowRightOutlined />}
          iconPlacement="end"
          className="btn-arrow"
          style={{ marginTop: 8, height: 50 }}
        >
          {t('auth.login')}
        </Button>
      </Form>
    </AuthShell>
  )
}
