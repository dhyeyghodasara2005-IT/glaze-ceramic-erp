import { useState } from 'react'
import { Alert, App, Button, Drawer, Space } from 'antd'
import { StopOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useDrawerSize } from '../../components/useDrawerSize'
import { useAuth } from '../../auth/AuthContext'
import { errorMessage } from '../../api/errors'
import type { LotCheck } from '../../api/types'
import DocStatusTag from '../store/DocStatusTag'
import { dateTime, qty, shortDate } from '../store/format'
import { useCancelLotCheck } from './finishApi'
import ReasonModal from './ReasonModal'
import VerdictTag from './VerdictTag'

/** One quality check, read-only, with "Cancel check" for the checker or a manager. */
export default function QualityCheckDrawer({ check, topGradeName, onClose }: {
  check?: LotCheck
  topGradeName?: string
  onClose: () => void
}) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const drawerSize = useDrawerSize(480)
  const { can } = useAuth()
  const cancel = useCancelLotCheck()
  const [cancelling, setCancelling] = useState(false)
  // the drawer shows the latest copy after a cancel, without waiting for the list to reload
  const [shown, setShown] = useState<LotCheck>()
  const c = shown?.id === check?.id ? shown : check

  const onCancel = async (reason: string) => {
    if (!c) return
    try {
      const done = await cancel.mutateAsync({ id: c.id, reason })
      setShown(done)
      message.success(t('store.cancelOk', { no: c.checkNo }))
      setCancelling(false)
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  const mm = (v?: number) => (v != null ? `${v > 0 ? '+' : ''}${qty(v, 2)} mm` : '–')
  const size = (v?: number) => (v != null ? `${qty(v, 2)} mm` : '–')
  const facts = c ? [
    { label: t('finishing.samples'), value: c.samples.toLocaleString('en-IN') },
    { label: t('finishing.centreBend'), value: mm(c.centreBendMm) },
    { label: t('finishing.edgeBend'), value: mm(c.edgeBendMm) },
    { label: t('finishing.warpage'), value: mm(c.warpageMm) },
    { label: t('finishing.length'), value: size(c.lengthMm) },
    { label: t('finishing.width'), value: size(c.widthMm) },
    { label: t('finishing.thickness'), value: size(c.thicknessMm) },
    { label: t('finishing.expectedTop', { grade: topGradeName ?? '' }), value: c.expectedTopPct != null ? `${qty(c.expectedTopPct, 1)} %` : '–' },
  ] : []

  return (
    <Drawer
      open={check !== undefined}
      onClose={onClose}
      size={drawerSize}
      title={c?.checkNo}
      destroyOnHidden
      extra={c?.status === 'POSTED' && (can('QUALITY_CHECK') || can('PRODUCTION_CANCEL')) && (
        <Button danger icon={<StopOutlined />} onClick={() => setCancelling(true)}>{t('finishing.cancelCheck')}</Button>
      )}
    >
      {c && (
        <>
          <Space size={10} wrap style={{ marginBottom: 16 }}>
            <DocStatusTag status={c.status} />
            <VerdictTag verdict={c.verdict} />
            <span><Link to={`/production/lots/${c.lotId}`} className="cell-title">{c.designName}</Link> · {c.lotNo}</span>
          </Space>
          <div className="cell-sub" style={{ marginBottom: 16 }}>
            {shortDate(c.checkDate)} · {t('prod.shift')} {c.shiftCode} · {t('finishing.checkedBy', { by: c.createdBy, at: dateTime(c.createdAt) })}
          </div>
          {c.status === 'CANCELLED' && (
            <Alert className="doc-alert" type="error" showIcon
              title={t('store.cancelledInfo', { by: c.cancelledBy, at: dateTime(c.cancelledAt), reason: c.cancelReason })} />
          )}
          <div className="fact-grid">
            {facts.map((f) => (
              <div key={f.label}>
                <span className="stat-label">{f.label}</span>
                <b>{f.value}</b>
              </div>
            ))}
          </div>
          {c.remarks && <p style={{ marginTop: 16 }}>{c.remarks}</p>}
        </>
      )}
      <ReasonModal
        open={cancelling}
        title={t('store.cancelTitle', { no: c?.checkNo })}
        hint={t('finishing.cancelCheckHint')}
        okText={t('finishing.cancelCheck')}
        loading={cancel.isPending}
        onOk={onCancel}
        onClose={() => setCancelling(false)}
      />
    </Drawer>
  )
}
