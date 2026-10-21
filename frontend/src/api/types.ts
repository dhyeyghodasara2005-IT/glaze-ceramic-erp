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
