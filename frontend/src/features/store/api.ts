import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type {
  DocStatus,
  LedgerRow,
  PageResponse,
  StockRow,
  StoreDocType,
  StoreDocument,
  StoreDocumentRequest,
  StoreDocumentSummary,
  StoreLocation,
  StoreLocationRequest,
  StoreOption,
} from '../../api/types'

// Matches the controllers in com.ceramicerp.store:
//   /api/store/locations   /api/store/documents   /api/store/stock   /api/store/lookups

// ---- godowns ----

export function useGodowns(plantId?: number) {
  return useQuery({
    queryKey: ['godowns', plantId],
    queryFn: async () => (await api.get<StoreLocation[]>('/store/locations', { params: { plantId } })).data,
    enabled: plantId !== undefined,
  })
}

export function useSaveGodown() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, body }: { id?: number; body: StoreLocationRequest }) =>
      id
        ? (await api.put<StoreLocation>(`/store/locations/${id}`, body)).data
        : (await api.post<StoreLocation>('/store/locations', body)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['godowns'] }),
  })
}

// ---- dropdowns (only need STORE_VIEW) ----

const LOOKUP_STALE = 60_000

export function useStoreMaterials() {
  return useQuery({
    queryKey: ['store-lookup', 'materials'],
    queryFn: async () => (await api.get<StoreOption[]>('/store/lookups/materials')).data,
    staleTime: LOOKUP_STALE,
  })
}

export function useStoreSuppliers(enabled = true) {
  return useQuery({
    queryKey: ['store-lookup', 'suppliers'],
    queryFn: async () => (await api.get<StoreOption[]>('/store/lookups/suppliers')).data,
    staleTime: LOOKUP_STALE,
    enabled,
  })
}

export function useStoreMachines(plantId?: number, enabled = true) {
  return useQuery({
    queryKey: ['store-lookup', 'machines', plantId],
    queryFn: async () => (await api.get<StoreOption[]>('/store/lookups/machines', { params: { plantId } })).data,
    staleTime: LOOKUP_STALE,
    enabled: enabled && plantId !== undefined,
  })
}

// ---- stock ----

export function useStock(plantId?: number) {
  return useQuery({
    queryKey: ['stock', plantId],
    queryFn: async () => (await api.get<StockRow[]>('/store/stock', { params: { plantId } })).data,
    enabled: plantId !== undefined,
  })
}

export interface LedgerSearch {
  plantId: number
  materialId: number
  locationId?: number
  page: number
  size: number
}

export function useLedger(params: LedgerSearch | null) {
  return useQuery({
    queryKey: ['ledger', params],
    queryFn: async () => (await api.get<PageResponse<LedgerRow>>('/store/stock/ledger', { params })).data,
    enabled: params !== null,
    placeholderData: keepPreviousData,
  })
}

// ---- documents ----

export interface DocumentSearch {
  plantId: number
  type: StoreDocType
  status?: DocStatus
  from?: string
  to?: string
  search?: string
  page: number
  size: number
}

export function useStoreDocuments(params: DocumentSearch | null) {
  return useQuery({
    queryKey: ['store-docs', params],
    queryFn: async () => (await api.get<PageResponse<StoreDocumentSummary>>('/store/documents', { params })).data,
    enabled: params !== null,
    placeholderData: keepPreviousData,
  })
}

export function useStoreDocument(id?: number) {
  return useQuery({
    queryKey: ['store-doc', id],
    queryFn: async () => (await api.get<StoreDocument>(`/store/documents/${id}`)).data,
    enabled: id !== undefined,
  })
}

/**
 * Every change to a document can change stock, lists and ledgers, so all of them are refreshed.
 * The changed document itself is put straight into the cache (no extra request).
 */
function useDocumentMutation<V, R extends StoreDocument | void>(fn: (vars: V) => Promise<R>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: (doc: R) => {
      if (doc) queryClient.setQueryData(['store-doc', doc.id], doc)
      for (const key of ['store-docs', 'stock', 'ledger']) queryClient.invalidateQueries({ queryKey: [key] })
    },
  })
}

export const useSaveDocument = () =>
  useDocumentMutation(async ({ id, body }: { id?: number; body: StoreDocumentRequest }) =>
    id
      ? (await api.put<StoreDocument>(`/store/documents/${id}`, body)).data
      : (await api.post<StoreDocument>('/store/documents', body)).data,
  )

export const usePostDocument = () =>
  useDocumentMutation(async (id: number) => (await api.post<StoreDocument>(`/store/documents/${id}/post`)).data)

export const useCancelDocument = () =>
  useDocumentMutation(async ({ id, reason }: { id: number; reason: string }) =>
    (await api.post<StoreDocument>(`/store/documents/${id}/cancel`, { reason })).data,
  )

export const useDeleteDocument = () =>
  useDocumentMutation(async (id: number) => {
    await api.delete(`/store/documents/${id}`)
  })
