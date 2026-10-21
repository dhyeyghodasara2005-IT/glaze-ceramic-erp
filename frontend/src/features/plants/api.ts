import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { Plant, PlantRequest } from '../../api/types'

export function usePlants(companyId?: number, enabled = true) {
  return useQuery({
    queryKey: ['plants', { companyId }],
    queryFn: async () => (await api.get<Plant[]>('/plants', { params: { companyId } })).data,
    enabled,
  })
}

export function useSavePlant() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, body }: { id?: number; body: PlantRequest }) =>
      id
        ? (await api.put<Plant>(`/plants/${id}`, body)).data
        : (await api.post<Plant>('/plants', body)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['plants'] }),
  })
}
