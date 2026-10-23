import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { FgGodown, FgGodownRequest, FgLedger, FgStockRow, GodownMap } from '../../api/types'

// Step 5a: /api/fg - finished-goods godowns, pillars and stock

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
