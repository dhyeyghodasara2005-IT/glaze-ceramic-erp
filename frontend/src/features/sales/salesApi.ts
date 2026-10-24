import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type {
  PageResponse, PartyType, PickOption, ProductMatch, SalesDesign, SalesOption, SalesOrder, SalesOrderRequest,
  SalesOrderStatus, SalesOrderSummary, SalesOrderType, SalesParty, TruckRequest,
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

/**
 * Any change to an order: keep the editor's copy and refresh the list. Planning, loading and dispatching also
 * change what other orders can keep, and dispatch moves stock: those refresh the stock views too.
 */
function useSalesMutation<V, R extends SalesOrder | void>(fn: (vars: V) => Promise<R>, movesStock = false) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: (saved: R) => {
      if (saved) queryClient.setQueryData(['sales-order', saved.id], saved)
      queryClient.invalidateQueries({ queryKey: ['sales-orders'] })
      queryClient.invalidateQueries({ queryKey: ['pick-options'] })
      if (movesStock) {
        for (const key of ['fg-stock', 'fg-ledger', 'godown-map', 'fg-godowns', 'pillar-options']) {
          queryClient.invalidateQueries({ queryKey: [key] })
        }
      }
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

// ---- step 6c: the loading plan, the loading checklist and dispatch ----

export const useReplanSalesOrder = () =>
  useSalesMutation(async (id: number) => (await api.post<SalesOrder>(`/sales/orders/${id}/plan`)).data)

export const useSetTruck = () =>
  useSalesMutation(async ({ id, body }: { id: number; body: TruckRequest }) =>
    (await api.put<SalesOrder>(`/sales/orders/${id}/truck`, body)).data,
  )

/** What was really loaded from a plan row; undefined takes the tick back. */
export const useTickPick = () =>
  useSalesMutation(async ({ id, pickId, loadedBoxes }: { id: number; pickId: number; loadedBoxes?: number }) =>
    (await api.put<SalesOrder>(`/sales/orders/${id}/picks/${pickId}`, { loadedBoxes: loadedBoxes ?? null })).data,
  )

/** Stock rows a line could take its boxes from instead, nearest first. */
export function usePickOptions(orderId?: number, lineId?: number) {
  return useQuery({
    queryKey: ['pick-options', orderId, lineId],
    queryFn: async () => (await api.get<PickOption[]>(`/sales/orders/${orderId}/lines/${lineId}/stock`)).data,
    enabled: orderId !== undefined && lineId !== undefined,
  })
}

export const useAddPick = () =>
  useSalesMutation(async ({ id, lineId, stockId, boxes }: { id: number; lineId: number; stockId: number; boxes: number }) =>
    (await api.post<SalesOrder>(`/sales/orders/${id}/lines/${lineId}/picks`, { stockId, boxes })).data,
  )

export const useDispatchOrder = () =>
  useSalesMutation(async (id: number) => (await api.post<SalesOrder>(`/sales/orders/${id}/dispatch`)).data, true)

export const useUndoDispatch = () =>
  useSalesMutation(async ({ id, reason }: { id: number; reason: string }) =>
    (await api.post<SalesOrder>(`/sales/orders/${id}/undo-dispatch`, { reason })).data, true,
  )
