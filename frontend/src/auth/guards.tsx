import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { Button, Result, Spin } from 'antd'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import type { Permission } from '../api/types'
import { useAuth } from './AuthContext'

/** Only lets logged-in users through. Forces a password change first if required. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return (
      <div className="center-screen">
        <Spin size="large" />
      </div>
    )
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  if (user.mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />
  }
  return <>{children}</>
}

/**
 * Hides a page from users without the permission.
 * This is only for a nice UX - the backend enforces the same rule with @PreAuthorize.
 */
export function RequirePermission({ permission, children }: { permission: Permission; children: ReactNode }) {
  const { can } = useAuth()
  const { t } = useTranslation()
  if (!can(permission)) {
    return (
      <Result
        status="403"
        title={t('errors.forbidden')}
        subTitle={t('errors.forbiddenHint')}
        extra={
          <Link to="/">
            <Button type="primary">{t('errors.goHome')}</Button>
          </Link>
        }
      />
    )
  }
  return <>{children}</>
}
