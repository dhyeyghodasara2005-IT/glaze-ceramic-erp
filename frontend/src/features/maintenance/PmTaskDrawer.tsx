import { useState } from 'react'
import { App, Button, Descriptions, Drawer, Space, Spin, Tag } from 'antd'
import { CheckOutlined, EditOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../auth/AuthContext'
import { errorMessage } from '../../api/errors'
import type { PmJob } from '../../api/types'
import { useDrawerSize } from '../../components/useDrawerSize'
import { money, qty, shortDate } from '../store/format'
import ReasonModal from '../production/ReasonModal'
import { useCancelPmJob, usePmTask } from './maintenanceApi'
import { minutesText } from './maintenanceParts'
import PmDoneModal from './PmDoneModal'
import PmTaskFormDrawer from './PmTaskFormDrawer'

/** One preventive job: what to do, when it is due, and every time it was done. */
export default function PmTaskDrawer({ id, onClose }: { id?: number; onClose: () => void }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const { can } = useAuth()
  const size = useDrawerSize(620)
  const query = usePmTask(id)
  const task = query.data
  const cancel = useCancelPmJob()
  const [editing, setEditing] = useState(false)
  const [doing, setDoing] = useState(false)
  const [cancelling, setCancelling] = useState<PmJob>()

  const onCancelJob = async (reason: string) => {
    try {
      await cancel.mutateAsync({ jobId: cancelling!.id, reason })
      message.success(t('pm.cancelledOk', { no: cancelling!.jobNo }))
      setCancelling(undefined)
    } catch (err) {
      message.error(errorMessage(err, t('common.somethingWrong')))
    }
  }

  return (
    <Drawer open={id !== undefined} onClose={onClose} size={size} destroyOnHidden
      title={task ? `${task.machine.name}: ${task.title}` : t('pm.job')}
      extra={task && can('MAINTENANCE_ENTRY') && (
        <Space>
          <Button icon={<EditOutlined />} onClick={() => setEditing(true)}>{t('common.edit')}</Button>
          {task.active && <Button type="primary" icon={<CheckOutlined />} onClick={() => setDoing(true)}>{t('pm.markDone')}</Button>}
        </Space>
      )}>
      {query.isLoading && <Spin />}
      {task && (
        <>
          <div className={`pm-banner ${task.active ? task.due.toLowerCase() : 'off'}`}>
            <div className="pm-banner-due">
              {!task.active ? t('pm.switchedOff') : task.due === 'OVERDUE' ? t('pm.overdueBy', { count: task.daysOverdue })
                : task.due === 'TODAY' ? t('pm.dueToday') : t('pm.dueOn', { date: shortDate(task.nextDue) })}
            </div>
            <div className="cell-sub">
              {t('pm.everyLong', { count: task.everyDays })} · {task.lastDone ? t('pm.lastDone', { date: shortDate(task.lastDone) }) : t('pm.neverDone')}
            </div>
          </div>
          <Descriptions column={1} size="small" className="bd-facts">
            <Descriptions.Item label={t('maint.machine')}>{task.machine.name} · {task.machine.code}</Descriptions.Item>
            <Descriptions.Item label={t('pm.kind')}>{t(`pm.kinds.${task.kind}`)}</Descriptions.Item>
            <Descriptions.Item label={t('pm.needsStop')}>{task.needsStop ? t('common.yes') : t('common.no')}</Descriptions.Item>
            {task.instructions && <Descriptions.Item label={t('pm.instructions')}><span className="pm-instructions">{task.instructions}</span></Descriptions.Item>}
          </Descriptions>

          <div className="form-section">{t('pm.history')}</div>
          {task.history.length === 0 && <p className="cell-sub">{t('pm.neverDone')}</p>}
          <ul className="pm-history">
            {task.history.map((j) => (
              <li key={j.id} className={j.status === 'CANCELLED' ? 'cancelled' : undefined}>
                <div className="pm-history-head">
                  <b>{shortDate(j.doneOn)}</b>
                  {j.status === 'CANCELLED' ? <Tag bordered={false}>{t('pm.cancelled')}</Tag>
                    : j.daysLate > 0 ? <Tag color="orange" bordered={false}>{t('pm.lateN', { count: j.daysLate })}</Tag>
                      : <Tag color="green" bordered={false}>{t('pm.onTime')}</Tag>}
                  <span className="cell-sub">{j.jobNo}</span>
                </div>
                <div className="cell-sub">
                  {[j.doneBy, j.minutes != null && minutesText(j.minutes), j.stopMinutes != null && t('pm.stoppedFor', { time: minutesText(j.stopMinutes) })]
                    .filter(Boolean).join(' · ')}
                </div>
                {j.findings && <div className="pm-findings">{j.findings}</div>}
                {j.spares.length > 0 && (
                  <div className="cell-sub">
                    {j.spares.map((s) => `${s.materialName} ${qty(s.quantity)} ${s.unit}`).join(', ')} · {money(j.spareCost)}
                    {j.issueDocumentId && can('STORE_VIEW') && <> · <Link to={`/store/issues/${j.issueDocumentId}`}>{j.issueDocumentNo}</Link></>}
                  </div>
                )}
                {j.status === 'CANCELLED' && <div className="cell-sub">{t('pm.cancelledBy', { by: j.cancelledBy, reason: j.cancelReason })}</div>}
                {j.latest && j.status === 'DONE' && can('MAINTENANCE_CANCEL') && (
                  <Button type="link" size="small" danger onClick={() => setCancelling(j)}>{t('pm.cancelRecord')}</Button>
                )}
              </li>
            ))}
          </ul>

          <PmTaskFormDrawer open={editing} plantId={task.plantId} task={task} onClose={() => setEditing(false)} />
          <PmDoneModal task={doing ? task : undefined} plantId={task.plantId} onClose={() => setDoing(false)} />
          <ReasonModal open={cancelling !== undefined} title={t('pm.cancelTitle', { no: cancelling?.jobNo })} hint={t('pm.cancelHint')}
            okText={t('pm.cancelRecord')} loading={cancel.isPending} onOk={onCancelJob} onClose={() => setCancelling(undefined)} />
        </>
      )}
    </Drawer>
  )
}
