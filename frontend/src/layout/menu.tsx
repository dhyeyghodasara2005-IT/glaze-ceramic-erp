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
  FolderOutlined,
  InboxOutlined,
  NumberOutlined,
  ToolOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import type { Permission } from '../api/types'

/**
 * Every page of the app, in one list. The sidebar, the phone bottom bar AND the search palette
 * are all built from it. When you build a new module, add its pages here.
 */
export interface MenuEntry {
  path: string
  labelKey: string
  icon: ReactNode
  permission?: Permission
  /** true = also shown in the phone bottom bar (max 3 besides Home) */
  mobile?: boolean
  /** Shows "New …" in the search palette; opens `${path}?new=1`. */
  create?: { labelKey: string; permission: Permission }
  /** Search results of this type (from GET /api/search) open on this page with `?open=<id>`. */
  searchType?: string
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
      {
        path: '/masters/designs', labelKey: 'menu.designs', icon: <PictureOutlined />, permission: 'MASTER_VIEW',
        searchType: 'design', create: { labelKey: 'design.new', permission: 'MASTER_MANAGE' },
      },
      {
        path: '/masters/sizes', labelKey: 'menu.sizes', icon: <BorderOutlined />, permission: 'MASTER_VIEW',
        searchType: 'size', create: { labelKey: 'size.new', permission: 'MASTER_MANAGE' },
      },
      {
        path: '/masters/finishes', labelKey: 'menu.finishes', icon: <BgColorsOutlined />, permission: 'MASTER_VIEW',
        searchType: 'finish', create: { labelKey: 'finish.new', permission: 'MASTER_MANAGE' },
      },
      {
        path: '/masters/categories', labelKey: 'menu.categories', icon: <TagsOutlined />, permission: 'MASTER_VIEW',
        searchType: 'category', create: { labelKey: 'category.new', permission: 'MASTER_MANAGE' },
      },
      {
        path: '/masters/grades', labelKey: 'menu.grades', icon: <TrophyOutlined />, permission: 'MASTER_VIEW',
        searchType: 'grade', create: { labelKey: 'grade.new', permission: 'MASTER_MANAGE' },
      },
    ],
  },
  {
    labelKey: 'menu.plantMasters',
    items: [
      {
        path: '/masters/materials', labelKey: 'menu.materials', icon: <InboxOutlined />, permission: 'MASTER_VIEW',
        searchType: 'material', create: { labelKey: 'material.new', permission: 'MASTER_MANAGE' },
      },
      {
        path: '/masters/machines', labelKey: 'menu.machines', icon: <ToolOutlined />, permission: 'MASTER_VIEW',
        searchType: 'machine', create: { labelKey: 'machine.new', permission: 'MASTER_MANAGE' },
      },
      {
        path: '/masters/material-groups', labelKey: 'menu.materialGroups', icon: <FolderOutlined />, permission: 'MASTER_VIEW',
        create: { labelKey: 'materialGroup.new', permission: 'MASTER_MANAGE' },
      },
      {
        path: '/masters/units', labelKey: 'menu.units', icon: <NumberOutlined />, permission: 'MASTER_VIEW',
        create: { labelKey: 'unit.new', permission: 'MASTER_MANAGE' },
      },
    ],
  },
  {
    labelKey: 'menu.setup',
    items: [
      {
        path: '/companies', labelKey: 'menu.companies', icon: <BankOutlined />, permission: 'COMPANY_VIEW',
        searchType: 'company', create: { labelKey: 'company.new', permission: 'COMPANY_MANAGE' },
      },
      {
        path: '/plants', labelKey: 'menu.plants', icon: <ApartmentOutlined />, permission: 'PLANT_VIEW', mobile: true,
        searchType: 'plant', create: { labelKey: 'plant.new', permission: 'PLANT_MANAGE' },
      },
      {
        path: '/roles', labelKey: 'menu.roles', icon: <SafetyCertificateOutlined />, permission: 'ROLE_VIEW',
        searchType: 'role', create: { labelKey: 'role.new', permission: 'ROLE_MANAGE' },
      },
      {
        path: '/users', labelKey: 'menu.users', icon: <TeamOutlined />, permission: 'USER_VIEW', mobile: true,
        searchType: 'user', create: { labelKey: 'user.new', permission: 'USER_MANAGE' },
      },
    ],
  },
]

/** Is this menu entry the current page? "/masters/sizes/5" belongs to "/masters/sizes". */
export function isActivePath(pathname: string, path: string) {
  return path === '/' ? pathname === '/' : pathname === path || pathname.startsWith(path + '/')
}

export const isGroup = (entry: MenuEntry | MenuGroup): entry is MenuGroup => 'items' in entry

/** All pages as one flat list (no groups). */
export const ALL_PAGES: MenuEntry[] = MENU.flatMap((e) => (isGroup(e) ? e.items : [e]))

/** The page where search results of a given type open, e.g. "design" -> /masters/designs. */
export const pageForType = (type: string) => ALL_PAGES.find((p) => p.searchType === type)
