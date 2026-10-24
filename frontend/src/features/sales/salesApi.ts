import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type {
  PageResponse, PartyType, ProductMatch, SalesDesign, SalesOption, SalesOrder, SalesOrderRequest, SalesOrderStatus,
  SalesOrderSummary, SalesOrderType, SalesParty,
} from '../../api/types'

// Step 6b: /api/sales - sales orders from loading slips, and matching their product names

export interface SalesSearch {
  plantId: number
  status?: SalesOrderStatus
  type?: SalesOrderType
  from?: string
  to?: string
  search?: string
  page: number
  size: number
}

export function useSalesOrders(params: SalesSearch | null) {
  return useQuery({
    queryKey: ['sales-orders', params],
    queryFn: async () => (await api.get<PageResponse<SalesOrderSummary>>('/sales/orders', { params })).data,
    enabled: params !== null,
    placeholderData: keepPreviousData,
  })
}

export function useSalesOrder(id?: number) {
  return useQuery({
    queryKey: ['sales-order', id],
    queryFn: async () => (await api.get<SalesOrder>(`/sales/orders/${id}`)).data,
    enabled: id !== undefined,
  })
}

/** Active dealers, jobwork principals or transporters. */
export function useSalesParties(type?: PartyType) {
  return useQuery({
    queryKey: ['sales-parties', type],
    queryFn: async () => (await api.get<SalesParty[]>('/sales/lookups/parties', { params: { type } })).data,
    enabled: type !== undefined,
    staleTime: 60_000,
  })
}

export function useSalesDesigns(enabled = true) {
  return useQuery({
    queryKey: ['sales-designs'],
    queryFn: async () => (await api.get<SalesDesign[]>('/sales/lookups/designs')).data,
    enabled,
    staleTime: 5 * 60_000,
  })
}

export function useSalesGrades(enabled = true) {
  return useQuery({
    queryKey: ['sales-grades'],
    queryFn: async () => (await api.get<SalesOption[]>('/sales/lookups/grades')).data,
    enabled,
    staleTime: 5 * 60_000,
  })
}

/** What Glaze understands from each product description: one answer per description, in the same order. */
export async function matchProducts(descriptions: string[]) {
  return (await api.post<ProductMatch[]>('/sales/match', { descriptions })).data
}

/** Any change to an order: keep the editor's copy and refresh the list. */
function useSalesMutation<V, R extends SalesOrder | void>(fn: (vars: V) => Promise<R>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: (saved: R) => {
      if (saved) queryClient.setQueryData(['sales-order', saved.id], saved)
      queryClient.invalidateQueries({ queryKey: ['sales-orders'] })
    },
  })
}

export const useSaveSalesOrder = () =>
  useSalesMutation(async ({ id, body }: { id?: number; body: SalesOrderRequest }) =>
    id ? (await api.put<SalesOrder>(`/sales/orders/${id}`, body)).data : (await api.post<SalesOrder>('/sales/orders', body)).data,
  )

export const useConfirmSalesOrder = () =>
  useSalesMutation(async (id: number) => (await api.post<SalesOrder>(`/sales/orders/${id}/confirm`)).data)

export const useReopenSalesOrder = () =>
  useSalesMutation(async (id: number) => (await api.post<SalesOrder>(`/sales/orders/${id}/reopen`)).data)

export const useCancelSalesOrder = () =>
  useSalesMutation(async ({ id, reason }: { id: number; reason: string }) =>
    (await api.post<SalesOrder>(`/sales/orders/${id}/cancel`, { reason })).data,
  )

export const useDeleteSalesOrder = () =>
  useSalesMutation(async (id: number) => {
    await api.delete(`/sales/orders/${id}`)
  })
