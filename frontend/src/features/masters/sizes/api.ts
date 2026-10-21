import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../../api/client'
import type { TileSize, TileSizeRequest } from '../../../api/types'

// Matches TileSizeController.java: /api/masters/sizes
const KEY = ['masters', 'sizes']

export function useSizes(enabled = true) {
  return useQuery({
    queryKey: KEY,
    queryFn: async () => (await api.get<TileSize[]>('/masters/sizes')).data,
    enabled,
  })
}

export function useSaveSize() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, body }: { id?: number; body: TileSizeRequest }) =>
      id
        ? (await api.put<TileSize>(`/masters/sizes/${id}`, body)).data
        : (await api.post<TileSize>('/masters/sizes', body)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: KEY }),
  })
}
