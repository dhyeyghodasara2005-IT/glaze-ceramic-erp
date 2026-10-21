import type { ReactNode } from 'react'
import { FadeIn } from './motion'

/** Page title with an icon badge and a one-line explanation; buttons on the right. */
export default function PageHeader({ title, subtitle, icon, extra }: {
  title: ReactNode
  subtitle?: ReactNode
  icon?: ReactNode
  extra?: ReactNode
}) {
  return (
    <FadeIn className="page-header" y={8}>
      <div className="page-title-row">
        {icon && <span className="page-icon">{icon}</span>}
        <div style={{ minWidth: 0 }}>
          <h1 className="page-title">{title}</h1>
          {subtitle && <div className="page-subtitle">{subtitle}</div>}
        </div>
      </div>
      {extra}
    </FadeIn>
  )
}
