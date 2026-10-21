import type { ReactNode } from 'react'
import { motion } from 'motion/react'
import { useTranslation } from 'react-i18next'
import { CheckOutlined, GlobalOutlined, MobileOutlined } from '@ant-design/icons'
import Logo from '../components/Logo'
import LanguageSwitch from '../layout/LanguageSwitch'
import { EASE, FadeIn, Stagger, StaggerItem } from '../components/motion'

// Which tiles in the 6x6 mosaic get which glaze. '' = plain glass tile.
const PATTERN = [
  '', 'cream', '', 'warm', '', 'deep',
  'warm', '', 'deep', '', 'cream', '',
  '', 'deep', 'warm', 'cream', '', 'warm',
  'cream', '', '', 'warm', 'deep', '',
  '', 'warm', 'cream', '', 'warm', 'cream',
  'deep', '', 'warm', 'deep', '', '',
]

/** Decorative wall of tiles that pops in one by one, then gently "breathes". */
function Mosaic() {
  return (
    <div className="auth-mosaic" aria-hidden>
      {PATTERN.map((kind, i) => (
        <motion.div
          key={i}
          initial={{ opacity: 0, scale: 0.6 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.6, ease: EASE, delay: 0.15 + i * 0.022 }}
        >
          <div className={`auth-tile ${kind}`} style={{ animationDelay: `${(i % 7) * 0.85}s` }} />
        </motion.div>
      ))}
    </div>
  )
}

/**
 * Two-column frame for the login and change-password screens.
 * Left: brand story with the animated mosaic (hidden on phones). Right: the form.
 */
export default function AuthShell({ children }: { children: ReactNode }) {
  const { t } = useTranslation()
  const points = [
    { icon: <MobileOutlined />, text: t('auth.heroPoint1') },
    { icon: <GlobalOutlined />, text: t('auth.heroPoint2') },
    { icon: <CheckOutlined />, text: t('auth.heroPoint3') },
  ]

  return (
    <div className="auth-shell">
      <aside className="auth-brand-panel">
        <div className="auth-sheen" />
        <Mosaic />
        <FadeIn>
          <Logo tone="light" size={42} />
        </FadeIn>
        <div>
          <FadeIn delay={0.1}>
            <h2 className="auth-hero-title">{t('auth.heroTitle')}</h2>
          </FadeIn>
          <FadeIn delay={0.18}>
            <p className="auth-hero-sub">{t('auth.heroSub')}</p>
          </FadeIn>
          <Stagger className="auth-points">
            {points.map((p) => (
              <StaggerItem key={p.text} className="auth-point">
                <span className="auth-point-icon">{p.icon}</span>
                {p.text}
              </StaggerItem>
            ))}
          </Stagger>
        </div>
        <div className="auth-crafted">{t('auth.crafted')}</div>
      </aside>

      <main className="auth-form-side">
        <div className="auth-form-top">
          <span className="auth-mobile-logo">
            <Logo size={36} />
          </span>
          <span />
          <LanguageSwitch />
        </div>
        <FadeIn className="auth-form-card" delay={0.08} y={16}>
          {children}
        </FadeIn>
      </main>
    </div>
  )
}
