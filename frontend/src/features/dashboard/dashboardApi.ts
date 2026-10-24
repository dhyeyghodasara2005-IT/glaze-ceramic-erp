import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { DashboardResponse } from '../../api/types'

// Step 7: /api/dashboard - the owner's numbers for one plant and period

export function useDashboard(params: { plantId: number; from: string; to: string } | null) {
  return useQuery({
    queryKey: ['dashboard', params],
    queryFn: async () => (await api.get<DashboardResponse>('/dashboard', { params })).data,
    enabled: params !== null,
    // a new period keeps the old numbers on screen (faded) until the new ones arrive: no flash, no jump
    placeholderData: keepPreviousData,
    refetchInterval: 5 * 60_000,
  })
}
