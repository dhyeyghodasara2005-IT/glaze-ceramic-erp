import type { ReactNode } from 'react'
import { Col, Row } from 'antd'
import {
  ApartmentOutlined,
  ArrowRightOutlined,
  BankOutlined,
  RocketOutlined,
  SafetyCertificateOutlined,
  ShopOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { Link } from 'react-router-dom'
import { motion } from 'motion/react'
import { useTranslation } from 'react-i18next'
import dayjs from 'dayjs'
import { useAuth } from '../auth/AuthContext'
import type { Permission } from '../api/types'
import CountUp from '../components/CountUp'
import { EASE, FadeIn, Stagger, StaggerItem } from '../components/motion'
import { useCompanies } from '../features/companies/api'
import { usePlants } from '../features/plants/api'
import { useRoles } from '../features/roles/api'
import { useUsers } from '../features/users/api'

// Tiles floating in the greeting banner.
const HERO_TILES = ['', 'warm', '', 'cream', 'cream', '', 'warm', '', '', 'warm', 'cream', '']

function HeroMosaic() {
  return (
    <div className="hero-mosaic" aria-hidden>
      {HERO_TILES.map((kind, i) => (
        <motion.div
          key={i}
          className={`hero-tile ${kind}`}
          initial={{ opacity: 0, scale: 0.5 }}
          animate={{ opacity: 1, scale: 1, y: [0, -6, 0] }}
          transition={{
            opacity: { duration: 0.5, delay: 0.2 + i * 0.04 },
            scale: { duration: 0.6, ease: EASE, delay: 0.2 + i * 0.04 },
            y: { duration: 4 + (i % 3), repeat: Infinity, ease: 'easeInOut', delay: i * 0.25 },
          }}
        />
      ))}
    </div>
  )
}

interface Stat {
  to: string
  icon: ReactNode
  label: string
  value?: number
  tint: [string, string] // [background, icon colour]
  permission: Permission
}

const SETUP: { to: string; icon: ReactNode; titleKey: string; hintKey: string; permission: Permission }[] = [
  { to: '/companies', icon: <BankOutlined />, titleKey: 'menu.companies', hintKey: 'dashboard.setupCompanies', permission: 'COMPANY_VIEW' },
  { to: '/plants', icon: <ApartmentOutlined />, titleKey: 'menu.plants', hintKey: 'dashboard.setupPlants', permission: 'PLANT_VIEW' },
  { to: '/roles', icon: <SafetyCertificateOutlined />, titleKey: 'menu.roles', hintKey: 'dashboard.setupRoles', permission: 'ROLE_VIEW' },
  { to: '/users', icon: <TeamOutlined />, titleKey: 'menu.users', hintKey: 'dashboard.setupUsers', permission: 'USER_VIEW' },
]

/** Home page. Becomes the owner's live production/dispatch dashboard in a later step. */
export default function DashboardPage() {
  const { t } = useTranslation()
  const { user, can, currentPlant } = useAuth()

  const companies = useCompanies(can('COMPANY_VIEW'))
  const plants = usePlants(undefined, can('PLANT_VIEW'))
  const roles = useRoles(can('ROLE_VIEW'))
  const users = useUsers({ page: 0, size: 1, active: true }, can('USER_VIEW'))

  const hour = new Date().getHours()
  const greeting = t(hour < 12 ? 'dashboard.morning' : hour < 17 ? 'dashboard.afternoon' : 'dashboard.evening')
  const firstName = user?.fullName.split(' ')[0] ?? ''

  const stats: Stat[] = (
    [
      { to: '/companies', icon: <BankOutlined />, label: t('menu.companies'), value: companies.data?.length, tint: ['#FBF3EE', '#A84A23'], permission: 'COMPANY_VIEW' },
      { to: '/plants', icon: <ApartmentOutlined />, label: t('menu.plants'), value: plants.data?.length, tint: ['#E9F3F3', '#285A5B'], permission: 'PLANT_VIEW' },
      { to: '/roles', icon: <SafetyCertificateOutlined />, label: t('menu.roles'), value: roles.data?.length, tint: ['#F1EEF8', '#4B3D73'], permission: 'ROLE_VIEW' },
      { to: '/users', icon: <TeamOutlined />, label: t('menu.users'), value: users.data?.totalElements, tint: ['#EDF5EE', '#33573A'], permission: 'USER_VIEW' },
    ] satisfies Stat[]
  ).filter((s) => can(s.permission))
  const setup = SETUP.filter((s) => can(s.permission))

  return (
    <>
      <motion.section
        className="hero"
        initial={{ opacity: 0, y: 16, scale: 0.99 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.55, ease: EASE }}
      >
        <HeroMosaic />
        <div className="hero-eyebrow">{dayjs().format('dddd, D MMMM YYYY')}</div>
        <h1 className="hero-title">
          {greeting}, {firstName}
        </h1>
        <p className="hero-sub">{t('dashboard.intro')}</p>
        {currentPlant && (
          <span className="hero-chip">
            <ShopOutlined /> {t('dashboard.currentPlant')}: {currentPlant.name}
          </span>
        )}
      </motion.section>

      {stats.length > 0 && (
        <>
          <FadeIn delay={0.1}>
            <h2 className="section-title">{t('dashboard.statsTitle')}</h2>
          </FadeIn>
          <Stagger className="stat-grid">
            {stats.map((s) => (
              <StaggerItem key={s.to}>
                <Link to={s.to} className="stat-card">
                  <span className="stat-icon" style={{ background: s.tint[0], color: s.tint[1] }}>
                    {s.icon}
                  </span>
                  <span>
                    <div className="stat-value">{s.value === undefined ? '–' : <CountUp value={s.value} />}</div>
                    <div className="stat-label">{s.label}</div>
                  </span>
                </Link>
              </StaggerItem>
            ))}
          </Stagger>
        </>
      )}

      <Row gutter={[20, 24]}>
        {setup.length > 0 && (
          <Col xs={24} xl={15}>
            <FadeIn delay={0.15}>
              <h2 className="section-title">{t('dashboard.setupTitle')}</h2>
            </FadeIn>
            <Stagger className="action-grid">
              {setup.map((c) => (
                <StaggerItem key={c.to}>
                  <Link to={c.to} className="action-card">
                    <span className="action-icon">{c.icon}</span>
                    <span style={{ minWidth: 0 }}>
                      <div className="action-title">{t(c.titleKey)}</div>
                      <div className="action-hint">{t(c.hintKey)}</div>
                    </span>
                    <ArrowRightOutlined className="action-arrow" />
                  </Link>
                </StaggerItem>
              ))}
            </Stagger>
          </Col>
        )}
        <Col xs={24} xl={setup.length > 0 ? 9 : 24}>
          <FadeIn delay={0.22}>
            <h2 className="section-title">
              <RocketOutlined style={{ color: 'var(--brand-600)' }} /> {t('dashboard.comingTitle')}
            </h2>
            <div className="panel">
              <div className="panel-hint">{t('dashboard.roadmapHint')}</div>
              <ol className="roadmap">
                {['dashboard.coming1', 'dashboard.coming2', 'dashboard.coming3', 'dashboard.coming4'].map((key, i) => (
                  <li key={key}>
                    <span className="roadmap-step">{i + 2}</span>
                    <span>{t(key)}</span>
                  </li>
                ))}
              </ol>
            </div>
          </FadeIn>
        </Col>
      </Row>
    </>
  )
}
