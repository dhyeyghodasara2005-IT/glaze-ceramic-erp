import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { Machine, MachineLink, MachineReplaceRequest } from '../../api/types'

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

// ---- machines: the layout and replacing one ----

/** Every "feeds into" arrow of one plant. */
export function useMachineLinks(plantId?: number) {
  return useQuery({
    queryKey: ['masters', 'machine-links', plantId],
    queryFn: async () => (await api.get<MachineLink[]>('/masters/machines/links', { params: { plantId } })).data,
    enabled: plantId !== undefined,
  })
}

/** The machines and arrows changed: refresh the lists here and the entry screens' lookups. */
function useRefreshMachines() {
  const queryClient = useQueryClient()
  return () => {
    queryClient.invalidateQueries({ queryKey: ['masters', 'machines'] })
    queryClient.invalidateQueries({ queryKey: ['masters', 'machine-links'] })
    queryClient.invalidateQueries({ queryKey: ['prod-lookup', 'machines'] })
    queryClient.invalidateQueries({ queryKey: ['prod-lookup', 'machine-links'] })
  }
}

/** Sets everything one machine feeds into (the whole list). */
export function useSetFeeds() {
  const refresh = useRefreshMachines()
  return useMutation({
    mutationFn: async ({ id, toMachineIds }: { id: number; toMachineIds: number[] }) =>
      (await api.put<MachineLink[]>(`/masters/machines/${id}/feeds`, { toMachineIds })).data,
    onSuccess: refresh,
  })
}

/** Takes a machine out and puts a new one in its place. */
export function useReplaceMachine() {
  const refresh = useRefreshMachines()
  return useMutation({
    mutationFn: async ({ id, body }: { id: number; body: MachineReplaceRequest }) =>
      (await api.post<Machine>(`/masters/machines/${id}/replace`, body)).data,
    onSuccess: refresh,
  })
}
