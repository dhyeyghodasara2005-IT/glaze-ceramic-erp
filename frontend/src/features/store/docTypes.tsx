import type { ReactNode } from 'react'
import { DiffOutlined, ExportOutlined, ImportOutlined } from '@ant-design/icons'
import type { DocStatus, StoreDocType } from '../../api/types'

/** Where each document type lives in the app. Used by lists, the editor and the stock ledger links. */
export const DOC_TYPES: Record<StoreDocType, { path: string; icon: ReactNode }> = {
  GRN: { path: '/store/grn', icon: <ImportOutlined /> },
  ISSUE: { path: '/store/issues', icon: <ExportOutlined /> },
  ADJUSTMENT: { path: '/store/adjustments', icon: <DiffOutlined /> },
}

export const DOC_STATUSES: DocStatus[] = ['DRAFT', 'POSTED', 'CANCELLED']
