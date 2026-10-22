// TypeScript copies of the backend DTOs (the Java records in */dto/).
// When you change a Java record, change the matching type here.

export interface ApiError {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  fieldErrors?: Record<string, string>
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// ---- auth ----

export type Language = 'en' | 'gu' | 'hi'

export interface PlantRef {
  id: number
  code: string
  name: string
}

export interface RoleRef {
  id: number
  name: string
}

export interface CurrentUser {
  id: number
  username: string
  fullName: string
  preferredLanguage: Language
  mustChangePassword: boolean
  allPlantsAccess: boolean
  roles: string[]
  permissions: Permission[]
  plants: PlantRef[]
}

export interface LoginResponse {
  token: string
  expiresAt: string
  user: CurrentUser
}

// ---- permissions (must match Permission.java) ----

export type Permission =
  | 'COMPANY_VIEW'
  | 'COMPANY_MANAGE'
  | 'PLANT_VIEW'
  | 'PLANT_MANAGE'
  | 'USER_VIEW'
  | 'USER_MANAGE'
  | 'ROLE_VIEW'
  | 'ROLE_MANAGE'
  | 'MASTER_VIEW'
  | 'MASTER_MANAGE'
  | 'PARTY_VIEW'
  | 'PARTY_MANAGE'
  | 'STORE_VIEW'
  | 'STORE_ENTRY'
  | 'STORE_CANCEL'
  | 'PRODUCTION_VIEW'
  | 'BODY_PREP_ENTRY'
  | 'PRODUCTION_CANCEL'

export interface PermissionInfo {
  code: Permission
  group: string
  description: string
}

// ---- organization ----

export interface Company {
  id: number
  code: string
  name: string
  legalName?: string
  gstin?: string
  pan?: string
  addressLine?: string
  city?: string
  state?: string
  pincode?: string
  phone?: string
  email?: string
  active: boolean
  version: number
}

export type CompanyRequest = Omit<Company, 'id' | 'version'>

export interface Plant {
  id: number
  companyId: number
  companyCode: string
  companyName: string
  code: string
  name: string
  gstin?: string
  addressLine?: string
  city?: string
  state?: string
  pincode?: string
  capacitySqmPerDay?: number
  active: boolean
  version: number
}

export type PlantRequest = Omit<Plant, 'id' | 'version' | 'companyCode' | 'companyName'>

// ---- roles & users ----

export interface Role {
  id: number
  name: string
  description?: string
  systemRole: boolean
  permissions: Permission[]
  userCount: number
  version: number
}

export interface RoleRequest {
  name: string
  description?: string
  permissions: Permission[]
}

export interface User {
  id: number
  username: string
  fullName: string
  phone?: string
  email?: string
  active: boolean
  mustChangePassword: boolean
  allPlantsAccess: boolean
  preferredLanguage: Language
  lastLoginAt?: string
  roles: RoleRef[]
  plants: PlantRef[]
  version: number
}

export interface UserUpdateRequest {
  fullName: string
  phone?: string
  email?: string
  roleIds: number[]
  plantIds: number[]
  allPlantsAccess: boolean
  preferredLanguage: Language
}

export interface UserCreateRequest extends UserUpdateRequest {
  username: string
  password: string
}

// ---- product masters (must match master/dto/*.java) ----

export interface TileSize {
  id: number
  code: string
  name: string
  lengthMm: number
  widthMm: number
  thicknessMm: number
  pcsPerBox: number
  /** calculated by the server: length x width x pcs */
  sqmPerBox: number
  boxWeightKg?: number
  boxesPerPallet?: number
  active: boolean
  version: number
}

export type TileSizeRequest = Omit<TileSize, 'id' | 'version' | 'sqmPerBox'>

export interface Finish {
  id: number
  code: string
  name: string
  active: boolean
  version: number
}

export type FinishRequest = Omit<Finish, 'id' | 'version'>

export interface ProductCategory {
  id: number
  code: string
  name: string
  hsnCode?: string
  gstRate: number
  active: boolean
  version: number
}

export type ProductCategoryRequest = Omit<ProductCategory, 'id' | 'version'>

export interface Grade {
  id: number
  code: string
  name: string
  sortOrder: number
  sellable: boolean
  active: boolean
  version: number
}

export type GradeRequest = Omit<Grade, 'id' | 'version'>

export interface Design {
  id: number
  code: string
  name: string
  categoryId: number
  categoryCode: string
  categoryName: string
  sizeId: number
  sizeCode: string
  sizeName: string
  sqmPerBox: number
  finishId: number
  finishCode: string
  finishName: string
  faces: number
  /** "2026-10-22" */
  launchDate?: string
  description?: string
  active: boolean
  version: number
}

export interface DesignRequest {
  code: string
  name: string
  categoryId: number
  sizeId: number
  finishId: number
  faces: number
  launchDate?: string
  description?: string
  active?: boolean
}

// ---- store & plant masters ----

export interface UnitOfMeasure {
  id: number
  code: string
  name: string
  /** decimal places allowed: BOX 0, KG 3 */
  decimals: number
  active: boolean
  version: number
}

export type UnitOfMeasureRequest = Omit<UnitOfMeasure, 'id' | 'version'>

export interface MaterialGroup {
  id: number
  code: string
  name: string
  active: boolean
  version: number
}

export type MaterialGroupRequest = Omit<MaterialGroup, 'id' | 'version'>

export interface Material {
  id: number
  code: string
  name: string
  groupId: number
  groupCode: string
  groupName: string
  unitId: number
  unitCode: string
  unitDecimals: number
  hsnCode?: string
  gstRate?: number
  reorderLevel?: number
  description?: string
  active: boolean
  version: number
}

export interface MaterialRequest {
  code: string
  name: string
  groupId: number
  unitId: number
  hsnCode?: string
  gstRate?: number
  reorderLevel?: number
  description?: string
  active?: boolean
}

/** Must match MachineType.java (process order). */
export const MACHINE_TYPES = [
  'BALL_MILL',
  'SLIP_TANK',
  'SPRAY_DRYER',
  'SILO',
  'PRESS',
  'DRYER',
  'GLAZE_LINE',
  'DIGITAL_PRINTER',
  'KILN',
  'POLISHING_LINE',
  'SORTING_LINE',
  'PACKING_LINE',
  'UTILITY',
  'OTHER',
] as const
export type MachineType = (typeof MACHINE_TYPES)[number]

export interface Machine {
  id: number
  plantId: number
  plantCode: string
  plantName: string
  code: string
  name: string
  type: MachineType
  capacity?: number
  capacityUnit?: string
  make?: string
  model?: string
  /** "2026-10-22" */
  installedOn?: string
  active: boolean
  version: number
}

export interface MachineRequest {
  plantId: number
  code: string
  name: string
  type: MachineType
  capacity?: number
  capacityUnit?: string
  make?: string
  model?: string
  installedOn?: string
  active?: boolean
}

// ---- parties (must match party/*.java) ----

export type PartyType = 'DEALER' | 'SUPPLIER' | 'TRANSPORTER'

export const GST_REGISTRATIONS = ['REGULAR', 'COMPOSITION', 'UNREGISTERED', 'CONSUMER', 'OVERSEAS'] as const
export type GstRegistration = (typeof GST_REGISTRATIONS)[number]

export interface Party {
  id: number
  partyType: PartyType
  code: string
  name: string
  contactPerson?: string
  gstRegistration: GstRegistration
  gstin?: string
  pan?: string
  addressLine?: string
  city?: string
  state?: string
  stateCode?: string
  pincode?: string
  country: string
  phone?: string
  mobile?: string
  email?: string
  region?: string
  creditLimit?: number
  creditDays?: number
  bankName?: string
  bankAccountNo?: string
  bankIfsc?: string
  notes?: string
  active: boolean
  version: number
}

export type PartyRequest = Omit<Party, 'id' | 'version' | 'state' | 'code' | 'country' | 'active'> & {
  /** empty = next automatic number */
  code?: string
  country?: string
  active?: boolean
}

// ---- store (step 3) ----

export type StoreDocType = 'GRN' | 'ISSUE' | 'ADJUSTMENT'
export type DocStatus = 'DRAFT' | 'POSTED' | 'CANCELLED'
export const ADJUSTMENT_REASONS = ['OPENING', 'PHYSICAL_COUNT', 'DAMAGE', 'OTHER'] as const
export type AdjustmentReason = (typeof ADJUSTMENT_REASONS)[number]

/** A godown (store location) of a plant. */
export interface StoreLocation {
  id: number
  plantId: number
  plantCode: string
  code: string
  name: string
  active: boolean
  version: number
}

export interface StoreLocationRequest {
  plantId: number
  code: string
  name: string
  active?: boolean
}

/** Dropdown item from /api/store/lookups. For materials: extra = unit code, decimals = its decimals. */
export interface StoreOption {
  id: number
  code: string
  name: string
  extra?: string
  decimals: number
}

export interface StoreDocumentLine {
  id: number
  lineNo: number
  materialId: number
  materialCode: string
  materialName: string
  unitCode: string
  unitDecimals: number
  locationId: number
  locationCode: string
  locationName: string
  quantity: number
  receivedQty?: number
  rejectedQty?: number
  rate?: number
  value?: number
  moisturePct?: number
  remarks?: string
}

export interface StoreDocument {
  id: number
  plantId: number
  plantCode: string
  docType: StoreDocType
  /** missing while DRAFT */
  docNo?: string
  docDate: string
  status: DocStatus
  /** set when another module made it, e.g. 'BODY_BATCH' - then it is read-only in the store */
  origin?: string
  supplierId?: number
  supplierCode?: string
  supplierName?: string
  supplierInvoiceNo?: string
  supplierInvoiceDate?: string
  vehicleNo?: string
  weighbridgeSlipNo?: string
  machineId?: number
  machineCode?: string
  machineName?: string
  issuedTo?: string
  reason?: AdjustmentReason
  remarks?: string
  totalValue: number
  postedAt?: string
  postedBy?: string
  cancelledAt?: string
  cancelledBy?: string
  cancelReason?: string
  createdAt: string
  createdBy: string
  version: number
  lines: StoreDocumentLine[]
}

/** One row of a document list (no lines). */
export interface StoreDocumentSummary {
  id: number
  docType: StoreDocType
  docNo?: string
  docDate: string
  status: DocStatus
  origin?: string
  supplierName?: string
  supplierInvoiceNo?: string
  machineName?: string
  issuedTo?: string
  reason?: AdjustmentReason
  totalValue: number
  createdBy: string
}

export interface StoreDocumentLineRequest {
  materialId: number
  locationId: number
  quantity?: number
  receivedQty?: number
  rejectedQty?: number
  rate?: number
  moisturePct?: number
  remarks?: string
}

export interface StoreDocumentRequest {
  plantId: number
  docType: StoreDocType
  docDate: string
  supplierId?: number
  supplierInvoiceNo?: string
  supplierInvoiceDate?: string
  vehicleNo?: string
  weighbridgeSlipNo?: string
  machineId?: number
  issuedTo?: string
  reason?: AdjustmentReason
  remarks?: string
  lines: StoreDocumentLineRequest[]
}

export interface LocationStock {
  locationId: number
  locationCode: string
  locationName: string
  quantity: number
  value: number
}

/** Stock of one material in a plant (GET /api/store/stock). */
export interface StockRow {
  materialId: number
  materialCode: string
  materialName: string
  groupName: string
  unitCode: string
  unitDecimals: number
  quantity: number
  value: number
  averageRate?: number
  reorderLevel?: number
  low: boolean
  active: boolean
  locations: LocationStock[]
}

/** One movement in a material's stock ledger. quantity: + in, - out. */
export interface LedgerRow {
  id: number
  txnDate: string
  docType: StoreDocType
  docNo: string
  documentId: number
  locationCode: string
  quantity: number
  rate: number
  value: number
  balanceQty: number
  reversal: boolean
}

// ---- shifts & body recipes (masters, step 4a) ----

export interface Shift {
  id: number
  code: string
  name: string
  /** "08:00:00" */
  startTime: string
  endTime: string
  crossesMidnight: boolean
  sortOrder: number
  active: boolean
  version: number
}

export interface ShiftRequest {
  code: string
  name: string
  startTime: string
  endTime: string
  sortOrder: number
  active?: boolean
}

export interface BodyRecipeLine {
  id: number
  lineNo: number
  materialId: number
  materialCode: string
  materialName: string
  unitCode: string
  unitDecimals: number
  /** in the material's own unit, per tonne of dry charge */
  qtyPerTon: number
}

export interface BodyRecipe {
  id: number
  code: string
  name: string
  description?: string
  active: boolean
  version: number
  lines: BodyRecipeLine[]
}

export interface BodyRecipeRequest {
  code: string
  name: string
  description?: string
  active?: boolean
  lines: { materialId: number; qtyPerTon: number }[]
}

// ---- production: body preparation (step 4a) ----

export type BodyBatchStatus = 'DRAFT' | 'CHARGED' | 'DISCHARGED' | 'CONSUMED' | 'CANCELLED'
export const BODY_BATCH_STATUSES: BodyBatchStatus[] = ['DRAFT', 'CHARGED', 'DISCHARGED', 'CONSUMED', 'CANCELLED']

export interface CurrentShift {
  shiftId?: number
  code?: string
  name?: string
  prodDate: string
}

export interface MachineOption {
  id: number
  code: string
  name: string
  type: MachineType
}

export interface AvailableStock {
  materialId: number
  locationId: number
  quantity: number
}

export interface BodyBatchSummary {
  id: number
  batchNo?: string
  prodDate: string
  shiftCode: string
  ballMillCode: string
  ballMillName: string
  recipeCode?: string
  chargeTon: number
  status: BodyBatchStatus
  /** plant clock time, "2026-10-23T08:30:00" */
  millingStart: string
  millingEnd?: string
  slipTankCode?: string
  slipDensity?: number
  slipViscosity?: number
  slipResidue?: number
}

export interface BodyBatchLine {
  id: number
  lineNo: number
  materialId: number
  materialCode: string
  materialName: string
  unitCode: string
  unitDecimals: number
  locationId: number
  locationCode: string
  locationName: string
  quantity: number
}

export interface BodyBatch {
  id: number
  plantId: number
  plantCode: string
  batchNo?: string
  prodDate: string
  shiftId: number
  shiftCode: string
  shiftName: string
  ballMillId: number
  ballMillCode: string
  ballMillName: string
  recipeId?: number
  recipeCode?: string
  recipeName?: string
  status: BodyBatchStatus
  chargeTon: number
  waterLitres?: number
  millingStart: string
  millingEnd?: string
  slipTankId?: number
  slipTankCode?: string
  slipTankName?: string
  slipDensity?: number
  slipViscosity?: number
  slipResidue?: number
  issueDocumentId?: number
  issueDocNo?: string
  remarks?: string
  chargedAt?: string
  chargedBy?: string
  dischargedAt?: string
  dischargedBy?: string
  cancelledAt?: string
  cancelledBy?: string
  cancelReason?: string
  createdAt: string
  createdBy: string
  version: number
  lines: BodyBatchLine[]
  sprayRuns: { runId: number; runNo: string; prodDate: string; finished: boolean }[]
}

export interface BodyBatchRequest {
  plantId: number
  prodDate: string
  shiftId: number
  ballMillId: number
  recipeId?: number
  chargeTon: number
  waterLitres?: number
  millingStart: string
  remarks?: string
  lines: { materialId: number; locationId: number; quantity: number }[]
}

export interface DischargeRequest {
  millingEnd: string
  slipTankId: number
  slipDensity: number
  slipViscosity?: number
  slipResidue: number
}

export interface SprayRunSummary {
  id: number
  runNo?: string
  prodDate: string
  shiftCode: string
  sprayDryerCode: string
  siloCode: string
  siloName: string
  powderTon: number
  powderMoisture?: number
  status: DocStatus
}

export interface SprayRunBatchUse {
  bodyBatchId: number
  batchNo?: string
  ballMillCode: string
  chargeTon: number
  slipTankCode?: string
  millingEnd?: string
  slipDensity?: number
  slipResidue?: number
  finished: boolean
}

export interface SprayRun {
  id: number
  plantId: number
  plantCode: string
  runNo?: string
  prodDate: string
  shiftId: number
  shiftCode: string
  shiftName: string
  sprayDryerId: number
  sprayDryerCode: string
  sprayDryerName: string
  siloId: number
  siloCode: string
  siloName: string
  status: DocStatus
  runStart: string
  runEnd?: string
  powderTon: number
  powderMoisture?: number
  gasScm?: number
  inletTemp?: number
  outletTemp?: number
  remarks?: string
  postedAt?: string
  postedBy?: string
  cancelledAt?: string
  cancelledBy?: string
  cancelReason?: string
  createdAt: string
  createdBy: string
  version: number
  batches: SprayRunBatchUse[]
}

export interface SprayRunRequest {
  plantId: number
  prodDate: string
  shiftId: number
  sprayDryerId: number
  siloId: number
  runStart: string
  runEnd?: string
  powderTon: number
  powderMoisture?: number
  gasScm?: number
  inletTemp?: number
  outletTemp?: number
  remarks?: string
  batches: { bodyBatchId: number; finished: boolean }[]
}

export interface DayTotal {
  count: number
  tonnes: number
}

export interface BodyPrepOverview {
  prodDate: string
  shiftId?: number
  shiftCode?: string
  shiftName?: string
  inMills: BodyBatchSummary[]
  waiting: BodyBatchSummary[]
  charged: DayTotal
  sprayed: DayTotal
}
