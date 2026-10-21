import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../../api/client'
import type { Material, MaterialRequest, PageResponse } from '../../../api/types'

// Matches MaterialController.java: /api/masters/materials (paged + filterable)

export interface MaterialSearch {
  search?: string
  groupId?: number
  active?: boolean
  page: number
  size: number
}

export function useMaterials(params: MaterialSearch) {
  return useQuery({
    queryKey: ['masters', 'materials', params],
    queryFn: async () => (await api.get<PageResponse<Material>>('/masters/materials', { params })).data,
    placeholderData: keepPreviousData,
  })
}

export const fetchMaterial = async (id: number) => (await api.get<Material>(`/masters/materials/${id}`)).data

export function useSaveMaterial() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, body }: { id?: number; body: MaterialRequest }) =>
      id
        ? (await api.put<Material>(`/masters/materials/${id}`, body)).data
        : (await api.post<Material>('/masters/materials', body)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['masters', 'materials'] }),
  })
}
