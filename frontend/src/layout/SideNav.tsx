import { Fragment } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import { Button, Tooltip } from 'antd'
import { MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons'
import { motion } from 'motion/react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../auth/AuthContext'
import Logo, { LogoMark } from '../components/Logo'
import UserAvatar from '../components/UserAvatar'
import { MENU, isActivePath, isGroup, type MenuEntry } from './menu'

interface Props {
  collapsed?: boolean
  /** Called after a link is clicked (the phone drawer uses it to close itself). */
  onNavigate?: () => void
  /** Shown only on desktop: collapse/expand button. */
  onToggle?: () => void
}

/** A spring makes the active highlight glide between items instead of jumping. */
const SPRING = { type: 'spring', stiffness: 420, damping: 36 } as const

/** Dark sidebar navigation. Used fixed on desktop and inside a drawer on phones. */
export default function SideNav({ collapsed = false, onNavigate, onToggle }: Props) {
  const { t } = useTranslation()
  const { can, user } = useAuth()
  const location = useLocation()
  const visible = (e: MenuEntry) => !e.permission || can(e.permission)

  const renderItem = (e: MenuEntry) => {
    const active = isActivePath(location.pathname, e.path)
    const link = (
      <NavLink to={e.path} end={e.path === '/'} onClick={onNavigate} className={`sidenav-item${active ? ' active' : ''}`}>
        {active && <motion.div layoutId="sidenav-active" className="sidenav-active" transition={SPRING} />}
        {e.icon}
        {!collapsed && <span>{t(e.labelKey)}</span>}
      </NavLink>
    )
    return collapsed ? (
      <Tooltip key={e.path} title={t(e.labelKey)} placement="right">
        {link}
      </Tooltip>
    ) : (
      <Fragment key={e.path}>{link}</Fragment>
    )
  }

  return (
    <div className={`sidenav${collapsed ? ' sidenav-collapsed' : ''}`}>
      <div className="sidenav-brand" style={collapsed ? { justifyContent: 'center', padding: 0 } : undefined}>
        {collapsed ? <LogoMark size={34} /> : <Logo tone="light" size={34} />}
      </div>

      <nav className="sidenav-scroll" aria-label="Main">
        {MENU.map((entry) => {
          if (!isGroup(entry)) return visible(entry) ? renderItem(entry) : null
          const items = entry.items.filter(visible)
          if (items.length === 0) return null
          return (
            <div key={entry.labelKey}>
              {collapsed ? <div className="sidenav-divider" /> : <div className="sidenav-group-label">{t(entry.labelKey)}</div>}
              {items.map(renderItem)}
            </div>
          )
        })}
      </nav>

      <div className="sidenav-footer">
        {user && !collapsed && (
          <div className="sidenav-user">
            <UserAvatar name={user.fullName} size={34} />
            <div style={{ minWidth: 0 }}>
              <div className="sidenav-user-name">{user.fullName}</div>
              <div className="sidenav-user-role">{user.roles.join(', ')}</div>
            </div>
          </div>
        )}
        {onToggle && (
          <Button
            type="text"
            block
            className="sidenav-toggle"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={onToggle}
            aria-label={collapsed ? t('menu.expand') : t('menu.collapse')}
          >
            {!collapsed && t('menu.collapse')}
          </Button>
        )}
      </div>
    </div>
  )
}
