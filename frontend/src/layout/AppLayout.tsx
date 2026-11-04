import { useEffect, useState, type ReactNode } from 'react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Button, Drawer, Dropdown, Grid, Result, type MenuProps } from 'antd'
import { DownOutlined, KeyOutlined, LogoutOutlined, SearchOutlined } from '@ant-design/icons'
import { motion } from 'motion/react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../auth/AuthContext'
import { sidenavStore } from '../api/client'
import { LogoMark } from '../components/Logo'
import UserAvatar from '../components/UserAvatar'
import { EASE } from '../components/motion'
import SideNav from './SideNav'
import BottomNav from './BottomNav'
import PlantSwitcher from './PlantSwitcher'
import LanguageSwitch from './LanguageSwitch'
import CommandPalette from '../search/CommandPalette'
import { ALL_PAGES, canSee } from './menu'

const SIDER_OPEN = 256
const SIDER_COLLAPSED = 80
const IS_MAC = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.userAgent)

/**
 * The frame around every page.
 * - PC / tablet landscape (>= 992px): dark sidebar on the left (collapsible), glass top bar.
 * - Phone: top bar + floating bottom bar; "More" opens the full menu in a drawer.
 */
export default function AppLayout() {
  const { t } = useTranslation()
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const screens = Grid.useBreakpoint()
  const isMobile = !screens.lg
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [collapsed, setCollapsed] = useState(() => sidenavStore.get() === 'collapsed')
  const [paletteOpen, setPaletteOpen] = useState(false)

  // Keyboard shortcuts that work on every page:
  //   Ctrl+K / ⌘K  open/close search      Alt+G  "Go To" (like Tally)      /  search (when not typing)
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const typing = (e.target as HTMLElement | null)?.closest('input, textarea, select, [contenteditable="true"]')
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setPaletteOpen((o) => !o)
      } else if (e.altKey && e.code === 'KeyG') {
        e.preventDefault()
        setPaletteOpen(true)
      } else if (e.key === '/' && !typing) {
        e.preventDefault()
        setPaletteOpen(true)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  const toggleCollapsed = () => {
    setCollapsed((c) => {
      sidenavStore.set(c ? 'open' : 'collapsed')
      return !c
    })
  }

  const userMenu: MenuProps['items'] = [
    ...(isMobile ? [{ key: 'lang', label: <LanguageSwitch /> }, { type: 'divider' as const }] : []),
    { key: 'password', icon: <KeyOutlined />, label: t('common.changePassword'), onClick: () => navigate('/change-password') },
    { type: 'divider' },
    { key: 'logout', icon: <LogoutOutlined />, label: t('common.logout'), danger: true, onClick: logout },
  ]

  return (
    <div className="app-shell">
      {!isMobile && (
        <motion.aside
          className="app-sider"
          initial={false}
          animate={{ width: collapsed ? SIDER_COLLAPSED : SIDER_OPEN }}
          transition={{ duration: 0.32, ease: EASE }}
        >
          <SideNav collapsed={collapsed} onToggle={toggleCollapsed} />
        </motion.aside>
      )}
      {isMobile && (
        <Drawer
          placement="left"
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          size={280}
          closable={false}
          styles={{ body: { padding: 0 }, header: { display: 'none' } }}
        >
          <SideNav onNavigate={() => setDrawerOpen(false)} />
        </Drawer>
      )}

      <div className="app-main">
        <header className="app-header">
          <div className="header-left">
            {isMobile && <LogoMark size={32} />}
            <PlantSwitcher />
            {!isMobile && (
              <button type="button" className="search-trigger" onClick={() => setPaletteOpen(true)}>
                <SearchOutlined />
                <span>{t('search.button')}</span>
                <kbd className="kbd">{IS_MAC ? '⌘ K' : 'Ctrl K'}</kbd>
              </button>
            )}
          </div>
          <div className="header-right">
            {isMobile && (
              <Button type="text" icon={<SearchOutlined />} onClick={() => setPaletteOpen(true)} aria-label={t('search.button')} />
            )}
            {!isMobile && <LanguageSwitch />}
            <Dropdown menu={{ items: userMenu }} trigger={['click']} placement="bottomRight">
              <Button type="text" className="user-chip" aria-label={user?.fullName}>
                {user && <UserAvatar name={user.fullName} size={32} />}
                {!isMobile && <span className="user-chip-name">{user?.fullName}</span>}
                <DownOutlined style={{ fontSize: 10, opacity: 0.6 }} />
              </Button>
            </Dropdown>
          </div>
        </header>

        <main className="app-content">
          {/* Each page fades/slides in when you navigate (keyed by URL). */}
          <motion.div
            key={location.pathname}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.35, ease: EASE }}
          >
            <SectionGuard>
              <Outlet />
            </SectionGuard>
          </motion.div>
        </main>
      </div>

      {isMobile && <BottomNav onMore={() => setDrawerOpen(true)} />}
      <CommandPalette open={paletteOpen} onClose={() => setPaletteOpen(false)} />
    </div>
  )
}

/**
 * The menu hides other sections' pages; this keeps them closed when someone types the address. Only the page itself
 * (/production/polish-entries) is closed: a record opened from one's own pages (a lot's glaze entry, from the kiln
 * supervisor's lot page) still opens. The server still checks every request with the role's permissions.
 */
function SectionGuard({ children }: { children: ReactNode }) {
  const { can } = useAuth()
  const { t } = useTranslation()
  const location = useLocation()
  const page = ALL_PAGES.find((p) => p.path !== '/' && p.path === location.pathname)
  if (page && !canSee(page, can)) {
    return (
      <Result status="403" title={t('errors.forbidden')} subTitle={t('errors.forbiddenHint')}
        extra={<Link to="/"><Button type="primary">{t('errors.goHome')}</Button></Link>} />
    )
  }
  return <>{children}</>
}
