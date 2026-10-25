import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { EnergyUse, Meter, MeterRequest, RoundRequest, RoundResponse } from '../../api/types'

// Step 8c: /api/energy - meters, reading rounds and what was used

export function useMeters(plantId?: number, includeInactive = false) {
  return useQuery({
    queryKey: ['meters', plantId, includeInactive],
    queryFn: async () => (await api.get<Meter[]>('/energy/meters', { params: { plantId, includeInactive } })).data,
    enabled: plantId !== undefined,
  })
}

export function useSaveMeter() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, body }: { id?: number; body: MeterRequest }) =>
      (id ? await api.put<Meter>(`/energy/meters/${id}`, body) : await api.post<Meter>('/energy/meters', body)).data,
    onSuccess: () => {
      for (const key of ['meters', 'meter-round', 'energy-use']) queryClient.invalidateQueries({ queryKey: [key] })
    },
  })
}

/** A reading round; without a date, the one to read now (the shift that ended last). */
export function useRound(params: { plantId: number; date?: string; shiftId?: number } | null) {
  return useQuery({
    queryKey: ['meter-round', params],
    queryFn: async () => (await api.get<RoundResponse>('/energy/round', { params })).data,
    enabled: params !== null,
    placeholderData: keepPreviousData,
  })
}

function useRoundMutation<V>(fn: (vars: V) => Promise<RoundResponse | void>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      for (const key of ['meter-round', 'energy-use', 'dashboard']) queryClient.invalidateQueries({ queryKey: [key] })
    },
  })
}

export const useSaveRound = () =>
  useRoundMutation(async (body: RoundRequest) => (await api.post<RoundResponse>('/energy/round', body)).data)

export const useDeleteReading = () =>
  useRoundMutation(async (id: number) => {
    await api.delete(`/energy/readings/${id}`)
  })

export function useEnergyUse(params: { plantId: number; from: string; to: string } | null) {
  return useQuery({
    queryKey: ['energy-use', params],
    queryFn: async () => (await api.get<EnergyUse>('/energy/use', { params })).data,
    enabled: params !== null,
    placeholderData: keepPreviousData,
  })
}
