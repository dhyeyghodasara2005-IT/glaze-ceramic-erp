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
  'SPRAY_DRYER',
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
