import { useTranslation } from 'react-i18next'
import type { CheckVerdict } from '../../api/types'

/** The checker's call on a lot: Good = green, Watch = amber, Problem = red. */
export default function VerdictTag({ verdict }: { verdict: CheckVerdict }) {
  const { t } = useTranslation()
  return (
    <span className={`status-pill verdict-${verdict.toLowerCase()}`} title={t(`finishing.verdictHint.${verdict}`)}>
      <span className="status-dot" />
      {t(`finishing.verdict.${verdict}`)}
    </span>
  )
}
