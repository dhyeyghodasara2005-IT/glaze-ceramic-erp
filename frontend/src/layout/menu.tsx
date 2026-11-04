import type { ReactNode } from 'react'
import {
  ApartmentOutlined,
  AppstoreOutlined,
  AuditOutlined,
  DownloadOutlined,
  SwapOutlined,
  WarningOutlined,
  GoldOutlined,
  SunOutlined,
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
  FileDoneOutlined,
  FieldTimeOutlined,
  FormOutlined,
  ThunderboltOutlined,
  ReconciliationOutlined,
  ScheduleOutlined,
  ExportOutlined,
  HomeOutlined,
  ImportOutlined,
  FolderOutlined,
  InboxOutlined,
  NumberOutlined,
  ToolOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
  IdcardOutlined,
  CalendarOutlined,
  SolutionOutlined,
  CalculatorOutlined,
  PercentageOutlined,
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
  /**
   * Supervisors with one of these work on this page: it is their start page (the first such page in the menu), and
   * their phone bar shows it before the others.
   */
  worksIn?: Permission[]
  /**
   * Only for people who work in one of these sections, besides having `permission`: the kiln supervisor may view
   * production, but sees the kiln's pages, not polishing's. Managers see every section (see SEES_ALL_SECTIONS).
   * An empty list = managers only.
   */
  sections?: Permission[]
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
  // The whole-plant dashboard: the owner and Accounts. Everyone else starts on their own section (homePath).
  { path: '/', labelKey: 'menu.dashboard', icon: <DashboardOutlined />, permission: 'DASHBOARD_VIEW' },
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
        path: '/parties/dealers', labelKey: 'menu.dealers', icon: <ShopOutlined />, permission: 'PARTY_VIEW', sections: ['SALES_ENTRY', 'LOADING_ENTRY'],
        searchType: 'dealer', create: { labelKey: 'party.new.DEALER', permission: 'PARTY_MANAGE' },
      },
      {
        path: '/parties/suppliers', labelKey: 'menu.suppliers', icon: <ShoppingCartOutlined />, permission: 'PARTY_VIEW', sections: ['STORE_ENTRY', 'LAB_ENTRY'],
        searchType: 'supplier', create: { labelKey: 'party.new.SUPPLIER', permission: 'PARTY_MANAGE' },
      },
      {
        path: '/parties/transporters', labelKey: 'menu.transporters', icon: <CarOutlined />, permission: 'PARTY_VIEW', sections: ['SALES_ENTRY', 'LOADING_ENTRY'],
        searchType: 'transporter', create: { labelKey: 'party.new.TRANSPORTER', permission: 'PARTY_MANAGE' },
      },
      {
        path: '/parties/principals', labelKey: 'menu.principals', icon: <ReconciliationOutlined />, permission: 'PARTY_VIEW', sections: ['SALES_ENTRY', 'LOADING_ENTRY'],
        searchType: 'jobwork', create: { labelKey: 'party.new.JOBWORK', permission: 'PARTY_MANAGE' },
      },
    ],
  },
  {
    labelKey: 'menu.store',
    items: [
      { path: '/store/stock', labelKey: 'menu.stock', icon: <DatabaseOutlined />, permission: 'STORE_VIEW', mobile: true, worksIn: ['STORE_ENTRY'] },
      // Step 10a: which truck the stock is from, and where each truck went.
      { path: '/store/lots', labelKey: 'menu.materialLots', icon: <GoldOutlined />, permission: 'STORE_VIEW' },
      {
        path: '/store/grn', labelKey: 'menu.grn', icon: <ImportOutlined />, permission: 'STORE_VIEW',
        searchType: 'grn', create: { labelKey: 'store.newDoc.GRN', permission: 'STORE_ENTRY' },
      },
      {
        // Step 8d: raw material tested when it arrives; the GRN waits for it.
        path: '/lab/tests', labelKey: 'menu.labTests', icon: <ExperimentOutlined />, permission: 'LAB_VIEW', mobile: true,
        worksIn: ['LAB_ENTRY'], searchType: 'lab-test',
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
        path: '/production/body', labelKey: 'menu.bodyPrep', icon: <ControlOutlined />, permission: 'PRODUCTION_VIEW', sections: ['BODY_PREP_ENTRY'], mobile: true,
        worksIn: ['BODY_PREP_ENTRY'],
      },
      { path: '/production/silos', labelKey: 'menu.silos', icon: <ContainerOutlined />, permission: 'PRODUCTION_VIEW', sections: ['BODY_PREP_ENTRY', 'PRESS_ENTRY'] },
      {
        path: '/production/press', labelKey: 'menu.press', icon: <BuildOutlined />, permission: 'PRODUCTION_VIEW', sections: ['PRESS_ENTRY'], mobile: true,
        worksIn: ['PRESS_ENTRY'],
      },
      {
        path: '/production/line', labelKey: 'menu.line', icon: <NodeExpandOutlined />, permission: 'PRODUCTION_VIEW', sections: ['GLAZE_ENTRY', 'KILN_ENTRY'], mobile: true,
        worksIn: ['GLAZE_ENTRY', 'KILN_ENTRY'],
      },
      {
        path: '/production/finishing', labelKey: 'menu.finishing', icon: <AppstoreOutlined />, permission: 'PRODUCTION_VIEW', sections: ['QUALITY_CHECK', 'POLISH_ENTRY', 'PACKING_ENTRY'], mobile: true,
        worksIn: ['QUALITY_CHECK', 'POLISH_ENTRY', 'PACKING_ENTRY'],
      },
      {
        path: '/production/lots', labelKey: 'menu.lots', icon: <TagOutlined />, permission: 'PRODUCTION_VIEW', sections: ['GLAZE_ENTRY', 'KILN_ENTRY', 'QUALITY_CHECK', 'POLISH_ENTRY', 'PACKING_ENTRY'],
        searchType: 'lot', create: { labelKey: 'press.startDesign', permission: 'GLAZE_ENTRY' },
      },
      // Step 10c: the weather of each shift, kept beside production for later analysis.
      { path: '/production/weather', labelKey: 'menu.weather', icon: <SunOutlined />, permission: 'PRODUCTION_VIEW', sections: [] },
    ],
  },
  {
    // Every production document, for looking things up.
    labelKey: 'menu.productionRecords',
    items: [
      {
        path: '/production/body-batches', labelKey: 'menu.bodyBatches', icon: <SyncOutlined />, permission: 'PRODUCTION_VIEW', sections: ['BODY_PREP_ENTRY'],
        searchType: 'body-batch', create: { labelKey: 'prod.newBatch', permission: 'BODY_PREP_ENTRY' },
      },
      {
        path: '/production/spray-runs', labelKey: 'menu.sprayRuns', icon: <CloudOutlined />, permission: 'PRODUCTION_VIEW', sections: ['BODY_PREP_ENTRY'],
        searchType: 'spray-run', create: { labelKey: 'prod.newRunFull', permission: 'BODY_PREP_ENTRY' },
      },
      {
        path: '/production/press-entries', labelKey: 'menu.pressEntries', icon: <OrderedListOutlined />, permission: 'PRODUCTION_VIEW', sections: ['PRESS_ENTRY'],
        searchType: 'press-entry', create: { labelKey: 'press.newEntry', permission: 'PRESS_ENTRY' },
      },
      {
        path: '/production/glaze-entries', labelKey: 'menu.glazeEntries', icon: <FormatPainterOutlined />, permission: 'PRODUCTION_VIEW', sections: ['GLAZE_ENTRY'],
        searchType: 'glaze-entry', create: { labelKey: 'line.newEntry.GLAZE', permission: 'GLAZE_ENTRY' },
      },
      {
        path: '/production/kiln-entries', labelKey: 'menu.kilnEntries', icon: <FireOutlined />, permission: 'PRODUCTION_VIEW', sections: ['KILN_ENTRY'],
        searchType: 'kiln-entry', create: { labelKey: 'line.newEntry.KILN', permission: 'KILN_ENTRY' },
      },
      {
        path: '/production/quality-checks', labelKey: 'menu.qualityChecks', icon: <AuditOutlined />, permission: 'PRODUCTION_VIEW', sections: ['QUALITY_CHECK'],
        searchType: 'quality-check', create: { labelKey: 'finishing.newCheck', permission: 'QUALITY_CHECK' },
      },
      {
        path: '/production/polish-entries', labelKey: 'menu.polishEntries', icon: <SketchOutlined />, permission: 'PRODUCTION_VIEW', sections: ['POLISH_ENTRY'],
        searchType: 'polish-entry', create: { labelKey: 'line.newEntry.POLISH', permission: 'POLISH_ENTRY' },
      },
      {
        path: '/production/pack-entries', labelKey: 'menu.packEntries', icon: <GoldOutlined />, permission: 'PRODUCTION_VIEW', sections: ['PACKING_ENTRY'],
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
      { path: '/fg/godowns', labelKey: 'menu.fgGodowns', icon: <TableOutlined />, permission: 'FG_VIEW', sections: ['FG_ENTRY', 'PACKING_ENTRY', 'LOADING_ENTRY'] },
      {
        path: '/fg/opening', labelKey: 'menu.fgOpening', icon: <DownloadOutlined />, permission: 'FG_VIEW', sections: ['FG_ENTRY'],
        searchType: 'fg-opening', create: { labelKey: 'fg.doc.new.OPENING', permission: 'FG_ENTRY' },
      },
      {
        path: '/fg/breakage', labelKey: 'menu.fgBreakage', icon: <WarningOutlined />, permission: 'FG_VIEW', sections: ['FG_ENTRY'],
        searchType: 'fg-breakage', create: { labelKey: 'fg.doc.new.BREAKAGE', permission: 'FG_ENTRY' },
      },
      {
        path: '/fg/regrading', labelKey: 'menu.fgRegrading', icon: <SwapOutlined />, permission: 'FG_VIEW', sections: ['FG_ENTRY'],
        searchType: 'fg-regrade', create: { labelKey: 'fg.doc.new.REGRADE', permission: 'FG_ENTRY' },
      },
    ],
  },
  {
    // Step 6: loading slips from the sales system, and (6c) the loading itself.
    labelKey: 'menu.sales',
    items: [
      {
        path: '/sales/orders', labelKey: 'menu.salesOrders', icon: <FileDoneOutlined />, permission: 'SALES_VIEW', sections: ['SALES_ENTRY'],
        worksIn: ['SALES_ENTRY'], searchType: 'sales-order', create: { labelKey: 'sales.new', permission: 'SALES_ENTRY' },
      },
      {
        path: '/sales/loading', labelKey: 'menu.loading', icon: <CarOutlined />, permission: 'SALES_VIEW', mobile: true,
        worksIn: ['LOADING_ENTRY'],
      },
    ],
  },
  {
    // Step 8: machines that stop, and what it costs.
    labelKey: 'menu.maintenance',
    items: [
      {
        path: '/maintenance/breakdowns', labelKey: 'menu.breakdowns', icon: <ToolOutlined />, permission: 'MAINTENANCE_VIEW', mobile: true,
        worksIn: ['MAINTENANCE_ENTRY'], searchType: 'breakdown', create: { labelKey: 'maint.report', permission: 'BREAKDOWN_REPORT' },
      },
      {
        path: '/maintenance/preventive', labelKey: 'menu.preventive', icon: <ScheduleOutlined />, permission: 'MAINTENANCE_VIEW', sections: ['MAINTENANCE_ENTRY'],
        worksIn: ['MAINTENANCE_ENTRY'], searchType: 'pm-job', create: { labelKey: 'pm.new', permission: 'MAINTENANCE_ENTRY' },
      },
      { path: '/maintenance/downtime', labelKey: 'menu.downtime', icon: <FieldTimeOutlined />, permission: 'MAINTENANCE_VIEW', sections: ['MAINTENANCE_ENTRY'] },
    ],
  },
  {
    // Step 8c: what the plant burns.
    labelKey: 'menu.energy',
    items: [
      { path: '/energy/readings', labelKey: 'menu.meterReadings', icon: <FormOutlined />, permission: 'METER_ENTRY', mobile: true },
      { path: '/energy/use', labelKey: 'menu.energyUse', icon: <ThunderboltOutlined />, permission: 'ENERGY_VIEW' },
    ],
  },
  {
    // Step 9a: who worked, through which contractor, and what the month comes to.
    labelKey: 'menu.labour',
    items: [
      { path: '/labour/attendance', labelKey: 'menu.attendance', icon: <ScheduleOutlined />, permission: 'ATTENDANCE_ENTRY', mobile: true, worksIn: ['ATTENDANCE_ENTRY'] },
      {
        path: '/labour/workers', labelKey: 'menu.workers', icon: <IdcardOutlined />, permission: 'LABOUR_VIEW',
        searchType: 'worker', create: { labelKey: 'labour.newWorker', permission: 'LABOUR_MANAGE' },
      },
      { path: '/labour/month', labelKey: 'menu.labourMonth', icon: <CalendarOutlined />, permission: 'LABOUR_VIEW' },
      {
        path: '/parties/contractors', labelKey: 'menu.contractors', icon: <SolutionOutlined />, permission: 'PARTY_VIEW', sections: ['LABOUR_MANAGE'],
        searchType: 'contractor', create: { labelKey: 'party.new.CONTRACTOR', permission: 'PARTY_MANAGE' },
      },
    ],
  },
  {
    // Step 9b: what a m² costs.
    labelKey: 'menu.costing',
    items: [
      { path: '/costing/sheet', labelKey: 'menu.costSheet', icon: <CalculatorOutlined />, permission: 'COST_VIEW' },
      { path: '/costing/designs', labelKey: 'menu.costDesigns', icon: <PictureOutlined />, permission: 'COST_VIEW' },
      {
        path: '/costing/rates', labelKey: 'menu.costRates', icon: <PercentageOutlined />, permission: 'COST_VIEW',
        create: { labelKey: 'costing.newRate', permission: 'COST_MANAGE' },
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
        path: '/masters/meters', labelKey: 'menu.meters', icon: <DashboardOutlined />, permission: 'MASTER_VIEW',
        create: { labelKey: 'energy.newMeter', permission: 'MASTER_MANAGE' },
      },
      { path: '/masters/lab-tests', labelKey: 'menu.labSetup', icon: <ExperimentOutlined />, permission: 'MASTER_VIEW' },
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

/**
 * Managers see every section's pages. Cancelling any posted production entry is the production manager's (and the
 * owner's) job, so that permission marks them.
 */
export const SEES_ALL_SECTIONS: Permission = 'PRODUCTION_CANCEL'

/** May this person see this page? Its permission, and (for a section page) a job in that section. */
export function canSee(entry: MenuEntry, can: (p: Permission) => boolean) {
  if (entry.permission && !can(entry.permission)) return false
  return !entry.sections || entry.sections.some(can) || can(SEES_ALL_SECTIONS)
}

/** All pages as one flat list (no groups). */
export const ALL_PAGES: MenuEntry[] = MENU.flatMap((e) => (isGroup(e) ? e.items : [e]))

/**
 * Where someone starts after logging in: the dashboard for the owner and Accounts; otherwise the first page of their
 * own section (`worksIn`), else the first page they may see. Null = nothing to show (a user with no role).
 */
export function homePath(can: (p: Permission) => boolean): string | null {
  if (can('DASHBOARD_VIEW')) return '/'
  const pages = ALL_PAGES.filter((p) => p.path !== '/' && canSee(p, can))
  return (pages.find((p) => p.worksIn?.some(can)) ?? pages[0])?.path ?? null
}

/** The page where search results of a given type open, e.g. "design" -> /masters/designs. */
export const pageForType = (type: string) => ALL_PAGES.find((p) => p.searchType === type)
