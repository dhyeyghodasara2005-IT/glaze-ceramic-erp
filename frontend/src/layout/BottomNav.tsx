import { NavLink, useLocation } from 'react-router-dom'
import { AppstoreOutlined } from '@ant-design/icons'
import { motion } from 'motion/react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../auth/AuthContext'
import { MENU, isActivePath, isGroup, type MenuEntry } from './menu'

/**
 * Floating bar at the bottom of phone screens: the most-used pages within thumb reach.
 * Entries marked `mobile: true` in menu.tsx appear here (max 3 besides Home); "More" opens the full menu.
 */
export default function BottomNav({ onMore }: { onMore: () => void }) {
  const { t } = useTranslation()
  const { can } = useAuth()
  const location = useLocation()

  const all: MenuEntry[] = MENU.flatMap((e) => (isGroup(e) ? e.items : [e]))
  const home = all.find((e) => e.path === '/')
  const quick = all.filter((e) => e.mobile && (!e.permission || can(e.permission))).slice(0, 3)
  const items = [...(home ? [home] : []), ...quick]

  return (
    <nav className="bottom-nav" aria-label="Quick">
      {items.map((e) => {
        const active = isActivePath(location.pathname, e.path)
        return (
          <NavLink key={e.path} to={e.path} end={e.path === '/'} className={`bottom-nav-item${active ? ' active' : ''}`}>
            {active && (
              <motion.div
                layoutId="bottomnav-active"
                className="bottom-nav-active"
                transition={{ type: 'spring', stiffness: 460, damping: 36 }}
              />
            )}
            {e.icon}
            <span className="bottom-nav-label">{t(e.labelKey)}</span>
          </NavLink>
        )
      })}
      <button type="button" className="bottom-nav-item" onClick={onMore}>
        <AppstoreOutlined />
        <span className="bottom-nav-label">{t('menu.more')}</span>
      </button>
    </nav>
  )
}
