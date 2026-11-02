import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { CostRate, CostRateRequest, CostSheet, CostTrend, MonthlyCostRequest, MonthlyCostRow } from '../../api/types'

// Step 9b: /api/costing - the month's cost per m², fuel rates and monthly costs

/** month as 2026-10 */
export function useCostSheet(params: { plantId: number; month: string } | null) {
  return useQuery({
    queryKey: ['cost-sheet', params],
    queryFn: async () => (await api.get<CostSheet>('/costing/sheet', { params })).data,
    enabled: params !== null,
    placeholderData: keepPreviousData,
  })
}

export function useCostTrend(plantId?: number) {
  return useQuery({
    queryKey: ['cost-trend', plantId],
    queryFn: async () => (await api.get<CostTrend[]>('/costing/trend', { params: { plantId, months: 6 } })).data,
    enabled: plantId !== undefined,
  })
}

export function useCostRates(plantId?: number) {
  return useQuery({
    queryKey: ['cost-rates', plantId],
    queryFn: async () => (await api.get<CostRate[]>('/costing/rates', { params: { plantId } })).data,
    enabled: plantId !== undefined,
  })
}

function useRefreshCosts() {
  const queryClient = useQueryClient()
  return () => {
    for (const key of ['cost-rates', 'cost-sheet', 'cost-trend', 'monthly-costs']) queryClient.invalidateQueries({ queryKey: [key] })
  }
}

export function useSaveCostRate() {
  const refresh = useRefreshCosts()
  return useMutation({
    mutationFn: async ({ id, body }: { id?: number; body: CostRateRequest }) =>
      (id ? await api.put<CostRate>(`/costing/rates/${id}`, body) : await api.post<CostRate>('/costing/rates', body)).data,
    onSuccess: refresh,
  })
}

export function useDeleteCostRate() {
  const refresh = useRefreshCosts()
  return useMutation({
    mutationFn: async (id: number) => {
      await api.delete(`/costing/rates/${id}`)
    },
    onSuccess: refresh,
  })
}

export function useMonthlyCosts(params: { plantId: number; month: string } | null) {
  return useQuery({
    queryKey: ['monthly-costs', params],
    queryFn: async () => (await api.get<MonthlyCostRow[]>('/costing/monthly', { params })).data,
    enabled: params !== null,
  })
}

export function useSaveMonthlyCosts() {
  const refresh = useRefreshCosts()
  return useMutation({
    mutationFn: async (body: MonthlyCostRequest) => (await api.put<MonthlyCostRow[]>('/costing/monthly', body)).data,
    onSuccess: refresh,
  })
}
