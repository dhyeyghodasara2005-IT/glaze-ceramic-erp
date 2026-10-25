import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type {
  GrnLabStatus, LabForm, LabMaterialRow, LabOverview, LabParameter, LabTest, LabTestRequest, LabTestSummary, LabSample,
  LabVerdict, PageResponse,
} from '../../api/types'

// Step 8d: /api/lab - raw material tested when it arrives

export function useLabMaterials() {
  return useQuery({
    queryKey: ['lab-materials'],
    queryFn: async () => (await api.get<LabMaterialRow[]>('/lab/materials')).data,
  })
}

export function useLabParameters(materialId?: number) {
  return useQuery({
    queryKey: ['lab-parameters', materialId],
    queryFn: async () => (await api.get<LabParameter[]>(`/lab/materials/${materialId}/parameters`)).data,
    enabled: materialId !== undefined,
  })
}

export interface ParameterLine {
  id?: number
  code: string
  name: string
  unit?: string
  minValue?: number
  maxValue?: number
}

export function useSaveLabParameters() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ materialId, parameters }: { materialId: number; parameters: ParameterLine[] }) =>
      (await api.put<LabParameter[]>(`/lab/materials/${materialId}/parameters`, { parameters })).data,
    onSuccess: () => {
      for (const key of ['lab-materials', 'lab-parameters', 'lab-waiting', 'lab-overview']) queryClient.invalidateQueries({ queryKey: [key] })
    },
  })
}

export function useLabWaiting(plantId?: number) {
  return useQuery({
    queryKey: ['lab-waiting', plantId],
    queryFn: async () => (await api.get<LabSample[]>('/lab/waiting', { params: { plantId } })).data,
    enabled: plantId !== undefined,
    // trucks arrive while the page is open
    refetchInterval: 60_000,
  })
}

export function useLabOverview(plantId?: number) {
  return useQuery({
    queryKey: ['lab-overview', plantId],
    queryFn: async () => (await api.get<LabOverview>('/lab/overview', { params: { plantId } })).data,
    enabled: plantId !== undefined,
  })
}

export interface LabSearch {
  plantId: number
  verdict?: LabVerdict
  search?: string
  page: number
  size: number
}

export function useLabTests(params: LabSearch | null) {
  return useQuery({
    queryKey: ['lab-tests', params],
    queryFn: async () => (await api.get<PageResponse<LabTestSummary>>('/lab/tests', { params })).data,
    enabled: params !== null,
    placeholderData: keepPreviousData,
  })
}

/** The form for one sample (a GRN and a material). */
export function useLabForm(sample?: { documentId: number; materialId: number }) {
  return useQuery({
    queryKey: ['lab-form', sample?.documentId, sample?.materialId],
    queryFn: async () => (await api.get<LabForm>('/lab/form', { params: sample })).data,
    enabled: sample !== undefined,
  })
}

export function useGrnLabStatus(documentId?: number, enabled = true) {
  return useQuery({
    queryKey: ['lab-grn', documentId],
    queryFn: async () => (await api.get<GrnLabStatus[]>(`/lab/grn/${documentId}`)).data,
    enabled: documentId !== undefined && enabled,
  })
}

/** The lab's result changes the GRN draft (rejected lines) and what waits: refresh both sides. */
function useLabMutation<V, R>(fn: (vars: V) => Promise<R>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      for (const key of ['lab-form', 'lab-waiting', 'lab-overview', 'lab-tests', 'lab-grn', 'store-doc', 'store-docs', 'dashboard']) {
        queryClient.invalidateQueries({ queryKey: [key] })
      }
    },
  })
}

export const useSaveLabTest = () =>
  useLabMutation(async (body: LabTestRequest) => (await api.post<LabTest>('/lab/tests', body)).data)

export const useDeleteLabTest = () =>
  useLabMutation(async (id: number) => {
    await api.delete(`/lab/tests/${id}`)
  })

export const fetchLabTest = async (id: number) => (await api.get<LabTest>(`/lab/tests/${id}`)).data
