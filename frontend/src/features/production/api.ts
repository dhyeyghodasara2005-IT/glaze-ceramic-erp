import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type {
  AvailableStock,
  BodyBatch,
  BodyBatchRequest,
  BodyBatchStatus,
  BodyBatchSummary,
  BodyPrepOverview,
  BodyRecipe,
  CurrentShift,
  DischargeRequest,
  DocStatus,
  MachineOption,
  PageResponse,
  Shift,
  SprayRun,
  SprayRunRequest,
  SprayRunSummary,
  StoreOption,
} from '../../api/types'

// Matches com.ceramicerp.production: /api/production/...
// The lookups only need PRODUCTION_VIEW, so a supervisor needs no master or store rights.

const LOOKUP_STALE = 60_000

export function useCurrentShift() {
  return useQuery({
    queryKey: ['prod-lookup', 'current-shift'],
    queryFn: async () => (await api.get<CurrentShift>('/production/lookups/current-shift')).data,
    staleTime: 5 * 60_000,
  })
}

export function useProdShifts() {
  return useQuery({
    queryKey: ['prod-lookup', 'shifts'],
    queryFn: async () => (await api.get<Shift[]>('/production/lookups/shifts')).data,
    staleTime: LOOKUP_STALE,
  })
}

export function useProdRecipes() {
  return useQuery({
    queryKey: ['prod-lookup', 'recipes'],
    queryFn: async () => (await api.get<BodyRecipe[]>('/production/lookups/recipes')).data,
    staleTime: LOOKUP_STALE,
  })
}

export function useProdMachines(plantId?: number) {
  return useQuery({
    queryKey: ['prod-lookup', 'machines', plantId],
    queryFn: async () => (await api.get<MachineOption[]>('/production/lookups/machines', { params: { plantId } })).data,
    staleTime: LOOKUP_STALE,
    enabled: plantId !== undefined,
  })
}

export function useProdMaterials() {
  return useQuery({
    queryKey: ['prod-lookup', 'materials'],
    queryFn: async () => (await api.get<StoreOption[]>('/production/lookups/materials')).data,
    staleTime: LOOKUP_STALE,
  })
}

export function useProdGodowns(plantId?: number) {
  return useQuery({
    queryKey: ['prod-lookup', 'godowns', plantId],
    queryFn: async () => (await api.get<StoreOption[]>('/production/lookups/godowns', { params: { plantId } })).data,
    staleTime: LOOKUP_STALE,
    enabled: plantId !== undefined,
  })
}

/** Stock quantities per material and godown (no values). Refreshed after every charge. */
export function useProdStock(plantId?: number) {
  return useQuery({
    queryKey: ['prod-stock', plantId],
    queryFn: async () => (await api.get<AvailableStock[]>('/production/lookups/stock', { params: { plantId } })).data,
    enabled: plantId !== undefined,
  })
}

export function useBodyOverview(plantId?: number) {
  return useQuery({
    queryKey: ['body-overview', plantId],
    queryFn: async () => (await api.get<BodyPrepOverview>('/production/body/overview', { params: { plantId } })).data,
    enabled: plantId !== undefined,
    refetchInterval: 60_000, // the supervisor's screen stays current without touching it
  })
}

// ---- body batches ----

export interface BatchSearch {
  plantId: number
  status?: BodyBatchStatus
  from?: string
  to?: string
  search?: string
  page: number
  size: number
}

export function useBodyBatches(params: BatchSearch | null) {
  return useQuery({
    queryKey: ['body-batches', params],
    queryFn: async () => (await api.get<PageResponse<BodyBatchSummary>>('/production/body-batches', { params })).data,
    enabled: params !== null,
    placeholderData: keepPreviousData,
  })
}

export function useBodyBatch(id?: number) {
  return useQuery({
    queryKey: ['body-batch', id],
    queryFn: async () => (await api.get<BodyBatch>(`/production/body-batches/${id}`)).data,
    enabled: id !== undefined,
  })
}

