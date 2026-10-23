import type { ReactNode } from 'react'
import { DownloadOutlined, SwapOutlined, WarningOutlined } from '@ant-design/icons'
import type { FgDocType, FgSource } from '../../api/types'

/** Where each warehouse document lives in the app, and its icon. */
export const FG_DOC_TYPES: Record<FgDocType, { path: string; icon: ReactNode }> = {
  OPENING: { path: '/fg/opening', icon: <DownloadOutlined /> },
  BREAKAGE: { path: '/fg/breakage', icon: <WarningOutlined /> },
  REGRADE: { path: '/fg/regrading', icon: <SwapOutlined /> },
}

/** The page of the document behind a ledger line. */
export const sourcePath = (source: FgSource, id: number) =>
  source === 'PACK' ? `/production/pack-entries/${id}` : `${FG_DOC_TYPES[source].path}/${id}`
