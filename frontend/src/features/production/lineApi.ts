import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { DocStatus, LineEntry, LineEntryRequest, LineEntrySummary, LineOverview, LineStage, PageResponse, Permission } from '../../api/types'
import { useProdMutation } from './api'

// Step 4c/4d: /api/production/line-entries?stage=GLAZE|KILN|POLISH and /api/production/line/overview

export function useLineOverview(plantId?: number) {
  return useQuery({
    queryKey: ['line-overview', plantId],
    queryFn: async () => (await api.get<LineOverview>('/production/line/overview', { params: { plantId } })).data,
    enabled: plantId !== undefined,
    refetchInterval: 60_000,
  })
}

export interface LineSearch {
  plantId: number
  stage: LineStage
  status?: DocStatus
  from?: string
  to?: string
  search?: string
  page: number
  size: number
}

export function useLineEntries(params: LineSearch | null) {
  return useQuery({
    queryKey: ['line-entries', params],
    queryFn: async () => (await api.get<PageResponse<LineEntrySummary>>('/production/line-entries', { params })).data,
    enabled: params !== null,
    placeholderData: keepPreviousData,
  })
}

export function useLineEntry(id?: number) {
  return useQuery({
    queryKey: ['line-entry', id],
    queryFn: async () => (await api.get<LineEntry>(`/production/line-entries/${id}`)).data,
    enabled: id !== undefined,
  })
}

export const useSaveLineEntry = () =>
  useProdMutation('line-entry', async ({ id, body }: { id?: number; body: LineEntryRequest }) =>
    id
      ? (await api.put<LineEntry>(`/production/line-entries/${id}`, body)).data
      : (await api.post<LineEntry>('/production/line-entries', body)).data,
  )

export const usePostLineEntry = () =>
  useProdMutation('line-entry', async (id: number) => (await api.post<LineEntry>(`/production/line-entries/${id}/post`)).data)

export const useCancelLineEntry = () =>
  useProdMutation('line-entry', async ({ id, reason }: { id: number; reason: string }) =>
    (await api.post<LineEntry>(`/production/line-entries/${id}/cancel`, { reason })).data,
  )

export const useDeleteLineEntry = () =>
  useProdMutation('line-entry', async (id: number) => {
    await api.delete(`/production/line-entries/${id}`)
  })

/** Where each stage lives in the app (lists, editors, search hits). */
export const LINE_PATHS: Record<LineStage, string> = {
  GLAZE: '/production/glaze-entries',
  KILN: '/production/kiln-entries',
  POLISH: '/production/polish-entries',
}

/** The permission a stage's supervisor needs. */
export const LINE_PERMISSION: Record<LineStage, Permission> = { GLAZE: 'GLAZE_ENTRY', KILN: 'KILN_ENTRY', POLISH: 'POLISH_ENTRY' }
