import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { Company, CompanyRequest } from '../../api/types'

// React Query keeps server data in a cache keyed by these arrays.
// After a save we "invalidate" the key, and every list showing companies refreshes itself.
const KEY = ['companies']

export function useCompanies(enabled = true) {
  return useQuery({
    queryKey: KEY,
    queryFn: async () => (await api.get<Company[]>('/companies')).data,
    enabled,
  })
}

export function useSaveCompany() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, body }: { id?: number; body: CompanyRequest }) =>
      id
        ? (await api.put<Company>(`/companies/${id}`, body)).data
        : (await api.post<Company>('/companies', body)).data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: KEY })
      queryClient.invalidateQueries({ queryKey: ['plants'] }) // plant rows show the company name
    },
  })
}
