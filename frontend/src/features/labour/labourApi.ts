import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { AttendanceRequest, AttendanceSheet, ContractorRef, Department, LabourMonth, Worker, WorkerRequest } from '../../api/types'

// Step 9a: /api/labour - workers, the day's attendance, the month per contractor

export interface WorkerSearch {
  plantId: number
  active?: boolean
  contractorId?: number
  department?: Department
  search?: string
}

export function useWorkers(params: WorkerSearch | null) {
  return useQuery({
    queryKey: ['workers', params],
    queryFn: async () => (await api.get<Worker[]>('/labour/workers', { params })).data,
    enabled: params !== null,
    placeholderData: keepPreviousData,
  })
}

export const fetchWorker = async (id: number) => (await api.get<Worker>(`/labour/workers/${id}`)).data

export function useContractors(enabled = true) {
  return useQuery({
    queryKey: ['labour-contractors'],
    queryFn: async () => (await api.get<ContractorRef[]>('/labour/contractors')).data,
    enabled,
  })
}

export function useSaveWorker() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, body }: { id?: number; body: WorkerRequest }) =>
      (id ? await api.put<Worker>(`/labour/workers/${id}`, body) : await api.post<Worker>('/labour/workers', body)).data,
    onSuccess: () => {
      for (const key of ['workers', 'attendance', 'labour-month', 'dashboard']) queryClient.invalidateQueries({ queryKey: [key] })
    },
  })
}

/** A day's sheet; without a date, today's production date. */
export function useAttendance(params: { plantId: number; date?: string; department?: Department } | null) {
  return useQuery({
    queryKey: ['attendance', params],
    queryFn: async () => (await api.get<AttendanceSheet>('/labour/attendance', { params })).data,
    enabled: params !== null,
    placeholderData: keepPreviousData,
  })
}

export function useSaveAttendance() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (body: AttendanceRequest) => (await api.post<AttendanceSheet>('/labour/attendance', body)).data,
    onSuccess: () => {
      for (const key of ['attendance', 'labour-month', 'dashboard']) queryClient.invalidateQueries({ queryKey: [key] })
    },
  })
}

/** month as 2026-10 */
export function useLabourMonth(params: { plantId: number; month: string } | null) {
  return useQuery({
    queryKey: ['labour-month', params],
    queryFn: async () => (await api.get<LabourMonth>('/labour/month', { params })).data,
    enabled: params !== null,
    placeholderData: keepPreviousData,
  })
}
