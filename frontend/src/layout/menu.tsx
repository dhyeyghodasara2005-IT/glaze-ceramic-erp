import type { ReactNode } from 'react'
import {
  ApartmentOutlined,
  AppstoreOutlined,
  AuditOutlined,
  GoldOutlined,
  HddOutlined,
  SketchOutlined,
  TableOutlined,
  BankOutlined,
  FireOutlined,
  FormatPainterOutlined,
  NodeExpandOutlined,
  BuildOutlined,
  ContainerOutlined,
  OrderedListOutlined,
  TagOutlined,
  ClockCircleOutlined,
  CloudOutlined,
  ControlOutlined,
  ExperimentOutlined,
  SyncOutlined,
  CarOutlined,
  ShopOutlined,
  ShoppingCartOutlined,
  BgColorsOutlined,
  BorderOutlined,
  PictureOutlined,
  TagsOutlined,
  TrophyOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  DiffOutlined,
  ExportOutlined,
  HomeOutlined,
  ImportOutlined,
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
  /** Supervisors with one of these work on this page: their phone bar shows it before the others. */
  worksIn?: Permission[]
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
        searchType: 'finish', create: { labelKey: 'finishing.new', permission: 'MASTER_MANAGE' },
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
    labelKey: 'menu.parties',
    items: [
      {
        path: '/parties/dealers', labelKey: 'menu.dealers', icon: <ShopOutlined />, permission: 'PARTY_VIEW',
        searchType: 'dealer', create: { labelKey: 'party.new.DEALER', permission: 'PARTY_MANAGE' },
      },
      {
        path: '/parties/suppliers', labelKey: 'menu.suppliers', icon: <ShoppingCartOutlined />, permission: 'PARTY_VIEW',
        searchType: 'supplier', create: { labelKey: 'party.new.SUPPLIER', permission: 'PARTY_MANAGE' },
      },
      {
        path: '/parties/transporters', labelKey: 'menu.transporters', icon: <CarOutlined />, permission: 'PARTY_VIEW',
        searchType: 'transporter', create: { labelKey: 'party.new.TRANSPORTER', permission: 'PARTY_MANAGE' },
      },
    ],
  },
  {
    labelKey: 'menu.store',
    items: [
      { path: '/store/stock', labelKey: 'menu.stock', icon: <DatabaseOutlined />, permission: 'STORE_VIEW', mobile: true },
      {
        path: '/store/grn', labelKey: 'menu.grn', icon: <ImportOutlined />, permission: 'STORE_VIEW',
        searchType: 'grn', create: { labelKey: 'store.newDoc.GRN', permission: 'STORE_ENTRY' },
      },
      {
        path: '/store/issues', labelKey: 'menu.issues', icon: <ExportOutlined />, permission: 'STORE_VIEW',
        searchType: 'issue', create: { labelKey: 'store.newDoc.ISSUE', permission: 'STORE_ENTRY' },
      },
      {
        path: '/store/adjustments', labelKey: 'menu.adjustments', icon: <DiffOutlined />, permission: 'STORE_VIEW',
        searchType: 'adjustment', create: { labelKey: 'store.newDoc.ADJUSTMENT', permission: 'STORE_ENTRY' },
      },
      {
        path: '/store/godowns', labelKey: 'menu.godowns', icon: <HomeOutlined />, permission: 'STORE_VIEW',
        create: { labelKey: 'store.newGodown', permission: 'STORE_ENTRY' },
      },
    ],
  },
  {
    // The supervisors' home screens, in process order.
    labelKey: 'menu.production',
    items: [
      {
        path: '/production/body', labelKey: 'menu.bodyPrep', icon: <ControlOutlined />, permission: 'PRODUCTION_VIEW', mobile: true,
        worksIn: ['BODY_PREP_ENTRY'],
      },
      { path: '/production/silos', labelKey: 'menu.silos', icon: <ContainerOutlined />, permission: 'PRODUCTION_VIEW' },
      {
        path: '/production/press', labelKey: 'menu.press', icon: <BuildOutlined />, permission: 'PRODUCTION_VIEW', mobile: true,
        worksIn: ['PRESS_ENTRY'],
      },
      {
        path: '/production/line', labelKey: 'menu.line', icon: <NodeExpandOutlined />, permission: 'PRODUCTION_VIEW', mobile: true,
        worksIn: ['GLAZE_ENTRY', 'KILN_ENTRY'],
      },
      {
        path: '/production/finishing', labelKey: 'menu.finishing', icon: <AppstoreOutlined />, permission: 'PRODUCTION_VIEW', mobile: true,
        worksIn: ['QUALITY_CHECK', 'POLISH_ENTRY', 'PACKING_ENTRY'],
      },
      {
        path: '/production/lots', labelKey: 'menu.lots', icon: <TagOutlined />, permission: 'PRODUCTION_VIEW',
        searchType: 'lot', create: { labelKey: 'press.startDesign', permission: 'PRESS_ENTRY' },
      },
    ],
  },
  {
    // Every production document, for looking things up.
    labelKey: 'menu.productionRecords',
    items: [
      {
        path: '/production/body-batches', labelKey: 'menu.bodyBatches', icon: <SyncOutlined />, permission: 'PRODUCTION_VIEW',
        searchType: 'body-batch', create: { labelKey: 'prod.newBatch', permission: 'BODY_PREP_ENTRY' },
      },
      {
        path: '/production/spray-runs', labelKey: 'menu.sprayRuns', icon: <CloudOutlined />, permission: 'PRODUCTION_VIEW',
        searchType: 'spray-run', create: { labelKey: 'prod.newRunFull', permission: 'BODY_PREP_ENTRY' },
      },
      {
        path: '/production/press-entries', labelKey: 'menu.pressEntries', icon: <OrderedListOutlined />, permission: 'PRODUCTION_VIEW',
        searchType: 'press-entry', create: { labelKey: 'press.newEntry', permission: 'PRESS_ENTRY' },
      },
      {
        path: '/production/glaze-entries', labelKey: 'menu.glazeEntries', icon: <FormatPainterOutlined />, permission: 'PRODUCTION_VIEW',
        searchType: 'glaze-entry', create: { labelKey: 'line.newEntry.GLAZE', permission: 'GLAZE_ENTRY' },
      },
      {
        path: '/production/kiln-entries', labelKey: 'menu.kilnEntries', icon: <FireOutlined />, permission: 'PRODUCTION_VIEW',
        searchType: 'kiln-entry', create: { labelKey: 'line.newEntry.KILN', permission: 'KILN_ENTRY' },
      },
      {
        path: '/production/quality-checks', labelKey: 'menu.qualityChecks', icon: <AuditOutlined />, permission: 'PRODUCTION_VIEW',
        searchType: 'quality-check', create: { labelKey: 'finishing.newCheck', permission: 'QUALITY_CHECK' },
      },
      {
        path: '/production/polish-entries', labelKey: 'menu.polishEntries', icon: <SketchOutlined />, permission: 'PRODUCTION_VIEW',
        searchType: 'polish-entry', create: { labelKey: 'line.newEntry.POLISH', permission: 'POLISH_ENTRY' },
      },
      {
        path: '/production/pack-entries', labelKey: 'menu.packEntries', icon: <GoldOutlined />, permission: 'PRODUCTION_VIEW',
        searchType: 'pack-entry', create: { labelKey: 'finishing.newPack', permission: 'PACKING_ENTRY' },
      },
    ],
  },
  {
    // Step 5: boxes ready to sell, and where they stand.
    labelKey: 'menu.finishedGoods',
    items: [
      {
        path: '/fg/stock', labelKey: 'menu.fgStock', icon: <HddOutlined />, permission: 'FG_VIEW', mobile: true,
        worksIn: ['FG_ENTRY'],
      },
      { path: '/fg/godowns', labelKey: 'menu.fgGodowns', icon: <TableOutlined />, permission: 'FG_VIEW' },
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
        path: '/masters/body-recipes', labelKey: 'menu.bodyRecipes', icon: <ExperimentOutlined />, permission: 'MASTER_VIEW',
        create: { labelKey: 'recipe.new', permission: 'MASTER_MANAGE' },
      },
      {
        path: '/masters/shifts', labelKey: 'menu.shifts', icon: <ClockCircleOutlined />, permission: 'MASTER_VIEW',
        create: { labelKey: 'shift.new', permission: 'MASTER_MANAGE' },
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
