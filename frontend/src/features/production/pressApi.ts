import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '../../api/client'
import type {
  DesignOption,
  DocStatus,
  Lot,
  LotOpenRequest,
  LotStatus,
  LotSummary,
  PageResponse,
  PressEntry,
  PressEntryRequest,
  PressEntrySummary,
  PressOverview,
  SiloStatus,
} from '../../api/types'
import { useProdMutation } from './api'

// Step 4b: /api/production/lots, /press-entries, /silos, /press/overview

export function useDesignOptions() {
  return useQuery({
    queryKey: ['prod-lookup', 'designs'],
    queryFn: async () => (await api.get<DesignOption[]>('/production/lookups/designs')).data,
    staleTime: 60_000,
  })
}

export function usePressOverview(plantId?: number) {
  return useQuery({
    queryKey: ['press-overview', plantId],
    queryFn: async () => (await api.get<PressOverview>('/production/press/overview', { params: { plantId } })).data,
    enabled: plantId !== undefined,
    refetchInterval: 60_000,
  })
}

export function useSilos(plantId?: number) {
  return useQuery({
    queryKey: ['silos', plantId],
    queryFn: async () => (await api.get<SiloStatus[]>('/production/silos', { params: { plantId } })).data,
    enabled: plantId !== undefined,
  })
}

export const useMarkSiloEmpty = () =>
  useProdMutation('silo', async (siloId: number) => {
    await api.post(`/production/silos/${siloId}/empty`)
  })

// ---- design lots ----

export interface LotSearch {
  plantId: number
  status?: LotStatus
  from?: string
  to?: string
  search?: string
  page: number
  size: number
}

export function useLots(params: LotSearch | null) {
  return useQuery({
    queryKey: ['lots', params],
    queryFn: async () => (await api.get<PageResponse<LotSummary>>('/production/lots', { params })).data,
    enabled: params !== null,
    placeholderData: keepPreviousData,
  })
}

/** Lots a press entry can be made for (running, or closed in the last few days). */
export function useSelectableLots(plantId?: number) {
  return useQuery({
    queryKey: ['selectable-lots', plantId],
    queryFn: async () => (await api.get<LotSummary[]>('/production/lots/selectable', { params: { plantId } })).data,
    enabled: plantId !== undefined,
  })
}

export function useLot(id?: number) {
  return useQuery({
    queryKey: ['lot', id],
    queryFn: async () => (await api.get<Lot>(`/production/lots/${id}`)).data,
    enabled: id !== undefined,
  })
}

export const useOpenLot = () =>
  useProdMutation('lot', async (body: LotOpenRequest) => (await api.post<Lot>('/production/lots', body)).data)

export const useCloseLot = () =>
  useProdMutation('lot', async (id: number) => (await api.post<Lot>(`/production/lots/${id}/close`)).data)

export const useCancelLot = () =>
  useProdMutation('lot', async ({ id, reason }: { id: number; reason: string }) =>
    (await api.post<Lot>(`/production/lots/${id}/cancel`, { reason })).data,
  )

// ---- press entries ----

export interface EntrySearch {
  plantId: number
  status?: DocStatus
  from?: string
  to?: string
  search?: string
  page: number
  size: number
}

export function usePressEntries(params: EntrySearch | null) {
  return useQuery({
    queryKey: ['press-entries', params],
    queryFn: async () => (await api.get<PageResponse<PressEntrySummary>>('/production/press-entries', { params })).data,
    enabled: params !== null,
    placeholderData: keepPreviousData,
  })
}

export function usePressEntry(id?: number) {
  return useQuery({
    queryKey: ['press-entry', id],
    queryFn: async () => (await api.get<PressEntry>(`/production/press-entries/${id}`)).data,
    enabled: id !== undefined,
  })
}

export const useSaveEntry = () =>
  useProdMutation('press-entry', async ({ id, body }: { id?: number; body: PressEntryRequest }) =>
    id
      ? (await api.put<PressEntry>(`/production/press-entries/${id}`, body)).data
      : (await api.post<PressEntry>('/production/press-entries', body)).data,
  )

export const usePostEntry = () =>
  useProdMutation('press-entry', async (id: number) => (await api.post<PressEntry>(`/production/press-entries/${id}/post`)).data)

export const useCancelEntry = () =>
  useProdMutation('press-entry', async ({ id, reason }: { id: number; reason: string }) =>
    (await api.post<PressEntry>(`/production/press-entries/${id}/cancel`, { reason })).data,
  )

export const useDeleteEntry = () =>
  useProdMutation('press-entry', async (id: number) => {
    await api.delete(`/production/press-entries/${id}`)
  })
