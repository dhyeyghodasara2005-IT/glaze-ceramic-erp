import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type {
  DocStatus, FgDocType, FgDocument, FgDocumentRequest, FgDocumentSummary, FgGodown, FgGodownRequest, FgLedger, FgOption,
  FgStockRow, GodownMap, PageResponse, PillarOption,
} from '../../api/types'

// Step 5: /api/fg - finished-goods godowns, pillars, stock and the warehouse's documents

export function useFgGodowns(plantId?: number) {
  return useQuery({
    queryKey: ['fg-godowns', plantId],
    queryFn: async () => (await api.get<FgGodown[]>('/fg/godowns', { params: { plantId } })).data,
    enabled: plantId !== undefined,
  })
}

export function useGodownMap(godownId?: number) {
  return useQuery({
    queryKey: ['godown-map', godownId],
    queryFn: async () => (await api.get<GodownMap>(`/fg/godowns/${godownId}/map`)).data,
    enabled: godownId !== undefined,
    refetchInterval: 60_000,
  })
}

export interface FgStockSearch {
  plantId: number
  godownId?: number
  gradeId?: number
  search?: string
}

export function useFgStock(params: FgStockSearch | null) {
  return useQuery({
    queryKey: ['fg-stock', params],
    queryFn: async () => (await api.get<FgStockRow[]>('/fg/stock', { params })).data,
    enabled: params !== null,
  })
}

export function useFgLedger(stockId?: number) {
  return useQuery({
    queryKey: ['fg-ledger', stockId],
    queryFn: async () => (await api.get<FgLedger>(`/fg/stock/${stockId}`)).data,
    enabled: stockId !== undefined,
  })
}

/** Any change to godowns or pillars: refresh every finished-goods view (and the pack entry's pillar list). */
function useFgMutation<V, R>(fn: (vars: V) => Promise<R>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      for (const key of ['fg-godowns', 'godown-map', 'fg-stock', 'pillar-options']) {
        queryClient.invalidateQueries({ queryKey: [key] })
      }
    },
  })
}

export const useSaveFgGodown = () =>
  useFgMutation(async ({ id, body }: { id?: number; body: FgGodownRequest }) =>
    id ? (await api.put<FgGodown>(`/fg/godowns/${id}`, body)).data : (await api.post<FgGodown>('/fg/godowns', body)).data,
  )

export const useAddPillars = () =>
  useFgMutation(async ({ godownId, body }: { godownId: number; body: { prefix: string; from: number; to: number; capacityPallets?: number } }) =>
    (await api.post<GodownMap>(`/fg/godowns/${godownId}/pillars`, body)).data,
  )

export const useUpdatePillar = () =>
  useFgMutation(async ({ id, body }: { id: number; body: { capacityPallets?: number; active: boolean } }) =>
    (await api.put<GodownMap>(`/fg/pillars/${id}`, body)).data,
  )

// ---- step 5b: the warehouse's documents and their dropdowns ----

export function useFgDesigns(enabled = true) {
  return useQuery({
    queryKey: ['fg-designs'],
    queryFn: async () => (await api.get<FgOption[]>('/fg/lookups/designs')).data,
    enabled,
    staleTime: 5 * 60_000,
  })
}

export function useFgGrades(enabled = true) {
  return useQuery({
    queryKey: ['fg-grades'],
    queryFn: async () => (await api.get<FgOption[]>('/fg/lookups/grades')).data,
    enabled,
    staleTime: 5 * 60_000,
  })
}

export function useFgPillars(plantId?: number) {
  return useQuery({
    queryKey: ['pillar-options', 'fg', plantId],
    queryFn: async () => (await api.get<PillarOption[]>('/fg/lookups/pillars', { params: { plantId } })).data,
    enabled: plantId !== undefined,
  })
}

export interface FgDocSearch {
  plantId: number
  type: FgDocType
  status?: DocStatus
  from?: string
  to?: string
  search?: string
  page: number
  size: number
}

export function useFgDocuments(params: FgDocSearch | null) {
  return useQuery({
    queryKey: ['fg-documents', params],
    queryFn: async () => (await api.get<PageResponse<FgDocumentSummary>>('/fg/documents', { params })).data,
    enabled: params !== null,
    placeholderData: keepPreviousData,
  })
}

export function useFgDocument(id?: number) {
  return useQuery({
    queryKey: ['fg-document', id],
    queryFn: async () => (await api.get<FgDocument>(`/fg/documents/${id}`)).data,
    enabled: id !== undefined,
  })
}

/** Saving, posting or cancelling a document changes stock: refresh every finished-goods view. */
function useFgDocMutation<V, R extends { id: number } | void>(fn: (vars: V) => Promise<R>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: (saved: R) => {
      if (saved) queryClient.setQueryData(['fg-document', saved.id], saved)
      for (const key of ['fg-documents', 'fg-stock', 'fg-ledger', 'godown-map', 'fg-godowns', 'pillar-options']) {
        queryClient.invalidateQueries({ queryKey: [key] })
      }
    },
  })
}

export const useSaveFgDocument = () =>
  useFgDocMutation(async ({ id, body }: { id?: number; body: FgDocumentRequest }) =>
    id ? (await api.put<FgDocument>(`/fg/documents/${id}`, body)).data : (await api.post<FgDocument>('/fg/documents', body)).data,
  )

export const usePostFgDocument = () =>
  useFgDocMutation(async (id: number) => (await api.post<FgDocument>(`/fg/documents/${id}/post`)).data)

export const useCancelFgDocument = () =>
  useFgDocMutation(async ({ id, reason }: { id: number; reason: string }) =>
    (await api.post<FgDocument>(`/fg/documents/${id}/cancel`, { reason })).data,
  )

export const useDeleteFgDocument = () =>
  useFgDocMutation(async (id: number) => {
    await api.delete(`/fg/documents/${id}`)
  })
