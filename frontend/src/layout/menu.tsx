import type { ReactNode } from 'react'
import {
  ApartmentOutlined,
  BankOutlined,
  BgColorsOutlined,
  BorderOutlined,
  PictureOutlined,
  TagsOutlined,
  TrophyOutlined,
  DashboardOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import type { Permission } from '../api/types'

/**
 * The sidebar menu. Each item lists the permission needed to see it.
 * When you build a new module, add its pages here.
 */
export interface MenuEntry {
  path: string
  labelKey: string
  icon: ReactNode
  permission?: Permission
  /** true = also shown in the phone bottom bar (max 3 besides Home) */
  mobile?: boolean
}

export interface MenuGroup {
  labelKey: string
  items: MenuEntry[]
}

export const MENU: (MenuEntry | MenuGroup)[] = [
  { path: '/', labelKey: 'menu.dashboard', icon: <DashboardOutlined /> },
  {
    labelKey: 'menu.masters',
    items: [
      { path: '/masters/designs', labelKey: 'menu.designs', icon: <PictureOutlined />, permission: 'MASTER_VIEW' },
      { path: '/masters/sizes', labelKey: 'menu.sizes', icon: <BorderOutlined />, permission: 'MASTER_VIEW' },
      { path: '/masters/finishes', labelKey: 'menu.finishes', icon: <BgColorsOutlined />, permission: 'MASTER_VIEW' },
      { path: '/masters/categories', labelKey: 'menu.categories', icon: <TagsOutlined />, permission: 'MASTER_VIEW' },
      { path: '/masters/grades', labelKey: 'menu.grades', icon: <TrophyOutlined />, permission: 'MASTER_VIEW' },
    ],
  },
  {
    labelKey: 'menu.setup',
    items: [
      { path: '/companies', labelKey: 'menu.companies', icon: <BankOutlined />, permission: 'COMPANY_VIEW' },
      { path: '/plants', labelKey: 'menu.plants', icon: <ApartmentOutlined />, permission: 'PLANT_VIEW', mobile: true },
      { path: '/roles', labelKey: 'menu.roles', icon: <SafetyCertificateOutlined />, permission: 'ROLE_VIEW' },
      { path: '/users', labelKey: 'menu.users', icon: <TeamOutlined />, permission: 'USER_VIEW', mobile: true },
    ],
  },
]

/** Is this menu entry the current page? "/masters/sizes/5" belongs to "/masters/sizes". */
export function isActivePath(pathname: string, path: string) {
  return path === '/' ? pathname === '/' : pathname === path || pathname.startsWith(path + '/')
}

export const isGroup = (entry: MenuEntry | MenuGroup): entry is MenuGroup => 'items' in entry
