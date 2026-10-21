import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../../api/client'
import type { Design, DesignRequest, PageResponse } from '../../../api/types'

// Matches DesignController.java: /api/masters/designs (paged + filterable)

export interface DesignSearch {
  search?: string
  categoryId?: number
  sizeId?: number
  finishId?: number
  active?: boolean
  page: number // 0-based, like Spring
  size: number
}

export function useDesigns(params: DesignSearch) {
  return useQuery({
    queryKey: ['masters', 'designs', params],
    queryFn: async () => (await api.get<PageResponse<Design>>('/masters/designs', { params })).data,
    placeholderData: keepPreviousData, // keep the old page visible while the next loads
  })
}

export function useSaveDesign() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, body }: { id?: number; body: DesignRequest }) =>
      id
        ? (await api.put<Design>(`/masters/designs/${id}`, body)).data
        : (await api.post<Design>('/masters/designs', body)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['masters', 'designs'] }),
  })
}