/** After any production change: refresh lists, the hub, stock quantities and the store's views. */
const AFFECTED = [
  'body-batches', 'spray-runs', 'body-overview', 'prod-stock', 'available-batches', 'stock', 'store-docs', 'ledger',
  'lots', 'selectable-lots', 'press-entries', 'press-overview', 'silos', 'line-entries', 'line-overview',
  'finishing-overview', 'pack-entries', 'pack-codes', 'lot-checks',
  // posting a pack entry puts boxes into finished-goods stock
  'pillar-options', 'fg-stock', 'fg-godowns', 'godown-map', 'fg-ledger', 'put-away',
]

export function useProdMutation<V, R extends { id: number } | void>(cacheKey: string, fn: (vars: V) => Promise<R>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: (saved: R) => {
      if (saved) queryClient.setQueryData([cacheKey, saved.id], saved)
      for (const key of AFFECTED) queryClient.invalidateQueries({ queryKey: [key] })
      // Records point at each other (batch <-> run <-> press entry <-> lot): refresh the single-record views too.
      for (const key of ['body-batch', 'spray-run', 'lot', 'press-entry', 'line-entry', 'pack-entry', 'lot-check']) {
        if (key !== cacheKey) queryClient.invalidateQueries({ queryKey: [key] })
      }
    },
  })
}

export const useSaveBatch = () =>
  useProdMutation('body-batch', async ({ id, body }: { id?: number; body: BodyBatchRequest }) =>
    id
      ? (await api.put<BodyBatch>(`/production/body-batches/${id}`, body)).data
      : (await api.post<BodyBatch>('/production/body-batches', body)).data,
  )

export const useChargeBatch = () =>
  useProdMutation('body-batch', async (id: number) => (await api.post<BodyBatch>(`/production/body-batches/${id}/charge`)).data)

export const useDischargeBatch = () =>
  useProdMutation('body-batch', async ({ id, body }: { id: number; body: DischargeRequest }) =>
    (await api.post<BodyBatch>(`/production/body-batches/${id}/discharge`, body)).data,
  )

export const useCancelBatch = () =>
  useProdMutation('body-batch', async ({ id, reason }: { id: number; reason: string }) =>
    (await api.post<BodyBatch>(`/production/body-batches/${id}/cancel`, { reason })).data,
  )

export const useDeleteBatch = () =>
  useProdMutation('body-batch', async (id: number) => {
    await api.delete(`/production/body-batches/${id}`)
  })

// ---- spray dryer runs ----

export interface RunSearch {
  plantId: number
  status?: DocStatus
  from?: string
  to?: string
  search?: string
  page: number
  size: number
}

export function useSprayRuns(params: RunSearch | null) {
  return useQuery({
    queryKey: ['spray-runs', params],
    queryFn: async () => (await api.get<PageResponse<SprayRunSummary>>('/production/spray-runs', { params })).data,
    enabled: params !== null,
    placeholderData: keepPreviousData,
  })
}

export function useSprayRun(id?: number) {
  return useQuery({
    queryKey: ['spray-run', id],
    queryFn: async () => (await api.get<SprayRun>(`/production/spray-runs/${id}`)).data,
    enabled: id !== undefined,
  })
}

export function useAvailableBatches(plantId?: number) {
  return useQuery({
    queryKey: ['available-batches', plantId],
    queryFn: async () =>
      (await api.get<BodyBatchSummary[]>('/production/spray-runs/available-batches', { params: { plantId } })).data,
    enabled: plantId !== undefined,
  })
}

export const useSaveRun = () =>
  useProdMutation('spray-run', async ({ id, body }: { id?: number; body: SprayRunRequest }) =>
    id
      ? (await api.put<SprayRun>(`/production/spray-runs/${id}`, body)).data
      : (await api.post<SprayRun>('/production/spray-runs', body)).data,
  )

export const usePostRun = () =>
  useProdMutation('spray-run', async (id: number) => (await api.post<SprayRun>(`/production/spray-runs/${id}/post`)).data)

export const useCancelRun = () =>
  useProdMutation('spray-run', async ({ id, reason }: { id: number; reason: string }) =>
    (await api.post<SprayRun>(`/production/spray-runs/${id}/cancel`, { reason })).data,
  )

export const useDeleteRun = () =>
  useProdMutation('spray-run', async (id: number) => {
    await api.delete(`/production/spray-runs/${id}`)
  })
