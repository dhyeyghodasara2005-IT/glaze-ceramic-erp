import type { MachineType } from '../../../api/types'

/** Tag colour by process stage, so a line-up reads like the factory floor. */
export const STAGE_COLOR: Record<MachineType, string> = {
  BALL_MILL: 'gold',
  SLIP_TANK: 'gold',
  SPRAY_DRYER: 'gold',
  SILO: 'gold',
  PRESS: 'blue',
  DRYER: 'blue',
  GLAZE_LINE: 'purple',
  DIGITAL_PRINTER: 'purple',
  COMPENSATOR: 'cyan',
  KILN: 'volcano',
  POLISHING_LINE: 'green',
  SORTING_LINE: 'green',
  PACKING_LINE: 'green',
  UTILITY: 'default',
  OTHER: 'default',
}
