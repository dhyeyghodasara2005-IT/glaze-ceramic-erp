import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type {
  Breakdown, BreakdownCloseRequest, BreakdownReportRequest, BreakdownStatus, BreakdownSummary, DowntimeResponse, MachineRef,
  PageResponse, SpareOption, StoreOption,
} from '../../api/types'

// Step 8a: /api/maintenance - the breakdown log and machine downtime

export interface BreakdownSearch {
  plantId: number
  status?: BreakdownStatus
  machineId?: number
  from?: string
  to?: string
  search?: string
  page: number
  size: number
}

export function useBreakdowns(params: BreakdownSearch | null) {
  return useQuery({
    queryKey: ['breakdowns', params],
    queryFn: async () => (await api.get<PageResponse<BreakdownSummary>>('/maintenance/breakdowns', { params })).data,
    enabled: params !== null,
    placeholderData: keepPreviousData,
    // the "down for 1 h 20 m" of others' reports stays fresh
    refetchInterval: 60_000,
  })
}

export function useBreakdown(id?: number) {
  return useQuery({
    queryKey: ['breakdown', id],
    queryFn: async () => (await api.get<Breakdown>(`/maintenance/breakdowns/${id}`)).data,
    enabled: id !== undefined,
  })
}

export function useDowntime(params: { plantId: number; from: string; to: string } | null) {
  return useQuery({
    queryKey: ['downtime', params],
    queryFn: async () => (await api.get<DowntimeResponse>('/maintenance/downtime', { params })).data,
    enabled: params !== null,
    placeholderData: keepPreviousData,
    refetchInterval: 60_000,
  })
}

export function useMaintenanceMachines(plantId?: number) {
  return useQuery({
    queryKey: ['maint-lookup', 'machines', plantId],
    queryFn: async () => (await api.get<MachineRef[]>('/maintenance/lookups/machines', { params: { plantId } })).data,
    enabled: plantId !== undefined,
    staleTime: 5 * 60_000,
  })
}

export function useSpares(enabled: boolean) {
  return useQuery({
    queryKey: ['maint-lookup', 'spares'],
    queryFn: async () => (await api.get<SpareOption[]>('/maintenance/lookups/spares')).data,
    enabled,
    staleTime: 5 * 60_000,
  })
}

export function useSpareGodowns(plantId?: number) {
  return useQuery({
    queryKey: ['maint-lookup', 'godowns', plantId],
    queryFn: async () => (await api.get<StoreOption[]>('/maintenance/lookups/godowns', { params: { plantId } })).data,
    enabled: plantId !== undefined,
    staleTime: 5 * 60_000,
  })
}

export function useSpareStock(plantId?: number) {
  return useQuery({
    queryKey: ['maint-lookup', 'stock', plantId],
    queryFn: async () => (await api.get<{ materialId: number; locationId: number; quantity: number }[]>(
      '/maintenance/lookups/stock', { params: { plantId } })).data,
    enabled: plantId !== undefined,
  })
}

/** A report or a fix changes the lists, the figures, the dashboard - and (spares) the store stock. */
function useBreakdownMutation<V>(fn: (vars: V) => Promise<Breakdown>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: (saved) => {
      queryClient.setQueryData(['breakdown', saved.id], saved)
      for (const key of ['breakdowns', 'downtime', 'dashboard', 'stock', 'ledger', 'store-docs', 'maint-lookup']) {
        queryClient.invalidateQueries({ queryKey: [key] })
      }
    },
  })
}

export const useReportBreakdown = () =>
  useBreakdownMutation(async ({ id, body }: { id?: number; body: BreakdownReportRequest }) =>
    id ? (await api.put<Breakdown>(`/maintenance/breakdowns/${id}`, body)).data
      : (await api.post<Breakdown>('/maintenance/breakdowns', body)).data,
  )

export const useCloseBreakdown = () =>
  useBreakdownMutation(async ({ id, body }: { id: number; body: BreakdownCloseRequest }) =>
    (await api.post<Breakdown>(`/maintenance/breakdowns/${id}/close`, body)).data,
  )

export const useCancelBreakdown = () =>
  useBreakdownMutation(async ({ id, reason }: { id: number; reason: string }) =>
    (await api.post<Breakdown>(`/maintenance/breakdowns/${id}/cancel`, { reason })).data,
  )
