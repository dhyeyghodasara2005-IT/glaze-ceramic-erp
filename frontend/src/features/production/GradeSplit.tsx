import { useTranslation } from 'react-i18next'
import { qty } from '../store/format'

/** Grade colours by sort order (1 = best): brand terracotta, then calmer tones. A grade keeps its colour everywhere. */
const TONES = ['#A84A23', '#D08A5B', '#7A8F6B', '#8C8279', '#B9A89A']
const tone = (sortOrder: number) => TONES[Math.min(Math.max(sortOrder - 1, 0), TONES.length - 1)]

/**
 * One bar split by grade (boxes), with a legend under it:
 *   [██████████████ Premium 700 ████ Standard 80 █ Commercial 10]
 */
export default function GradeSplit({ grades }: { grades: { key: string; name: string; sortOrder: number; boxes: number; pieces: number }[] }) {
  const { t } = useTranslation()
  const total = grades.reduce((s, g) => s + g.pieces, 0)
  if (total === 0) return null
  return (
    <div className="grade-split">
      <div className="grade-bar" role="img" aria-label={grades.map((g) => `${g.name} ${g.boxes}`).join(', ')}>
        {grades.map((g) => (
          <span key={g.key} style={{ flexGrow: g.pieces, background: tone(g.sortOrder) }} />
        ))}
      </div>
      <div className="grade-legend">
        {grades.map((g) => (
          <span key={g.key}>
            <i style={{ background: tone(g.sortOrder) }} />
            {g.name} <b>{t('finishing.boxesN', { n: g.boxes.toLocaleString('en-IN') })}</b>
            <span className="cell-sub"> · {qty((g.pieces * 100) / total, 1)} %</span>
          </span>
        ))}
      </div>
    </div>
  )
}
