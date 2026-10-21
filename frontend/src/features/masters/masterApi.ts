import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'

/**
 * Shared data hooks for the simple masters (sizes, finishes, categories, grades).
 * They all have the same 4 endpoints, so one generic hook serves them all:
 *   GET  /api/masters/<path>        list
 *   POST /api/masters/<path>        create
 *   PUT  /api/masters/<path>/<id>   update
 *
 * <T> = what the server returns, <R> = what we send.
 */
export function useMasterList<T>(path: string, enabled = true) {
  return useQuery({
    queryKey: ['masters', path],
    queryFn: async () => (await api.get<T[]>(`/masters/${path}`)).data,
    enabled,
  })
}

export function useSaveMaster<T, R>(path: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, body }: { id?: number; body: R }) =>
      id
        ? (await api.put<T>(`/masters/${path}/${id}`, body)).data
        : (await api.post<T>(`/masters/${path}`, body)).data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['masters', path] })
      // design rows show size/finish/category names, so refresh them too
      queryClient.invalidateQueries({ queryKey: ['masters', 'designs'] })
    },
  })
}
