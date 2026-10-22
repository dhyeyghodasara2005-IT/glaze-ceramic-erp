import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { PageResponse, Party, PartyRequest, PartyType } from '../../api/types'

// Matches PartyController.java: /api/parties?type=DEALER&search=...&stateCode=...&page=0&size=20

export interface PartySearch {
  type: PartyType
  search?: string
  stateCode?: string
  active?: boolean
  page: number
  size: number
}

export function useParties(params: PartySearch) {
  return useQuery({
    queryKey: ['parties', params],
    queryFn: async () => (await api.get<PageResponse<Party>>('/parties', { params })).data,
    placeholderData: keepPreviousData,
  })
}

export const fetchParty = async (id: number) => (await api.get<Party>(`/parties/${id}`)).data

export function useSaveParty() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, body }: { id?: number; body: PartyRequest }) =>
      id ? (await api.put<Party>(`/parties/${id}`, body)).data : (await api.post<Party>('/parties', body)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['parties'] }),
  })
}
