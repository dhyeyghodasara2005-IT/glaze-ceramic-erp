import { FireOutlined, FormatPainterOutlined, SketchOutlined } from '@ant-design/icons'
import type { LineStage } from '../../api/types'

/** One icon per line stage, the same everywhere: brush = glaze & print, flame = kiln, diamond = polishing. */
export default function LineStageIcon({ stage }: { stage: LineStage }) {
  if (stage === 'GLAZE') return <FormatPainterOutlined />
  if (stage === 'KILN') return <FireOutlined />
  return <SketchOutlined />
}
