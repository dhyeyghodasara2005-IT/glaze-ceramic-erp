import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { PageResponse, User, UserCreateRequest, UserUpdateRequest } from '../../api/types'

export interface UserSearch {
  search?: string
  active?: boolean
  page: number // 0-based, like Spring
  size: number
}

export function useUsers(params: UserSearch, enabled = true) {
  return useQuery({
    queryKey: ['users', params],
    queryFn: async () => (await api.get<PageResponse<User>>('/users', { params })).data,
    // keep showing the old page while the next one loads (no flicker)
    placeholderData: keepPreviousData,
    enabled,
  })
}

function useInvalidateUsers() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: ['users'] })
}

export function useCreateUser() {
  const invalidate = useInvalidateUsers()
  return useMutation({
    mutationFn: async (body: UserCreateRequest) => (await api.post<User>('/users', body)).data,
    onSuccess: invalidate,
  })
}

export function useUpdateUser() {
  const invalidate = useInvalidateUsers()
  return useMutation({
    mutationFn: async ({ id, body }: { id: number; body: UserUpdateRequest }) =>
      (await api.put<User>(`/users/${id}`, body)).data,
    onSuccess: invalidate,
  })
}

export function useSetUserActive() {
  const invalidate = useInvalidateUsers()
  return useMutation({
    mutationFn: async ({ id, active }: { id: number; active: boolean }) =>
      (await api.post<User>(`/users/${id}/${active ? 'activate' : 'deactivate'}`)).data,
    onSuccess: invalidate,
  })
}

export function useResetPassword() {
  const invalidate = useInvalidateUsers()
  return useMutation({
    mutationFn: async ({ id, newPassword }: { id: number; newPassword: string }) =>
      (await api.post<User>(`/users/${id}/reset-password`, { newPassword })).data,
    onSuccess: invalidate,
  })
}
