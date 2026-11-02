import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { WeatherShift, WeatherStatus } from '../../api/types'

// Matches com.ceramicerp.weather.WeatherController: /api/weather/...

export function useWeatherStatus(plantId?: number) {
  return useQuery({
    queryKey: ['weather-status', plantId],
    queryFn: async () => (await api.get<WeatherStatus>('/weather/status', { params: { plantId } })).data,
    enabled: plantId !== undefined,
  })
}

export function useWeatherShifts(params: { plantId: number; from: string; to: string } | null) {
  return useQuery({
    queryKey: ['weather-shifts', params],
    queryFn: async () => (await api.get<WeatherShift[]>('/weather/shifts', { params })).data,
    enabled: params !== null,
    placeholderData: keepPreviousData,
  })
}

export function useCollectWeather() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (plantId: number) => (await api.post<WeatherStatus>('/weather/collect', null, { params: { plantId } })).data,
    onSuccess: () => {
      for (const key of ['weather-status', 'weather-shifts']) queryClient.invalidateQueries({ queryKey: [key] })
    },
  })
}
