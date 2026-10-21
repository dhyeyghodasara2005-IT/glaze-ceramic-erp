import type { TileSize, TileSizeRequest } from '../../../api/types'
import { useMasterList, useSaveMaster } from '../masterApi'

// Matches TileSizeController.java: /api/masters/sizes
export const useSizes = (enabled = true) => useMasterList<TileSize>('sizes', enabled)
export const useSaveSize = () => useSaveMaster<TileSize, TileSizeRequest>('sizes')
