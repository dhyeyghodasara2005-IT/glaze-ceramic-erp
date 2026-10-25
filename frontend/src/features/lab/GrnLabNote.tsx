import { Alert } from 'antd'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { useGrnLabStatus } from './labApi'

/**
 * On a GRN: which of its materials wait for the lab, and what the lab decided. The store keeper sees why the GRN
 * can't be posted yet, and that a rejected material won't enter stock.
 */
export default function GrnLabNote({ documentId }: { documentId: number }) {
  const { t } = useTranslation()
  const { can } = useAuth()
  const status = useGrnLabStatus(documentId, can('LAB_VIEW') || can('LAB_ENTRY') || can('STORE_VIEW'))
  const all = status.data ?? []
  const waiting = all.filter((s) => !s.verdict)
  const rejected = all.filter((s) => s.verdict === 'REJECTED')
  const accepted = all.filter((s) => s.verdict === 'ACCEPTED')
  const link = can('LAB_VIEW') || can('LAB_ENTRY')
  return (
    <>
      {waiting.length > 0 && (
        <Alert className="doc-alert" type="warning" showIcon
          title={t('lab.grnWaiting', { names: waiting.map((s) => s.materialName).join(', ') })}
          action={link && <Link to="/lab/tests">{t('lab.openLab')}</Link>} />
      )}
      {rejected.length > 0 && (
        <Alert className="doc-alert" type="error" showIcon
          title={t('lab.grnRejected', { names: rejected.map((s) => `${s.materialName} (${s.testNo})`).join(', ') })} />
      )}
      {accepted.length > 0 && (
        <Alert className="doc-alert" type="success" showIcon
          title={t('lab.grnAccepted', { names: accepted.map((s) => `${s.materialName} (${s.testNo})`).join(', ') })} />
      )}
    </>
  )
}
