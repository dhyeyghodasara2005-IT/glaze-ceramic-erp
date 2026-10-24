import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '../../api/client'
import type {
  DocStatus, FinishingOverview, GradeOption, LotCheck, LotCheckRequest, PackCodes, PackEntry, PackEntryRequest,
  PackEntrySummary, PageResponse, PillarOption, PutAwaySuggestion,
} from '../../api/types'
import { useProdMutation } from './api'

// Step 4d: /api/production/finishing/overview, /api/production/lot-checks, /api/production/pack-entries

export const PACK_PATH = '/production/pack-entries'
export const CHECKS_PATH = '/production/quality-checks'

export function useFinishingOverview(plantId?: number) {
  return useQuery({
    queryKey: ['finishing-overview', plantId],
    queryFn: async () => (await api.get<FinishingOverview>('/production/finishing/overview', { params: { plantId } })).data,
    enabled: plantId !== undefined,
    refetchInterval: 60_000,
  })
}

/** Grades boxes can be packed in, best first (no Reject: broken tiles are counted, not boxed). */
export function useGradeOptions(enabled = true) {
  return useQuery({
    queryKey: ['prod-grades'],
    queryFn: async () => (await api.get<GradeOption[]>('/production/lookups/grades')).data,
    enabled,
    staleTime: 5 * 60_000,
  })
}

/** Open pillars of the plant's finished-goods godowns, with how full each is (step 5a). */
export function usePillarOptions(plantId?: number) {
  return useQuery({
    queryKey: ['pillar-options', plantId],
    queryFn: async () => (await api.get<PillarOption[]>('/production/lookups/pillars', { params: { plantId } })).data,
    enabled: plantId !== undefined,
  })
}

/**
 * Where a row of boxes should go, best first (step 6a). Waits until the row has a lot and a grade;
 * shade, caliber and boxes make the suggestion better as they are typed.
 */
export function usePutAway(params: { plantId?: number; lotId?: number; gradeId?: number; shade?: string; caliber?: string; boxes?: number }) {
  const ready = params.plantId !== undefined && params.lotId !== undefined && params.gradeId !== undefined
  return useQuery({
    queryKey: ['put-away', params],
    queryFn: async () => (await api.get<PutAwaySuggestion[]>('/production/lookups/put-away', { params })).data,
    enabled: ready,
    placeholderData: keepPreviousData,
  })
}

// ---- quality checks ----

export interface CheckSearch {
  plantId: number
  status?: DocStatus
  from?: string
  to?: string
  search?: string
  page: number
  size: number
}

export function useLotChecks(params: CheckSearch | null) {
  return useQuery({
    queryKey: ['lot-checks', params],
    queryFn: async () => (await api.get<PageResponse<LotCheck>>('/production/lot-checks', { params })).data,
    enabled: params !== null,
    placeholderData: keepPreviousData,
  })
}

export const fetchLotCheck = async (id: number) => (await api.get<LotCheck>(`/production/lot-checks/${id}`)).data

export const useCreateLotCheck = () =>
  useProdMutation('lot-check', async (body: LotCheckRequest) => (await api.post<LotCheck>('/production/lot-checks', body)).data)

export const useCancelLotCheck = () =>
  useProdMutation('lot-check', async ({ id, reason }: { id: number; reason: string }) =>
    (await api.post<LotCheck>(`/production/lot-checks/${id}/cancel`, { reason })).data,
  )

// ---- sorting & packing ----

export interface PackSearch {
  plantId: number
  status?: DocStatus
  from?: string
  to?: string
  search?: string
  page: number
  size: number
}

export function usePackEntries(params: PackSearch | null) {
  return useQuery({
    queryKey: ['pack-entries', params],
    queryFn: async () => (await api.get<PageResponse<PackEntrySummary>>('/production/pack-entries', { params })).data,
    enabled: params !== null,
    placeholderData: keepPreviousData,
  })
}

export function usePackEntry(id?: number) {
  return useQuery({
    queryKey: ['pack-entry', id],
    queryFn: async () => (await api.get<PackEntry>(`/production/pack-entries/${id}`)).data,
    enabled: id !== undefined,
  })
}

/** Shades and calibers the lot has used so far, suggested first. */
export function usePackCodes(lotId?: number) {
  return useQuery({
    queryKey: ['pack-codes', lotId],
    queryFn: async () => (await api.get<PackCodes>('/production/pack-entries/codes', { params: { lotId } })).data,
    enabled: lotId !== undefined,
  })
}

export const useSavePackEntry = () =>
  useProdMutation('pack-entry', async ({ id, body }: { id?: number; body: PackEntryRequest }) =>
    id
      ? (await api.put<PackEntry>(`/production/pack-entries/${id}`, body)).data
      : (await api.post<PackEntry>('/production/pack-entries', body)).data,
  )

export const usePostPackEntry = () =>
  useProdMutation('pack-entry', async (id: number) => (await api.post<PackEntry>(`/production/pack-entries/${id}/post`)).data)

export const useCancelPackEntry = () =>
  useProdMutation('pack-entry', async ({ id, reason }: { id: number; reason: string }) =>
    (await api.post<PackEntry>(`/production/pack-entries/${id}/cancel`, { reason })).data,
  )

export const useDeletePackEntry = () =>
  useProdMutation('pack-entry', async (id: number) => {
    await api.delete(`/production/pack-entries/${id}`)
  })
