import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { PermissionInfo, Role, RoleRequest } from '../../api/types'

const KEY = ['roles']

export function useRoles(enabled = true) {
  return useQuery({
    queryKey: KEY,
    queryFn: async () => (await api.get<Role[]>('/roles')).data,
    enabled,
  })
}

export function usePermissions(enabled = true) {
  return useQuery({
    queryKey: ['permissions'],
    queryFn: async () => (await api.get<PermissionInfo[]>('/permissions')).data,
    staleTime: Infinity, // the list only changes when the backend is updated
    enabled,
  })
}

export function useSaveRole() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, body }: { id?: number; body: RoleRequest }) =>
      id ? (await api.put<Role>(`/roles/${id}`, body)).data : (await api.post<Role>('/roles', body)).data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: KEY })
      queryClient.invalidateQueries({ queryKey: ['users'] })
    },
  })
}

export function useDeleteRole() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: number) => api.delete(`/roles/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: KEY }),
  })
}
