import { Button, Result } from 'antd'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

export default function NotFoundPage() {
  const { t } = useTranslation()
  return (
    <Result
      status="404"
      title={t('errors.notFound')}
      extra={
        <Link to="/">
          <Button type="primary">{t('errors.goHome')}</Button>
        </Link>
      }
    />
  )
}
